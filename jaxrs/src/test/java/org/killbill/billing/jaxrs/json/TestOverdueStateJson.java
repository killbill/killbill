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

import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestOverdueStateJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String name = UUID.randomUUID().toString();
        final String externalMessage = UUID.randomUUID().toString();
        final boolean disableEntitlementAndChangesBlocked = true;
        final boolean blockChanges = false;
        final boolean clearState = true;
        final int reevaluationIntervalDays = 100;
        final OverdueStateJson overdueStateJson = new OverdueStateJson(name, externalMessage,
                                                                       disableEntitlementAndChangesBlocked, blockChanges, clearState,
                                                                       reevaluationIntervalDays);
        Assert.assertEquals(overdueStateJson.getName(), name);
        Assert.assertEquals(overdueStateJson.getExternalMessage(), externalMessage);
        Assert.assertEquals(overdueStateJson.isDisableEntitlementAndChangesBlocked(), (Boolean) disableEntitlementAndChangesBlocked);
        Assert.assertEquals(overdueStateJson.isBlockChanges(), (Boolean) blockChanges);
        Assert.assertEquals(overdueStateJson.isClearState(), (Boolean) clearState);
        Assert.assertEquals(overdueStateJson.getReevaluationIntervalDays(), (Integer) reevaluationIntervalDays);

        final String asJson = mapper.writeValueAsString(overdueStateJson);
        final OverdueStateJson fromJson = mapper.readValue(asJson, OverdueStateJson.class);
        Assert.assertEquals(fromJson, overdueStateJson);
    }
}
