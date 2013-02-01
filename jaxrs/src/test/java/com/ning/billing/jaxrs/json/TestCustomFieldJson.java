/*
 * Copyright 2010-2013 Ning, Inc.
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

import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

public class TestCustomFieldJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String name = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final CustomFieldJson customFieldJson = new CustomFieldJson(name, value);
        Assert.assertEquals(customFieldJson.getName(), name);
        Assert.assertEquals(customFieldJson.getValue(), value);

        final String asJson = mapper.writeValueAsString(customFieldJson);
        Assert.assertEquals(asJson, "{\"name\":\"" + customFieldJson.getName() + "\"," +
                                    "\"value\":\"" + customFieldJson.getValue() + "\"}");

        final CustomFieldJson fromJson = mapper.readValue(asJson, CustomFieldJson.class);
        Assert.assertEquals(fromJson, customFieldJson);
    }
}
