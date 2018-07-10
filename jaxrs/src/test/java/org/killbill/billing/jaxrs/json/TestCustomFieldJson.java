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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;

public class TestCustomFieldJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final UUID customFieldId = UUID.randomUUID();
        final UUID objectId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.INVOICE;
        final String name = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final CustomFieldJson customFieldJson = new CustomFieldJson(customFieldId, objectId, objectType, name, value, null);
        Assert.assertEquals(customFieldJson.getCustomFieldId(), customFieldId);
        Assert.assertEquals(customFieldJson.getObjectId(), objectId);
        Assert.assertEquals(customFieldJson.getObjectType(), objectType);
        Assert.assertEquals(customFieldJson.getName(), name);
        Assert.assertEquals(customFieldJson.getValue(), value);
        Assert.assertEquals(customFieldJson.getAuditLogs().size(), 0);

        final String asJson = mapper.writeValueAsString(customFieldJson);
        final CustomFieldJson fromJson = mapper.readValue(asJson, CustomFieldJson.class);
        Assert.assertEquals(fromJson, customFieldJson);
    }
}
