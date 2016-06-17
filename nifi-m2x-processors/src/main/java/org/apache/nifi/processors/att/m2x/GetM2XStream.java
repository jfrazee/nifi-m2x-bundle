/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.att.m2x;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import org.apache.nifi.att.m2x.M2XStreamValue;
import org.apache.nifi.att.m2x.M2XStreamValues;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.components.state.Scope;
import org.apache.nifi.components.state.StateManager;
import org.apache.nifi.components.state.StateMap;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.*;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.behavior.Stateful;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({"M2X", "ATT", "AT&T", "IoT"})
@CapabilityDescription("Get messages from an M2X device data stream")
@InputRequirement(Requirement.INPUT_FORBIDDEN)
@Stateful(scopes = {Scope.LOCAL}, description = "When reading from the M2X" +
    "data stream, the timestamp is stored so the next time reading will " +
    "start from the last seen data values."
)
public class GetM2XStream extends AbstractM2XProcessor {
    public static final PropertyDescriptor M2X_DEVICE_ID = new PropertyDescriptor
            .Builder()
            .name("m2x-device-id")
            .displayName("Device ID")
            .description("The M2X device id")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor M2X_STREAM_NAME = new PropertyDescriptor
            .Builder()
            .name("m2x-stream-name")
            .displayName("Stream Name")
            .description("The M2X stream name")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor START_TIME_AGO = new PropertyDescriptor
            .Builder()
            .name("start-time-ago")
            .displayName("Start Time Ago")
            .description(
                "How far in the past to set the start of the date range to " +
                "read the stream from"
            )
            .required(true)
            .defaultValue("0 secs")
            .addValidator(StandardValidators.TIME_PERIOD_VALIDATOR)
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    private String getLastStartTime(final ProcessContext context, StateManager stateManager) {
        String startTime = null;

        try {
            final StateMap stateMap = stateManager.getState(Scope.CLUSTER);

            startTime = stateMap.get("startTime");
            if (StringUtils.isEmpty(startTime)) {
                final long startTimeAgo = context.getProperty(START_TIME_AGO).asTimePeriod(TimeUnit.MILLISECONDS).longValue();
                if (startTimeAgo > 0) {
                    final long startTimeMillis = System.currentTimeMillis() - startTimeAgo;
                    startTime = ISODateTimeFormat.dateTime().print(startTimeMillis);
                }
            }
        } catch (final IOException e) {
            getLogger().warn("Failed to retrieve the last start time from the state manager", e);
        }

        return startTime;
    }

    private void setLastStartTime(StateManager stateManager, String startTime) throws IOException {
        final StateMap stateMap = stateManager.getState(Scope.CLUSTER);
        final Map<String, String> statePropertyMap = new HashMap<>(stateMap.toMap());
        statePropertyMap.put("startTime", startTime);
        stateManager.setState(statePropertyMap, Scope.CLUSTER);
    }

    private String getStreamUrl(final String apiUrl, final String deviceId, final String streamName, String startTime) {
        final StringBuilder builder = new StringBuilder().append(apiUrl.replaceAll("/*$", ""))
            .append("/devices/")
            .append(deviceId)
            .append("/streams/")
            .append(streamName)
            .append("/values");

        if (!StringUtils.isEmpty(startTime)) {
            builder.append("?start=").append(startTime);
        }

        return builder.toString();
    }

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);

        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(M2X_DEVICE_ID);
        descriptors.add(M2X_STREAM_NAME);
        descriptors.add(START_TIME_AGO);
        descriptors.addAll(super.getSupportedPropertyDescriptors());
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.addAll(super.getRelationships());
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final ProcessorLog logger = getLogger();
        final OkHttpClient httpClient = getHttpClient();
        final StateManager stateManager = context.getStateManager();
        final String apiKey = context.getProperty(M2X_API_KEY).getValue();
        final String apiUrl = context.getProperty(M2X_API_URL).getValue();
        final String deviceId = context.getProperty(M2X_DEVICE_ID).getValue();
        final String streamName = context.getProperty(M2X_STREAM_NAME).getValue();
        final String startTime = getLastStartTime(context, stateManager);
        final String streamUrl = getStreamUrl(apiUrl, deviceId, streamName, startTime);

        String responseBody;
        try {
            final Request request = new Request.Builder().url(streamUrl).addHeader("X-M2X-KEY", apiKey).build();
            final Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                logger.error(response.message());
                context.yield();
                return;
            }

            responseBody = response.body().string();
        }
        catch (IOException e) {
            logger.error(e.getMessage(), e);
            context.yield();
            return;
        }

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());

        try {
            final M2XStreamValues m2xValues = mapper.readValue(responseBody, M2XStreamValues.class);
            final List<M2XStreamValue> m2xValueList = m2xValues.getValues();

            if (!CollectionUtils.isEmpty(m2xValueList)) {
                for (final M2XStreamValue m2xValue : m2xValueList) {
                    final DateTime timestamp = m2xValue.getTimestamp();
                    final Object valueObj = m2xValue.getValue();
                    final Set<Map.Entry<String, Object>> properties = m2xValue.getAdditionalProperties().entrySet();
                    final ByteArrayInputStream bytes = new ByteArrayInputStream(String.valueOf(valueObj).getBytes(StandardCharsets.UTF_8));

                    FlowFile newFlowFile = session.create();
                    newFlowFile = session.importFrom(bytes, newFlowFile);
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.device.id", deviceId);
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.name", streamName);
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.start", m2xValues.getStart().toString());
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.end", m2xValues.getEnd().toString());
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.limit", String.valueOf(m2xValues.getLimit()));
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.value.timestamp", timestamp.toString());
                    newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.value.millis", String.valueOf(timestamp.getMillis()));
                    for (final Map.Entry<String, Object> e : properties) {
                        newFlowFile = session.putAttribute(newFlowFile, "m2x.stream.value." + e.getKey(), String.valueOf(e.getValue()));
                    }

                    session.getProvenanceReporter().create(newFlowFile);
                    session.transfer(newFlowFile, REL_SUCCESS);
                }
            }

            setLastStartTime(stateManager, m2xValues.getEnd().toString());
        }
        catch (Throwable t) {
            logger.error(t.getMessage(), t);
            context.yield();
        }
    }
}
