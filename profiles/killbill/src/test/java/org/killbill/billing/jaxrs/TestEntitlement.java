/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.override.PriceOverride;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.PhasePriceOverride;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestEntitlement extends TestJaxrsBase {

    private static final int CALL_COMPLETION_TIMEOUT_SEC = 5;

    @Test(groups = "slow", description = "Can change plan and cancel a subscription")
    public void testEntitlementInTrialOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // Change plan IMM
        final String newProductName = "Assault-Rifle";

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setProductName(newProductName);
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(entitlementJson.getBillingPeriod());
        newInput.setPriceList(entitlementJson.getPriceList());
        objFromJson = killBillClient.updateSubscription(newInput, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);
        Assert.assertNotNull(objFromJson);

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel IMM (Billing EOT)
        killBillClient.cancelSubscription(newInput.getSubscriptionId(), CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNotNull(objFromJson.getCancelledDate());
        assertTrue(objFromJson.getCancelledDate().compareTo(new LocalDate(clock.getUTCNow())) == 0);
    }

    @Test(groups = "slow", description = "Can cancel and uncancel a subscription")
    public void testEntitlementUncancel() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel EOT
        killBillClient.cancelSubscription(entitlementJson.getSubscriptionId(), EntitlementActionPolicy.END_OF_TERM,
                                          BillingActionPolicy.END_OF_TERM, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNotNull(objFromJson.getCancelledDate());

        // Uncancel
        killBillClient.uncancelSubscription(entitlementJson.getSubscriptionId(), createdBy, reason, comment);

        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId());
        assertNull(objFromJson.getCancelledDate());
    }

    @Test(groups = "slow", description = "Can handle non existent subscription")
    public void testWithNonExistentEntitlement() throws Exception {
        final UUID subscriptionId = UUID.randomUUID();
        final Subscription subscription = new Subscription();
        subscription.setAccountId(UUID.randomUUID());
        subscription.setSubscriptionId(subscriptionId);
        subscription.setProductName("Pistol");
        subscription.setProductCategory(ProductCategory.BASE);
        subscription.setBillingPeriod(BillingPeriod.ANNUAL);
        subscription.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        try {
            killBillClient.updateSubscription(subscription, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getClassName(), "java.lang.IllegalStateException");
        }

        try {
            killBillClient.cancelSubscription(subscriptionId, createdBy, reason, comment);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getClassName(), "java.lang.IllegalStateException");
        }

        try {
            killBillClient.getSubscription(subscriptionId);
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getClassName(), "java.lang.IllegalStateException");
        }
    }

    @Test(groups = "slow", description = "Can override billing policy on change")
    public void testOverridePolicy() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        final Subscription subscriptionJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = killBillClient.getSubscription(subscriptionJson.getSubscriptionId());
        Assert.assertTrue(objFromJson.equals(subscriptionJson));
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.ANNUAL);

        // Change billing period immediately
        final Subscription newInput = new Subscription();
        newInput.setAccountId(accountJson.getAccountId());
        newInput.setSubscriptionId(subscriptionJson.getSubscriptionId());
        newInput.setProductName(subscriptionJson.getProductName());
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(BillingPeriod.MONTHLY);
        newInput.setPriceList(subscriptionJson.getPriceList());
        objFromJson = killBillClient.updateSubscription(newInput, BillingActionPolicy.IMMEDIATE, CALL_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);
        Assert.assertNotNull(objFromJson);
        assertEquals(objFromJson.getBillingPeriod(), BillingPeriod.MONTHLY);
    }


    @Test(groups = "slow", description = "Can override a price when creating a subscription")
    public void testOverridePrice() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;


        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setExternalKey("identical");
        input.setProductName(productName);
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final List<PhasePriceOverride> overrides = new ArrayList<PhasePriceOverride>();
        overrides.add(new PhasePriceOverride(null, PhaseType.TRIAL.toString(), BigDecimal.TEN, null));
        input.setPriceOverrides(overrides);

        final Subscription subscription =  killBillClient.createSubscription(input,  DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, createdBy, reason, comment);

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, AuditLevel.FULL);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getAmount().compareTo(BigDecimal.TEN), 0);
    }

}
