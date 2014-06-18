/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import java.util.HashSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.security.Permission;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

public class TestSecurity extends TestJaxrsBase {

    @Test(groups = "slow")
    public void testPermissions() throws Exception {
        logout();

        try {
            killBillClient.getPermissions();
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getResponse().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());
        }

        // See src/test/resources/shiro.ini

        final List<String> pierresPermissions = getPermissions("pierre", "password");
        Assert.assertEquals(pierresPermissions.size(), 2);
        Assert.assertEquals(new HashSet<String>(pierresPermissions), ImmutableSet.<String>of(Permission.INVOICE_CAN_CREDIT.toString(), Permission.INVOICE_CAN_ITEM_ADJUST.toString()));

        final List<String> stephanesPermissions = getPermissions("stephane", "password");
        Assert.assertEquals(stephanesPermissions.size(), 1);
        Assert.assertEquals(new HashSet<String>(stephanesPermissions), ImmutableSet.<String>of(Permission.PAYMENT_CAN_REFUND.toString()));
    }

    private List<String> getPermissions(@Nullable final String username, @Nullable final String password) throws Exception {
        login(username, password);
        return killBillClient.getPermissions();
    }
}
