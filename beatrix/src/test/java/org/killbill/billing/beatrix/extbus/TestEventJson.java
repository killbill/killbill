/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.beatrix.extbus;

import java.util.UUID;

import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ObjectType;
import org.killbill.billing.beatrix.BeatrixTestSuiteNoDB;
import org.killbill.billing.util.jackson.ObjectMapper;

public class  TestEventJson extends BeatrixTestSuiteNoDB {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test(groups = "fast")
    public void testBusExternalEvent() throws Exception {
        final UUID objectId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final ObjectType objectType = ObjectType.ACCOUNT;
        final ExtBusEventType extBusEventType = ExtBusEventType.ACCOUNT_CREATION;

        final DefaultBusExternalEvent e = new DefaultBusExternalEvent(objectId, objectType, extBusEventType, accountId, tenantId, null, 1L, 2L, UUID.randomUUID());
        final String json = mapper.writeValueAsString(e);

        final Class<?> claz = Class.forName(DefaultBusExternalEvent.class.getName());
        final ExtBusEvent obj = (ExtBusEvent) mapper.readValue(json, claz);
        Assert.assertEquals(obj.getAccountId(), accountId);
        Assert.assertEquals(obj.getObjectId(), objectId);
        Assert.assertEquals(obj.getTenantId(), tenantId);
        Assert.assertEquals(obj.getObjectType(), objectType);
        Assert.assertEquals(obj.getEventType(), extBusEventType);
    }

    @Test(groups = "fast")
    public void testBusExternalEventWithMissingMetadata() throws Exception {
        final String jsonWithMetadata = "{\"objectId\":\"273ff2ed-5442-4d10-971f-3cc2414fe33b\",\"accountId\":\"c3b5b220-aaa1-406e-abd0-e8448b140082\",\"tenantId\":\"6962cf97-5fc2-4ef6-9099-3806acdb134d\",\"objectType\":\"ACCOUNT\",\"eventType\":\"ACCOUNT_CREATION\",\"metaData\":null}";
        final String jsonWithoutMetadata = "{\"objectId\":\"273ff2ed-5442-4d10-971f-3cc2414fe33b\",\"accountId\":\"c3b5b220-aaa1-406e-abd0-e8448b140082\",\"tenantId\":\"6962cf97-5fc2-4ef6-9099-3806acdb134d\",\"objectType\":\"ACCOUNT\",\"eventType\":\"ACCOUNT_CREATION\"}";
        final Class<?> claz = Class.forName(DefaultBusExternalEvent.class.getName());
        final ExtBusEvent obj = (ExtBusEvent) mapper.readValue(jsonWithoutMetadata, claz);
        Assert.assertTrue(obj.getObjectId().equals(UUID.fromString("273ff2ed-5442-4d10-971f-3cc2414fe33b")));
        Assert.assertNull(obj.getMetaData());
    }


    @Test(groups = "fast")
    public void testBusExternalEventWithJsonMetadata() throws Exception {
        final String eventWithJsonMetadata = "{\"objectId\":null,\"objectType\":\"SERVICE_BROADCAST\",\"eventType\":\"BROADCAST_SERVICE\",\"accountId\":null,\"tenantId\":null,\"metaData\":\"{\\\"service\\\":\\\"nodes-service\\\",\\\"commandType\\\":\\\"START_PLUGIN\\\",\\\"eventJson\\\":\\\"{\\\\\\\"pluginName\\\\\\\":\\\\\\\"pluginName\\\\\\\",\\\\\\\"pluginVersion\\\\\\\":\\\\\\\"4.5.6\\\\\\\",\\\\\\\"properties\\\\\\\":[{\\\\\\\"key\\\\\\\":\\\\\\\"key\\\\\\\",\\\\\\\"value\\\\\\\":\\\\\\\"value\\\\\\\"}]}\\\"}\"}";
        final Class<?> claz = Class.forName(DefaultBusExternalEvent.class.getName());
        final ExtBusEvent obj = (ExtBusEvent) mapper.readValue(eventWithJsonMetadata, claz);
        Assert.assertTrue(obj.getObjectType().equals(ObjectType.SERVICE_BROADCAST));
        Assert.assertTrue(obj.getEventType().equals(ExtBusEventType.BROADCAST_SERVICE));
        Assert.assertNotNull(obj.getMetaData());
        Assert.assertTrue(obj.getMetaData().equals("{\"service\":\"nodes-service\",\"commandType\":\"START_PLUGIN\",\"eventJson\":\"{\\\"pluginName\\\":\\\"pluginName\\\",\\\"pluginVersion\\\":\\\"4.5.6\\\",\\\"properties\\\":[{\\\"key\\\":\\\"key\\\",\\\"value\\\":\\\"value\\\"}]}\"}"));
    }
}
