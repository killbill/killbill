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

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.PaymentMethod;
import org.killbill.billing.client.model.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.io.Resources;
import net.sf.ehcache.Ehcache;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestCache extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can Invalidate (clear) a Cache by name")
    public void testInvalidateCacheByName() throws Exception {
        // get Ehcache item with name "record-id"
        final Ehcache cache = cacheManager.getEhcache(CacheType.RECORD_ID.getCacheName());
        // verify that it is not null and has one stored key (the default tenant created for all integration tests)
        assertNotNull(cache);
        Assert.assertEquals(cache.getSize(), 1);

        // invalidate the specified cache
        killBillClient.invalidateCacheByName(cache.getName(), requestOptions);

        // verify that now the cache is empty and has no keys stored
        Assert.assertEquals(cache.getSize(), 0);
    }

    @Test(groups = "slow", description = "Can Invalidate (clear) all available Caches")
    public void testInvalidateAllCaches() throws Exception {
        // get Ehcache item with name "record-id"
        final Ehcache cache = cacheManager.getEhcache(CacheType.RECORD_ID.getCacheName());
        // verify that it is not null and has one stored key (the default tenant created for all integration tests)
        assertNotNull(cache);
        Assert.assertEquals(cache.getSize(), 1);

        // invalidate all caches
        killBillClient.invalidateAllCaches(requestOptions);

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
        assertNotNull(accountRecordIdCache);
        assertNotNull(accountRecordIdCache.get(input.getAccountId().toString()));
        assertNotNull(accountImmutableCache);
        assertNotNull(accountImmutableCache.get(input.getAccountId()));
        assertNotNull(accountBcdCache);
        assertNotNull(accountBcdCache.get(input.getAccountId()));

        // invalidate caches per account level by accountId
        killBillClient.invalidateCacheByAccount(input.getAccountId().toString(), requestOptions);

        // verify that now the caches don't have the accountId key stored
        Assert.assertNull(accountRecordIdCache.get(input.getAccountId().toString()));
        Assert.assertNull(accountImmutableCache.get(input.getAccountId()));
        Assert.assertNull(accountBcdCache.get(input.getAccountId()));
    }

    @Test(groups = "slow", description = "Can Invalidate (clear) all Tenant Caches for current Tenant")
    public void testInvalidateCacheByTenant() throws Exception {
        // creating a new Tenant for this test
        final String testApiKey = "testApiKey";
        final String testApiSecret = "testApiSecret";
        final Tenant tenant = new Tenant();
        tenant.setApiKey(testApiKey);
        tenant.setApiSecret(testApiSecret);
        loginTenant(testApiKey, testApiSecret);
        Tenant currentTenant = killBillClient.createTenant(tenant, false, requestOptions);

        // using custom RequestOptions with the new Tenant created before
        RequestOptions inputOptions = RequestOptions.builder()
                                                    .withCreatedBy(createdBy)
                                                    .withReason(reason)
                                                    .withComment(comment)
                                                    .withTenantApiKey(currentTenant.getApiKey())
                                                    .withTenantApiSecret(currentTenant.getApiSecret())
                                                    .build();

        // Uploading the test catalog using the new Tenant created before
        killBillClient.uploadXMLCatalog(Resources.getResource("SpyCarAdvanced.xml").getPath(), inputOptions);

        // creating an Account with PaymentMethod and a Subscription
        createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoiceWithInputOptions(inputOptions);

        // get all caches per tenant level
        final Ehcache tenantRecordIdCache = cacheManager.getEhcache(CacheType.TENANT_RECORD_ID.getCacheName());
        final Ehcache tenantPaymentStateMachineConfigCache = cacheManager.getEhcache(CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG.getCacheName());
        final Ehcache tenantCache = cacheManager.getEhcache(CacheType.TENANT.getCacheName());
        final Ehcache tenantKvCache = cacheManager.getEhcache(CacheType.TENANT_KV.getCacheName());
        final Ehcache tenantConfigCache = cacheManager.getEhcache(CacheType.TENANT_CONFIG.getCacheName());
        final Ehcache tenantOverdueConfigCache = cacheManager.getEhcache(CacheType.TENANT_OVERDUE_CONFIG.getCacheName());
        final Ehcache tenantCatalogCache = cacheManager.getEhcache(CacheType.TENANT_CATALOG.getCacheName());

        // getting current Tenant's record Id from the specific Cache
        Long tenantRecordId = (Long) tenantRecordIdCache.get(currentTenant.getTenantId().toString()).getObjectValue();

        // verify that they are not null and have the expected tenant information
        assertNotNull(tenantRecordIdCache);
        assertNotNull(tenantRecordIdCache.get(currentTenant.getTenantId().toString()));
        assertNotNull(tenantPaymentStateMachineConfigCache);
        assertTrue(hasKeysByTenantRecordId(tenantPaymentStateMachineConfigCache, tenantRecordId.toString()));
        assertNotNull(tenantCache);
        assertNotNull(tenantCache.get(testApiKey));
        assertNotNull(tenantKvCache);
        assertTrue(hasKeysByTenantRecordId(tenantKvCache, tenantRecordId.toString()));
        assertNotNull(tenantConfigCache);
        assertNotNull(tenantConfigCache.get(tenantRecordId));
        assertNotNull(tenantOverdueConfigCache);
        assertNotNull(tenantOverdueConfigCache.get(tenantRecordId));
        assertNotNull(tenantCatalogCache);
        assertNotNull(tenantCatalogCache.get(tenantRecordId));

        // invalidate caches per tenant level
        killBillClient.invalidateCacheByTenant(inputOptions);

        // verify that now the caches don't have the previous values
        Assert.assertNull(tenantRecordIdCache.get(currentTenant.getTenantId().toString()));
        assertFalse(hasKeysByTenantRecordId(tenantPaymentStateMachineConfigCache, tenantRecordId.toString()));
        Assert.assertNull(tenantCache.get(testApiKey));
        assertFalse(hasKeysByTenantRecordId(tenantKvCache, tenantRecordId.toString()));
        Assert.assertNull(tenantConfigCache.get(tenantRecordId));
        Assert.assertNull(tenantOverdueConfigCache.get(tenantRecordId));
        Assert.assertNull(tenantCatalogCache.get(tenantRecordId));
    }

    private boolean hasKeysByTenantRecordId(final Ehcache tenantCache, final String tenantRecordId) {
        for (String key : (List<String>) tenantCache.getKeys()) {
            if (key.endsWith("::" + tenantRecordId)) {
                return true;
            }
        }
        return false;
    }

    private void createAccountWithPMBundleAndSubscriptionAndWaitForFirstInvoiceWithInputOptions(final RequestOptions inputOptions) throws Exception {
        Account account = killBillClient.createAccount(getAccount(), inputOptions);

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        info.setProperties(null);
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), true, PLUGIN_NAME, info);
        killBillClient.createPaymentMethod(paymentMethodJson, inputOptions);

        final Subscription subscription = new Subscription();
        subscription.setAccountId(account.getAccountId());
        subscription.setExternalKey(UUID.randomUUID().toString());
        subscription.setProductName("Sports");
        subscription.setProductCategory(ProductCategory.BASE);
        subscription.setBillingPeriod(BillingPeriod.MONTHLY);
        subscription.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        clock.resetDeltaFromReality();
        clock.setDay(new LocalDate(2013, 3, 1));
        final Subscription subscriptionJson = killBillClient.createSubscription(subscription, clock.getUTCToday(), DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, inputOptions);

        assertNotNull(subscriptionJson);
        clock.addDays(32);
        crappyWaitForLackOfProperSynchonization();
    }
}
