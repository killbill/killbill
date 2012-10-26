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

package com.ning.billing.tenant.dao;

import java.util.UUID;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.tenant.TenantTestSuiteWithEmbeddedDb;
import com.ning.billing.tenant.api.DefaultTenant;
import com.ning.billing.tenant.security.KillbillCredentialsMatcher;
import com.ning.billing.util.svcsapi.bus.InternalBus;

public class TestDefaultTenantDao extends TenantTestSuiteWithEmbeddedDb {

    @Test(groups = "slow")
    public void testWeCanStoreAndMatchCredentials() throws Exception {
        final DefaultTenantDao tenantDao = new DefaultTenantDao(getMysqlTestingHelper().getDBI(), Mockito.mock(InternalBus.class));

        final DefaultTenant tenant = new DefaultTenant(UUID.randomUUID(), null, null, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        tenantDao.create(tenant, internalCallContext);

        // Verify we can retrieve it
        Assert.assertEquals(tenantDao.getTenantByApiKey(tenant.getApiKey()).getId(), tenant.getId());

        // Verify we can authenticate against it
        final AuthenticationInfo authenticationInfo = tenantDao.getAuthenticationInfoForTenant(tenant.getId());

        // Good combo
        final AuthenticationToken goodToken = new UsernamePasswordToken(tenant.getApiKey(), tenant.getApiSecret());
        Assert.assertTrue(KillbillCredentialsMatcher.getCredentialsMatcher().doCredentialsMatch(goodToken, authenticationInfo));

        // Bad combo
        final AuthenticationToken badToken = new UsernamePasswordToken(tenant.getApiKey(), tenant.getApiSecret() + "T");
        Assert.assertFalse(KillbillCredentialsMatcher.getCredentialsMatcher().doCredentialsMatch(badToken, authenticationInfo));
    }

    @Test(groups = "slow")
    public void testTenantKeyValue() throws Exception {

        final DefaultTenantDao tenantDao = new DefaultTenantDao(getMysqlTestingHelper().getDBI(), Mockito.mock(InternalBus.class));
        final DefaultTenant tenant = new DefaultTenant(UUID.randomUUID(), null, null, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
        tenantDao.create(tenant, internalCallContext);

        tenantDao.addTenantKeyValue(tenant.getId(), "TheKey", "TheValue", internalCallContext);

        final String value  = tenantDao.getTenantValueForKey(tenant.getId(), "TheKey");
        Assert.assertEquals(value, "TheValue");

        tenantDao.deleteTenantKey(tenant.getId(), "TheKey");
    }
}
