/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import java.io.IOException;
import java.util.UUID;

import org.killbill.billing.tenant.TenantTestSuiteNoDB;
import org.redisson.client.codec.Codec;
import org.redisson.codec.SerializationCodec;
import org.testng.Assert;
import org.testng.annotations.Test;

import io.netty.buffer.ByteBuf;

public class TestDefaultTenant extends TenantTestSuiteNoDB {

    @Test(groups = "fast")
    public void testExternalizable() throws IOException {
        final DefaultTenant tenantdata = new DefaultTenant(UUID.randomUUID(), clock.getUTCNow(), clock.getUTCNow(), "er44TT-yy4r", "TTR445ee2", null);
        final Codec code = new SerializationCodec();
        final ByteBuf byteBuf = code.getValueEncoder().encode(tenantdata);
        final DefaultTenant tenantData2 = (DefaultTenant) code.getValueDecoder().decode(byteBuf, null);
        Assert.assertEquals(tenantData2, tenantdata);
    }
}