/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.server.security;

import javax.ws.rs.core.Response.Status;

import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.Tenant;
import org.killbill.billing.jaxrs.TestJaxrsBase;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class TestTenantFilter extends TestJaxrsBase {

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        if (hasFailed()) {
            return;
        }

        // Default credentials
        loginTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET);
    }

    @Test(groups = "slow")
    public void testTenantShouldOnlySeeOwnAccount() throws Exception {
        // Try to create an account without being logged-in
        logoutTenant();
        try {
            accountApi.createAccount(getAccount(), requestOptions);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getResponse().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());
        }
        callbackServlet.assertListenerStatus();

        // Create the tenant
        final Tenant tenant1 = createTenant("pierre", "pierreIsFr3nch", true);

        final Account account1 = createAccount();
        Assert.assertEquals(accountApi.getAccountByKey(account1.getExternalKey(), requestOptions), account1);

        logoutTenant();

        // Create another tenant
        createTenant("stephane", "stephane1sAlsoFr3nch", true);

        final Account account2 = createAccount();
        Assert.assertEquals(accountApi.getAccountByKey(account2.getExternalKey(), requestOptions), account2);

        // We should not be able to retrieve the first account as tenant2
        Assert.assertNull(accountApi.getAccountByKey(account1.getExternalKey(), requestOptions));

        // Same for tenant1 and account2
        loginTenant(tenant1.getApiKey(), tenant1.getApiSecret());
        Assert.assertNull(accountApi.getAccountByKey(account2.getExternalKey(), requestOptions));
    }
}
