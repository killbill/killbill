/*
 * Copyright 2010-2012 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.meter.timeline.metrics;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestSamplesForMetricAndSource extends MeterTestSuiteNoDB {

    @Test(groups = "fast")
    public void testMapping() throws Exception {
        final SamplesForMetricAndSource samples = new SamplesForMetricAndSource("host.foo.com", "JVM", "GC", "1,2,2,0");

        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(samples);
        Assert.assertEquals("{\"sourceName\":\"host.foo.com\",\"eventCategory\":\"JVM\",\"metric\":\"GC\",\"samples\":\"1,2,2,0\"}", json);

        final SamplesForMetricAndSource samplesFromJson = mapper.readValue(json, SamplesForMetricAndSource.class);
        Assert.assertEquals(samplesFromJson, samples);
    }
}
