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

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;

public class TestM2XStreamValues {

    @Before
    public void init() {
    }

    @Test
    public void testNumericValues() throws JsonParseException, IOException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());

        final String json = "{\"start\":\"2014-09-01T00:00:00.000Z\",\"end\":\"2014-09-30T23:59:59.000Z\",\"limit\":100,\"values\":[{\"timestamp\":\"2014-09-09T19:15:00.563Z\",\"value\":32},{\"timestamp\":\"2014-09-09T20:15:00.874Z\",\"value\":29},{\"timestamp\":\"2014-09-09T21:15:00.325Z\",\"value\":30}]}";
        final M2XStreamValues valuesObj = mapper.readValue(json, M2XStreamValues.class);
        final List<M2XStreamValue> valuesList = valuesObj.getValues();

        assertEquals((Integer) 100, valuesObj.getLimit());
        assertEquals(3, valuesList.size());

        assertEquals(32, valuesList.get(0).getValue());
        assertEquals(29, valuesList.get(1).getValue());
        assertEquals(30, valuesList.get(2).getValue());
    }

    @Test
    public void testStringValues() throws JsonParseException, IOException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JodaModule());

        final String json = "{\"start\":\"2014-09-01T00:00:00.000Z\",\"end\":\"2014-09-30T23:59:59.000Z\",\"limit\":100,\"values\":[{\"timestamp\":\"2014-09-09T19:15:00.563Z\",\"value\":\"lions\"},{\"timestamp\":\"2014-09-09T20:15:00.874Z\",\"value\":\"tigers\"},{\"timestamp\":\"2014-09-09T21:15:00.325Z\",\"value\":\"bears\"}]}";
        final M2XStreamValues valuesObj = mapper.readValue(json, M2XStreamValues.class);
        final List<M2XStreamValue> valuesList = valuesObj.getValues();

        assertEquals((Integer) 100, valuesObj.getLimit());
        assertEquals(3, valuesList.size());

        assertEquals("lions", valuesList.get(0).getValue());
        assertEquals("tigers", valuesList.get(1).getValue());
        assertEquals("bears", valuesList.get(2).getValue());
    }

}
