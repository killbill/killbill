/*
 * Copyright 2016 The Billing Project, LLC
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

import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.testng.Assert;
import org.testng.annotations.Test;

import net.sf.ehcache.Ehcache;

public class TestCache extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can Invalidate (clear) a Cache by name")
    public void testInvalidateCacheByName() throws Exception {
        // get Ehcache item with name "record-id"
        final Ehcache cache = cacheManager.getEhcache(CacheType.RECORD_ID.getCacheName());
        // verify that it is not null and has one stored key (the default tenant created for all integration tests)
        Assert.assertNotNull(cache);
        Assert.assertEquals(cache.getSize(), 1);

        // invalidate the specified cache
        killBillClient.invalidatesCacheByName(cache.getName(), requestOptions);

        // verify that now the cache is empty and has no keys stored
        Assert.assertEquals(cache.getSize(), 0);
    }

    @Test(groups = "slow", description = "Can Invalidate (clear) all available Caches")
    public void testInvalidateAllCaches() throws Exception {
        // get Ehcache item with name "record-id"
        final Ehcache cache = cacheManager.getEhcache(CacheType.RECORD_ID.getCacheName());
        // verify that it is not null and has one stored key (the default tenant created for all integration tests)
        Assert.assertNotNull(cache);
        Assert.assertEquals(cache.getSize(), 1);

        // invalidate all caches
        killBillClient.invalidatesAllCaches(requestOptions);

        // verify that now the cache is empty and has no keys stored
        Assert.assertEquals(cache.getSize(), 0);
    }

    @Test(groups = "slow", description = "Can Invalidate (clear) all Account Caches by accountId")
    public void testInvalidateCacheByAccount() throws Exception {
        final Account input = createAccountNoPMBundleAndSubscription();

        // get all caches per account level
        final Ehcache accountRecordIdCache = cacheManager.getEhcache(CacheType.ACCOUNT_RECORD_ID.getCacheName());
        final Ehcache accountImmutableCache = cacheManager.getEhcache(CacheType.ACCOUNT_IMMUTABLE.getCacheName());
        final Ehcache accountBcdCache = cacheManager.getEhcache(CacheType.ACCOUNT_BCD.getCacheName());

        // verify that they are not null and have the accountId stored as a key (the account created before)
        Assert.assertNotNull(accountRecordIdCache);
        Assert.assertNotNull(accountRecordIdCache.get(input.getAccountId().toString()));
        Assert.assertNotNull(accountImmutableCache);
        Assert.assertNotNull(accountImmutableCache.get(input.getAccountId()));
        Assert.assertNotNull(accountBcdCache);
        Assert.assertNotNull(accountBcdCache.get(input.getAccountId()));

        // invalidate caches per account level by accountId
        killBillClient.invalidatesCacheByAccount(input.getAccountId().toString(), requestOptions);

        // verify that now the caches don't have the accountId key stored
        Assert.assertNull(accountRecordIdCache.get(input.getAccountId().toString()));
        Assert.assertNull(accountImmutableCache.get(input.getAccountId()));
        Assert.assertNull(accountBcdCache.get(input.getAccountId()));
    }

    @Test(groups = "slow", description = "Can Invalidate (clear) all Tenant Caches by tenantId")
    public void testInvalidateCacheByTenant() throws Exception {

        // create a new tenant so it can be stored in the cache
        final Tenant tenant = new Tenant();
        tenant.setApiKey("testApiKey");
        tenant.setApiSecret("testApiSecret");
        final Tenant input = killBillClient.createTenant(tenant, false, requestOptions);

        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoice();

        // get all caches per tenant level
        final Ehcache tenantRecordIdCache = cacheManager.getEhcache(CacheType.TENANT_RECORD_ID.getCacheName());
        final Ehcache tenantPaymentStateMachineConfigCache = cacheManager.getEhcache(CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG.getCacheName());
        final Ehcache tenantCache = cacheManager.getEhcache(CacheType.TENANT.getCacheName());
        final Ehcache tenantKvCache = cacheManager.getEhcache(CacheType.TENANT_KV.getCacheName());
        final Ehcache tenantConfigCache = cacheManager.getEhcache(CacheType.TENANT_CONFIG.getCacheName());
        final Ehcache tenantOverdueConfigCache = cacheManager.getEhcache(CacheType.TENANT_OVERDUE_CONFIG.getCacheName());
        final Ehcache tenantCatalogCache = cacheManager.getEhcache(CacheType.TENANT_CATALOG.getCacheName());

        // verify that they are not null and empty
        Assert.assertNotNull(tenantRecordIdCache);
        Assert.assertFalse(tenantRecordIdCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantPaymentStateMachineConfigCache);
        Assert.assertFalse(tenantPaymentStateMachineConfigCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantCache);
        Assert.assertFalse(tenantCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantKvCache);
        Assert.assertFalse(tenantKvCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantConfigCache);
        Assert.assertFalse(tenantConfigCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantOverdueConfigCache);
        Assert.assertFalse(tenantOverdueConfigCache.getKeys().isEmpty());
        Assert.assertNotNull(tenantCatalogCache);
        Assert.assertFalse(tenantCatalogCache.getKeys().isEmpty());

        // invalidate caches per tenant level by tenantId
        killBillClient.invalidatesCacheByTenant(input.getTenantId().toString(), requestOptions);

        // TODO: verify that now the caches are empty
//        Assert.assertTrue(tenantRecordIdCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantPaymentStateMachineConfigCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantKvCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantConfigCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantOverdueConfigCache.getKeys().isEmpty());
//        Assert.assertTrue(tenantCatalogCache.getKeys().isEmpty());
    }
}
