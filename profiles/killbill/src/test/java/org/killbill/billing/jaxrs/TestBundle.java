/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.model.BlockingStates;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.BlockingState;
import org.killbill.billing.client.model.gen.Bundle;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;

public class TestBundle extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can create bundles without an external key")
    public void testCreateBundleWithNoExternalKey() throws Exception {
        final Account accountJson;
        accountJson = createAccount();

        final Subscription subscription = createSubscription(accountJson.getAccountId(), null, "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        Assert.assertNotNull(subscription.getExternalKey());
    }

    @Test(groups = "slow", description = "Can retrieve bundles by external key")
    public void testBundleOk() throws Exception {
        final Account accountJson = createAccount();

        createSubscription(accountJson.getAccountId(), "123467", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        // Retrieves by external key
        final List<Bundle> objFromJson = accountApi.getAccountBundles(accountJson.getAccountId(), "123467", null, requestOptions);
        Assert.assertEquals(objFromJson.size(), 1);
    }

    @Test(groups = "slow", description = "Can retrieve account bundles")
    public void testBundleFromAccount() throws Exception {
        final Account accountJson = createAccount();
        createSubscription(accountJson.getAccountId(), "156567", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        createSubscription(accountJson.getAccountId(), "265658", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        final List<Bundle> objFromJson = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        Assert.assertEquals(objFromJson.size(), 2);
    }

    @Test(groups = "slow", description = "Can handle non existent bundle")
    public void testBundleNonExistent() throws Exception {
        final Account accountJson = createAccount();

        // ID
        Assert.assertNull(bundleApi.getBundle(UUID.randomUUID(), requestOptions));

        // External Key
        Assert.assertTrue(bundleApi.getBundleByKey(UUID.randomUUID().toString(), requestOptions).isEmpty());
        
        Assert.assertTrue(bundleApi.getBundleByKey(UUID.randomUUID().toString(), requestOptions).isEmpty());

        // Account Id
        Assert.assertTrue(accountApi.getAccountBundles(accountJson.getAccountId(), "98374982743892", null, requestOptions).isEmpty());
        Assert.assertTrue(accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions).isEmpty());

    }

    @Test(groups = "slow", description = "Can handle non existent account")
    public void testAccountNonExistent() throws Exception {
        Assert.assertTrue(accountApi.getAccountBundles(UUID.randomUUID(), null, null, requestOptions).isEmpty());
    }

    @Test(groups = "slow", description = "Can transfer bundle")
    public void testBundleTransfer() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String bundleExternalKey = "93199";

        final Subscription entitlementJsonNoEvents = createSubscription(accountJson.getAccountId(), bundleExternalKey, productName,
                                                                       ProductCategory.BASE, term, true);

        Bundles existingBundles = bundleApi.getBundleByKey(bundleExternalKey, requestOptions);
        assertEquals(existingBundles.size(), 1);
        Bundle originalBundle = existingBundles.get(0);
        existingBundles = bundleApi.getBundleByKey(bundleExternalKey, requestOptions);
        assertEquals(existingBundles.size(), 1);
        originalBundle = existingBundles.get(0);
        assertEquals(originalBundle.getAccountId(), accountJson.getAccountId());
        assertEquals(originalBundle.getAccountId(), accountJson.getAccountId());
        assertEquals(originalBundle.getExternalKey(), bundleExternalKey);

        final Account newAccount = createAccountWithDefaultPaymentMethod();

        final Bundle bundle = new Bundle();
        bundle.setAccountId(newAccount.getAccountId());
        bundle.setBundleId(entitlementJsonNoEvents.getBundleId());
        bundleApi.transferBundle(entitlementJsonNoEvents.getBundleId(), bundle, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        existingBundles = bundleApi.getBundleByKey(bundleExternalKey, requestOptions);
        assertEquals(existingBundles.size(), 1);
        final Bundle newBundle = existingBundles.get(0);
        assertNotEquals(newBundle.getBundleId(), originalBundle.getBundleId());
        assertEquals(newBundle.getExternalKey(), originalBundle.getExternalKey());
        assertEquals(newBundle.getAccountId(), newAccount.getAccountId());

        final Bundles bundles = bundleApi.getBundleByKey(bundleExternalKey, true, AuditLevel.NONE, requestOptions);
        assertEquals(bundles.size(), 2);
        assertSubscriptionState(bundles, originalBundle.getBundleId(), EntitlementState.CANCELLED);
        assertSubscriptionState(bundles, newBundle.getBundleId(), EntitlementState.ACTIVE);
    }

    private void assertSubscriptionState(final Bundles bundles, final UUID bundleId, final EntitlementState expectedState) {
        final Bundle bundle = Iterables.tryFind(bundles, new Predicate<Bundle>() {
            @Override
            public boolean apply(final Bundle input) {
                return input.getBundleId().equals(bundleId);
            }
        }).orNull();

        assertNotNull(bundle);
        assertEquals(bundle.getSubscriptions().get(0).getState(), expectedState);
    }

    @Test(groups = "slow", description = "Block a bundle")
    public void testBlockBundle() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String bundleExternalKey = "93199";

        final Subscription entitlement = createSubscription(accountJson.getAccountId(), bundleExternalKey, productName,
                                                           ProductCategory.BASE, term, true);

        Bundles existingBundles = bundleApi.getBundleByKey(bundleExternalKey, requestOptions);
        assertEquals(existingBundles.size(), 1);
        final Bundle bundle = existingBundles.get(0);
        assertEquals(bundle.getAccountId(), accountJson.getAccountId());
        assertEquals(bundle.getExternalKey(), bundleExternalKey);

        final BlockingState blockingState = new BlockingState(bundle.getBundleId(), "block", "service", false, true, true, null, BlockingStateType.SUBSCRIPTION_BUNDLE, null);
        bundleApi.addBundleBlockingState(bundle.getBundleId(), blockingState, clock.getToday(DateTimeZone.forID(accountJson.getTimeZone())), ImmutableMap.<String, String>of(), requestOptions);

        final Subscription subscription = subscriptionApi.getSubscription(entitlement.getSubscriptionId(), requestOptions);
        assertEquals(subscription.getState(), EntitlementState.BLOCKED);

        clock.addDays(1);

        final BlockingState unblockingState = new BlockingState(bundle.getBundleId(), "unblock", "service", false, false, false, null, BlockingStateType.SUBSCRIPTION_BUNDLE, null);
        bundleApi.addBundleBlockingState(bundle.getBundleId(), unblockingState, clock.getToday(DateTimeZone.forID(accountJson.getTimeZone())), ImmutableMap.<String, String>of(), requestOptions);

        final Subscription subscription2 = subscriptionApi.getSubscription(entitlement.getSubscriptionId(), requestOptions);
        assertEquals(subscription2.getState(), EntitlementState.ACTIVE);

        final BlockingStates blockingStates = accountApi.getBlockingStates(accountJson.getAccountId(), null, ImmutableList.<String>of("service"), AuditLevel.FULL, requestOptions);
        Assert.assertEquals(blockingStates.size(), 2);

        final BlockingStates blockingStates2 = accountApi.getBlockingStates(accountJson.getAccountId(), ImmutableList.<BlockingStateType>of(BlockingStateType.SUBSCRIPTION_BUNDLE), null, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(blockingStates2.size(), 2);

        final BlockingStates blockingStates3 = accountApi.getBlockingStates(accountJson.getAccountId(), null, null, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(blockingStates3.size(), 3);
    }

    @Test(groups = "slow", description = "Can paginate and search through all bundles")
    public void testBundlesPagination() throws Exception {
        final Account accountJson = createAccount();

        for (int i = 0; i < 5; i++) {
            createSubscription(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        }

        final Bundles allBundles = bundleApi.getBundles(requestOptions);
        Assert.assertEquals(allBundles.size(), 5);

        for (final Bundle bundle : allBundles) {
            Assert.assertEquals(bundleApi.searchBundles(bundle.getBundleId().toString(), requestOptions).size(), 1);
            Assert.assertEquals(bundleApi.searchBundles(bundle.getAccountId().toString(), requestOptions).size(), 5);
            Assert.assertEquals(bundleApi.searchBundles(bundle.getExternalKey(), requestOptions).size(), 1);
        }

        Bundles page = bundleApi.getBundles(0L, 1L, AuditLevel.NONE, requestOptions);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allBundles.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }
}
