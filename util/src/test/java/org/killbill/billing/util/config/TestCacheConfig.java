/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.util.config;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.config.tenant.PerTenantConfig;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCacheConfig extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testDeSerialization() throws Exception {

        final ObjectMapper objectMapper = new ObjectMapper();

        final PerTenantConfig input = new PerTenantConfig();
        input.put("key1", "foo");
        input.put("key2", "bar");
        input.put("key3", "34346");
        input.put("key4", "23.999");

        final String inputString = objectMapper.writeValueAsString(input);

        final PerTenantConfig result = objectMapper.readValue(inputString, PerTenantConfig.class);
        Assert.assertEquals(result.size(), 4);
    }
}
