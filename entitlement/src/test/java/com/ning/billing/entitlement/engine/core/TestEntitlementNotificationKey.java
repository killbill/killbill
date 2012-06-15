/* 
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.entitlement.engine.core;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

public class TestEntitlementNotificationKey {

    @Test(groups = "fast")
    public void testKeyWithSeqId() {
        final UUID id = UUID.randomUUID();
        final int seq = 4;
        final EntitlementNotificationKey input = new EntitlementNotificationKey(id, seq);
        Assert.assertEquals(id.toString() + ":" + seq, input.toString());
        final EntitlementNotificationKey output = new EntitlementNotificationKey(input.toString());
        Assert.assertEquals(output, input);
    }

    @Test(groups = "fast")
    public void testKeyWithoutSeqId() {
        final UUID id = UUID.randomUUID();
        final int seq = 0;
        final EntitlementNotificationKey input = new EntitlementNotificationKey(id, seq);
        Assert.assertEquals(input.toString(), id.toString());
        final EntitlementNotificationKey output = new EntitlementNotificationKey(input.toString());
        Assert.assertEquals(output, input);
    }

}
