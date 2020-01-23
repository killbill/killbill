/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.util.security.api;

import java.util.Set;

import org.apache.shiro.realm.Realm;
import org.killbill.billing.security.Permission;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TestDefaultSecurityApi extends UtilTestSuiteNoDB {

    @Test(groups = "fast")
    public void testRetrievePermissions() throws Exception {
        configureShiro();

        logout();
        final Set<String> anonsPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(anonsPermissions.size(), 0, "Invalid permissions: " + anonsPermissions);

        login("pierre");
        final Set<String> pierresPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(pierresPermissions.size(), 2);
        Assert.assertTrue(pierresPermissions.containsAll(ImmutableList.<String>of(Permission.INVOICE_CAN_CREDIT.toString(), Permission.INVOICE_CAN_ITEM_ADJUST.toString())));

        login("stephane");
        final Set<String> stephanesPermissions = securityApi.getCurrentUserPermissions(callContext);
        Assert.assertEquals(stephanesPermissions.size(), 1);
        Assert.assertTrue(stephanesPermissions.containsAll(ImmutableList.<String>of(Permission.PAYMENT_CAN_REFUND.toString())));
    }
}
