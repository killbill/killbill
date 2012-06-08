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

package com.ning.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestBundleJsonSimple {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String bundleId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final BundleJsonSimple bundleJsonSimple = new BundleJsonSimple(bundleId, externalKey);
        Assert.assertEquals(bundleJsonSimple.getBundleId(), bundleId);
        Assert.assertEquals(bundleJsonSimple.getExternalKey(), externalKey);

        final String asJson = mapper.writeValueAsString(bundleJsonSimple);
        Assert.assertEquals(asJson, "{\"bundleId\":\"" + bundleJsonSimple.getBundleId() + "\"," +
                "\"externalKey\":\"" + bundleJsonSimple.getExternalKey() + "\"}");

        final BundleJsonSimple fromJson = mapper.readValue(asJson, BundleJsonSimple.class);
        Assert.assertEquals(fromJson, bundleJsonSimple);
    }
}
