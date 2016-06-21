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
package org.apache.nifi.att.m2x;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "timestamp",
    "value"
})
public class M2XStreamValue<T> {

    @JsonProperty("timestamp")
    private DateTime timestamp;

    @JsonProperty("value")
    private T value;

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
    *
    * @return
    * The timestamp
    */
    @JsonProperty("timestamp")
    public DateTime getTimestamp() {
        return timestamp;
    }

    /**
    *
    * @param timestamp
    * The timestamp
    */
    @JsonProperty("timestamp")
    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(String timestamp) {
        setTimestamp(ISODateTimeFormat.dateTime().parseDateTime(timestamp));
    }

    /**
    *
    * @return
    * The value
    */
    @JsonProperty("value")
    public T getValue() {
        return value;
    }

    /**
    *
    * @param value
    * The value
    */
    @JsonProperty("value")
    public void setValue(T value) {
        this.value = value;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
