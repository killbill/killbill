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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Bundle;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.Subscription;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

public class TestBundle extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can retrieve bundles by external key")
    public void testBundleOk() throws Exception {
        final Account accountJson = createAccount();

        createEntitlement(accountJson.getAccountId(), "123467", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        // Retrieves by external key
        final List<Bundle> objFromJson = killBillClient.getAccountBundles(accountJson.getAccountId(), "123467");
        Assert.assertEquals(objFromJson.size(), 1);
    }

    @Test(groups = "slow", description = "Can retrieve account bundles")
    public void testBundleFromAccount() throws Exception {
        final Account accountJson = createAccount();
        createEntitlement(accountJson.getAccountId(), "156567", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        createEntitlement(accountJson.getAccountId(), "265658", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);

        final List<Bundle> objFromJson = killBillClient.getAccountBundles(accountJson.getAccountId());
        Assert.assertEquals(objFromJson.size(), 2);
    }

    @Test(groups = "slow", description = "Can handle non existent bundle")
    public void testBundleNonExistent() throws Exception {
        final Account accountJson = createAccount();

        Assert.assertNull(killBillClient.getBundle(UUID.randomUUID()));
        Assert.assertTrue(killBillClient.getAccountBundles(accountJson.getAccountId(), "98374982743892").isEmpty());
        Assert.assertTrue(killBillClient.getAccountBundles(accountJson.getAccountId()).isEmpty());
    }

    @Test(groups = "slow", description = "Can handle non existent account")
    public void testAccountNonExistent() throws Exception {
        Assert.assertTrue(killBillClient.getAccountBundles(UUID.randomUUID()).isEmpty());
    }

    @Test(groups = "slow", description = "Can transfer bundle")
    public void testBundleTransfer() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String bundleExternalKey = "93199";

        final Subscription entitlementJsonNoEvents = createEntitlement(accountJson.getAccountId(), bundleExternalKey, productName,
                                                                       ProductCategory.BASE, term, true);

        final Bundle originalBundle = killBillClient.getBundle(bundleExternalKey);
        assertEquals(originalBundle.getAccountId(), accountJson.getAccountId());
        assertEquals(originalBundle.getExternalKey(), bundleExternalKey);

        final Account newAccount = createAccountWithDefaultPaymentMethod();

        final Bundle bundle = new Bundle();
        bundle.setAccountId(newAccount.getAccountId());
        bundle.setBundleId(entitlementJsonNoEvents.getBundleId());
        assertEquals(killBillClient.transferBundle(bundle, createdBy, reason, comment).getAccountId(), newAccount.getAccountId());

        final Bundle newBundle = killBillClient.getBundle(bundleExternalKey);
        assertNotEquals(newBundle.getBundleId(), originalBundle.getBundleId());
        assertEquals(newBundle.getExternalKey(), originalBundle.getExternalKey());
        assertEquals(newBundle.getAccountId(), newAccount.getAccountId());
    }

    @Test(groups = "slow", description = "Can paginate and search through all bundles")
    public void testBundlesPagination() throws Exception {
        final Account accountJson = createAccount();

        for (int i = 0; i < 5; i++) {
            createEntitlement(accountJson.getAccountId(), UUID.randomUUID().toString(), "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, true);
        }

        final Bundles allBundles = killBillClient.getBundles();
        Assert.assertEquals(allBundles.size(), 5);

        for (final Bundle bundle : allBundles) {
            Assert.assertEquals(killBillClient.searchBundles(bundle.getBundleId().toString()).size(), 1);
            Assert.assertEquals(killBillClient.searchBundles(bundle.getAccountId().toString()).size(), 5);
            Assert.assertEquals(killBillClient.searchBundles(bundle.getExternalKey()).size(), 1);
        }

        Bundles page = killBillClient.getBundles(0L, 1L);
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(page);
            Assert.assertEquals(page.size(), 1);
            Assert.assertEquals(page.get(0), allBundles.get(i));
            page = page.getNext();
        }
        Assert.assertNull(page);
    }
}
