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

package org.killbill.billing.overdue.notification;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.util.jackson.ObjectMapper;

import static org.testng.Assert.assertEquals;

public class TestOverdueNotificationKeyJson {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testOverdueNotificationKeyJson() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final OverdueCheckNotificationKey e = new OverdueCheckNotificationKey(uuid);

        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(OverdueCheckNotificationKey.class.getName());
        final Object obj = mapper.readValue(json, claz);
        Assert.assertTrue(obj.equals(e));
    }

    @Test(groups = "fast")
    public void testOverdueNotificationKeyJsonWithNoKey() throws Exception {
        final String uuidString = "bab0fca4-c628-4997-8980-14d6c3a98c48";
        final String json = "{\"uuidKey\":\"" + uuidString + "\"}";

        final Class<?> claz = Class.forName(OverdueCheckNotificationKey.class.getName());
        final OverdueCheckNotificationKey obj = (OverdueCheckNotificationKey) mapper.readValue(json, claz);
        assertEquals(obj.getUuidKey().toString(), uuidString);
    }
}
