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

package com.ning.billing.meter.timeline.categories;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.meter.MeterTestSuiteNoDB;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestCategoryAndMetrics extends MeterTestSuiteNoDB {

    @Test(groups = "fast")
    public void testMapping() throws Exception {
        final CategoryAndMetrics kinds = new CategoryAndMetrics("JVM");
        kinds.addMetric("GC");
        kinds.addMetric("CPU");

        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(kinds);
        Assert.assertEquals("{\"eventCategory\":\"JVM\",\"metrics\":[\"GC\",\"CPU\"]}", json);

        final CategoryAndMetrics kindsFromJson = mapper.readValue(json, CategoryAndMetrics.class);
        Assert.assertEquals(kindsFromJson, kinds);
    }

    @Test(groups = "fast")
    public void testComparison() throws Exception {
        final CategoryAndMetrics aKinds = new CategoryAndMetrics("JVM");
        aKinds.addMetric("GC");
        aKinds.addMetric("CPU");
        Assert.assertEquals(aKinds.compareTo(aKinds), 0);

        final CategoryAndMetrics bKinds = new CategoryAndMetrics("JVM");
        bKinds.addMetric("GC");
        bKinds.addMetric("CPU");
        Assert.assertEquals(aKinds.compareTo(bKinds), 0);
        Assert.assertEquals(bKinds.compareTo(aKinds), 0);

        final CategoryAndMetrics cKinds = new CategoryAndMetrics("JVM");
        cKinds.addMetric("GC");
        cKinds.addMetric("CPU");
        cKinds.addMetric("Something else");
        Assert.assertTrue(aKinds.compareTo(cKinds) < 0);
        Assert.assertTrue(cKinds.compareTo(aKinds) > 0);

        final CategoryAndMetrics dKinds = new CategoryAndMetrics("ZVM");
        dKinds.addMetric("GC");
        Assert.assertTrue(aKinds.compareTo(dKinds) < 0);
        Assert.assertTrue(dKinds.compareTo(aKinds) > 0);
    }
}
