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
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
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
import org.apache.nifi.processor.io.InputStreamCallback;
import org.apache.nifi.processor.util.StandardValidators;

@Tags({"M2X", "ATT", "AT&T", "IoT"})
@CapabilityDescription("Push messages to a M2X device data stream")
public class PutM2XStream extends AbstractM2XProcessor {
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

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

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("failure")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    private String encodeFlowFile(FlowFile flowFile) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());

        return "{\"value\":\"foo\"}";
    }

    @Override
    protected void init(final ProcessorInitializationContext context) {
        super.init(context);

        final List<PropertyDescriptor> descriptors = new ArrayList<PropertyDescriptor>();
        descriptors.add(M2X_DEVICE_ID);
        descriptors.add(M2X_STREAM_NAME);
        descriptors.addAll(super.getSupportedPropertyDescriptors());
        this.descriptors = Collections.unmodifiableList(descriptors);

        final Set<Relationship> relationships = new HashSet<Relationship>();
        relationships.add(REL_FAILURE);
        relationships.addAll(super.getRelationships());
        this.relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        final ProcessorLog logger = getLogger();
        final OkHttpClient httpClient = getHttpClient();
        final StateManager stateManager = context.getStateManager();
        final String apiKey = context.getProperty(M2X_API_KEY).getValue();
        final String apiUrl = context.getProperty(M2X_API_URL).getValue();
        final String deviceId = context.getProperty(M2X_DEVICE_ID).getValue();
        final String streamName = context.getProperty(M2X_STREAM_NAME).getValue();
        final String streamUrl = new StringBuilder().append(apiUrl.replaceAll("/*$", ""))
            .append("/devices/")
            .append(deviceId)
            .append("/streams/")
            .append(streamName)
            .append("/value")
            .toString();

        try {
            final AtomicReference<String> postBodyRef = new AtomicReference<>();
            session.read(flowFile, new InputStreamCallback() {
                @Override
                public void process(InputStream is) {
                    try {
                        final String value = IOUtils.toString(is, StandardCharsets.UTF_8);
                        final M2XStreamValue m2xValue = new M2XStreamValue();
                        m2xValue.setValue(value);

                        final ObjectMapper mapper = new ObjectMapper();
                        mapper.registerModule(new JodaModule());

                        final String postBody = mapper.writeValueAsString(m2xValue);
                        postBodyRef.set(postBody);
                    }
                    catch (Exception e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            });

            final String postBody = postBodyRef.get();
            if (StringUtils.isEmpty(postBody)) {
                logger.error("FlowFile {} contents didn't produce a valid M2X stream value", new Object[]{flowFile});
                session.transfer(flowFile, REL_FAILURE);
                return;
            }

            final Request request = new Request.Builder()
                .url(streamUrl)
                .addHeader("X-M2X-KEY", apiKey)
                .put(RequestBody.create(MEDIA_TYPE_JSON, postBody))
                .build();
            final Response response = httpClient.newCall(request).execute();

            if (!response.isSuccessful()) {
                logger.error(response.message());
                context.yield();
                session.penalize(flowFile);
                return;
            }
        }
        catch (IOException e) {
            logger.error(e.getMessage(), e);
            context.yield();
            session.penalize(flowFile);
            return;
        }

        session.transfer(flowFile, REL_SUCCESS);
    }
}
