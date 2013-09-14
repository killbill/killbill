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

import com.ning.billing.jaxrs.TestJaxrsBase;
import com.ning.billing.jaxrs.json.AccountJson;

// Pure Multi-Tenancy test (no RBAC)
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
        Assert.assertEquals(createAccountNoValidation().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());

        // Create the tenant
        final String apiKeyTenant1 = "pierre";
        final String apiSecretTenant1 = "pierreIsFr3nch";
        createTenant(apiKeyTenant1, apiSecretTenant1);

        // We should still not be able to create an account
        Assert.assertEquals(createAccountNoValidation().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());

        // Now, let's log-in and try again
        loginTenant(apiKeyTenant1, apiSecretTenant1);
        final AccountJson account1 = createAccount();
        Assert.assertEquals(getAccountByExternalKey(account1.getExternalKey()), account1);

        logoutTenant();

        // Create another tenant
        final String apiKeyTenant2 = "stephane";
        final String apiSecretTenant2 = "stephane1sAlsoFr3nch";
        createTenant(apiKeyTenant2, apiSecretTenant2);

        // We should not be able to create an account before being logged-in
        Assert.assertEquals(createAccountNoValidation().getStatusCode(), Status.UNAUTHORIZED.getStatusCode());

        // Now, let's log-in and try again
        loginTenant(apiKeyTenant2, apiSecretTenant2);
        final AccountJson account2 = createAccount();
        Assert.assertEquals(getAccountByExternalKey(account2.getExternalKey()), account2);

        // We should not be able to retrieve the first account as tenant2
        Assert.assertEquals(getAccountByExternalKeyNoValidation(account1.getExternalKey()).getStatusCode(), Status.NOT_FOUND.getStatusCode());

        // Same for tenant1 and account2
        loginTenant(apiKeyTenant1, apiSecretTenant1);
        Assert.assertEquals(getAccountByExternalKeyNoValidation(account2.getExternalKey()).getStatusCode(), Status.NOT_FOUND.getStatusCode());
    }

    private void loginTenant(final String apiKey, final String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    private void logoutTenant() {
        apiKey = "";
        apiSecret = "";
    }
}
