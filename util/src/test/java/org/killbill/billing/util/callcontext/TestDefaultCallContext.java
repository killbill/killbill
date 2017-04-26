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

package org.killbill.billing.util.callcontext;

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultCallContext extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testGetters() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final String userName = UUID.randomUUID().toString();
        final DateTime createdDate = clock.getUTCNow();
        final String reasonCode = UUID.randomUUID().toString();
        final String comment = UUID.randomUUID().toString();
        final UUID userToken = UUID.randomUUID();
        final DefaultCallContext callContext = new DefaultCallContext(accountId, tenantId, userName, createdDate, reasonCode, comment, userToken);

        Assert.assertEquals(callContext.getTenantId(), tenantId);
        Assert.assertEquals(callContext.getCreatedDate(), createdDate);
        Assert.assertNull(callContext.getCallOrigin());
        Assert.assertEquals(callContext.getComments(), comment);
        Assert.assertEquals(callContext.getReasonCode(), reasonCode);
        Assert.assertEquals(callContext.getUserName(), userName);
        Assert.assertEquals(callContext.getUpdatedDate(), createdDate);
        Assert.assertEquals(callContext.getUserToken(), userToken);
        Assert.assertNull(callContext.getUserType());
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID tenantId = UUID.randomUUID();
        final String userName = UUID.randomUUID().toString();
        final DateTime createdDate = clock.getUTCNow();
        final String reasonCode = UUID.randomUUID().toString();
        final String comment = UUID.randomUUID().toString();
        final UUID userToken = UUID.randomUUID();

        final DefaultCallContext callContext = new DefaultCallContext(accountId, tenantId, userName, createdDate, reasonCode, comment, userToken);
        Assert.assertEquals(callContext, callContext);

        final DefaultCallContext sameCallContext = new DefaultCallContext(accountId, tenantId, userName, createdDate, reasonCode, comment, userToken);
        Assert.assertEquals(sameCallContext, callContext);

        final DefaultCallContext otherCallContext = new DefaultCallContext(accountId, tenantId, UUID.randomUUID().toString(), createdDate, reasonCode, comment, userToken);
        Assert.assertNotEquals(otherCallContext, callContext);
    }
}
