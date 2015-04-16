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

package org.killbill.billing.util.security.api;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.security.Permission;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.UtilTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

public class TestDefaultSecurityApi extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testRetrievePermissions() throws Exception {
        configureShiro();

        // We don't want the Guice injected one (it has Shiro disabled)
        final SecurityApi securityApi = new DefaultSecurityApi(null);

        final Set<Permission> anonsPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(anonsPermissions.size(), 0);

        login("pierre");
        final Set<Permission> pierresPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(pierresPermissions.size(), 2);
        Assert.assertTrue(pierresPermissions.containsAll(ImmutableList.<Permission>of(Permission.INVOICE_CAN_CREDIT, Permission.INVOICE_CAN_ITEM_ADJUST)));

        login("stephane");
        final Set<Permission> stephanesPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(stephanesPermissions.size(), 1);
        Assert.assertTrue(stephanesPermissions.containsAll(ImmutableList.<Permission>of(Permission.PAYMENT_CAN_REFUND)));
    }
}
