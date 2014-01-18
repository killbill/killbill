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

package com.ning.billing.server.security;

import javax.ws.rs.core.Response.Status;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.ning.billing.client.KillBillClient;
import com.ning.billing.client.KillBillClientException;
import com.ning.billing.client.KillBillHttpClient;
import com.ning.billing.client.model.Account;
import com.ning.billing.client.model.Tenant;
import com.ning.billing.jaxrs.TestJaxrsBase;

public class TestTenantFilter extends TestJaxrsBase {

    @AfterMethod(groups = "slow")
    public void tearDown() throws Exception {
        // Default credentials
        loginTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET);
    }

    @Test(groups = "slow")
    public void testTenantShouldOnlySeeOwnAccount() throws Exception {
        // Try to create an account without being logged-in
        logoutTenant();
        try {
            killBillClient.createAccount(getAccount(), createdBy, reason, comment);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getResponse().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());
        }

        // Create the tenant
        final String apiKeyTenant1 = "pierre";
        final String apiSecretTenant1 = "pierreIsFr3nch";
        loginTenant(apiKeyTenant1, apiSecretTenant1);
        final Tenant tenant1 = new Tenant();
        tenant1.setApiKey(apiKeyTenant1);
        tenant1.setApiSecret(apiSecretTenant1);
        killBillClient.createTenant(tenant1, createdBy, reason, comment);

        final Account account1 = createAccount();
        Assert.assertEquals(killBillClient.getAccount(account1.getExternalKey()), account1);

        logoutTenant();

        // Create another tenant
        final String apiKeyTenant2 = "stephane";
        final String apiSecretTenant2 = "stephane1sAlsoFr3nch";
        loginTenant(apiKeyTenant2, apiSecretTenant2);
        final Tenant tenant2 = new Tenant();
        tenant2.setApiKey(apiKeyTenant2);
        tenant2.setApiSecret(apiSecretTenant2);
        killBillClient.createTenant(tenant2, createdBy, reason, comment);

        final Account account2 = createAccount();
        Assert.assertEquals(killBillClient.getAccount(account2.getExternalKey()), account2);

        // We should not be able to retrieve the first account as tenant2
        Assert.assertNull(killBillClient.getAccount(account1.getExternalKey()));

        // Same for tenant1 and account2
        loginTenant(apiKeyTenant1, apiSecretTenant1);
        Assert.assertNull(killBillClient.getAccount(account2.getExternalKey()));
    }
}
