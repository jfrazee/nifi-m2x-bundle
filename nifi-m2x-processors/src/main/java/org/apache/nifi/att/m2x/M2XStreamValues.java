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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Generated;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.joda.time.DateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Generated("org.jsonschema2pojo")
@JsonPropertyOrder({
    "start",
    "end",
    "limit",
    "values"
})
public class M2XStreamValues {

    @JsonProperty("start")
    private DateTime start;

    @JsonProperty("end")
    private DateTime end;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("values")
    private List<M2XStreamValue> values = new ArrayList<M2XStreamValue>();

    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    /**
    *
    * @return
    * The start
    */
    @JsonProperty("start")
    public DateTime getStart() {
        return start;
    }

    /**
    *
    * @param start
    * The start
    */
    @JsonProperty("start")
    public void setStart(DateTime start) {
        this.start = start;
    }

    /**
    *
    * @return
    * The end
    */
    @JsonProperty("end")
    public DateTime getEnd() {
        return end;
    }

    /**
    *
    * @param end
    * The end
    */
    @JsonProperty("end")
    public void setEnd(DateTime end) {
        this.end = end;
    }

    /**
    *
    * @return
    * The limit
    */
    @JsonProperty("limit")
    public Integer getLimit() {
        return limit;
    }

    /**
    *
    * @param limit
    * The limit
    */
    @JsonProperty("limit")
    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    /**
    *
    * @return
    * The values
    */
    @JsonProperty("values")
    public List<M2XStreamValue> getValues() {
        return values;
    }

    /**
    *
    * @param values
    * The values
    */
    @JsonProperty("values")
    public void setValues(List<M2XStreamValue> values) {
        this.values = values;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
