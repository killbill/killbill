/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Account;
import org.killbill.billing.client.model.BulkBaseSubscriptionAndAddOns;
import org.killbill.billing.client.model.Bundle;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.Invoice;
import org.killbill.billing.client.model.PhasePriceOverride;
import org.killbill.billing.client.model.Subscription;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
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
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());

        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);
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
        objFromJson = killBillClient.updateSubscription(newInput, CALL_COMPLETION_TIMEOUT_SEC, requestOptions);
        Assert.assertNotNull(objFromJson);

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel IMM (Billing EOT)
        killBillClient.cancelSubscription(newInput.getSubscriptionId(), CALL_COMPLETION_TIMEOUT_SEC, requestOptions);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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
        Subscription objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);

        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // MOVE AFTER TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(31));
        clock.addDeltaFromReality(it.toDurationMillis());

        crappyWaitForLackOfProperSynchonization();

        // Cancel EOT
        killBillClient.cancelSubscription(entitlementJson.getSubscriptionId(), EntitlementActionPolicy.END_OF_TERM,
                                          BillingActionPolicy.END_OF_TERM, CALL_COMPLETION_TIMEOUT_SEC, requestOptions);

        // Retrieves to check EndDate
        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNotNull(objFromJson.getCancelledDate());
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Uncancel
        killBillClient.uncancelSubscription(entitlementJson.getSubscriptionId(), requestOptions);

        objFromJson = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNull(objFromJson.getCancelledDate());
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));
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

        assertNull(killBillClient.updateSubscription(subscription, CALL_COMPLETION_TIMEOUT_SEC, requestOptions));

        killBillClient.cancelSubscription(subscriptionId, requestOptions);

        assertNull(killBillClient.getSubscription(subscriptionId, requestOptions));
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
        Subscription objFromJson = killBillClient.getSubscription(subscriptionJson.getSubscriptionId(), requestOptions);
        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        subscriptionJson.setPriceOverrides(null);

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
        objFromJson = killBillClient.updateSubscription(newInput, BillingActionPolicy.IMMEDIATE, CALL_COMPLETION_TIMEOUT_SEC, requestOptions );
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
        overrides.add(new PhasePriceOverride(null, null, PhaseType.TRIAL.toString(), BigDecimal.TEN, null));
        input.setPriceOverrides(overrides);

        final Subscription subscription = killBillClient.createSubscription(input, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);
        Assert.assertEquals(subscription.getPriceOverrides().size(), 2);

        Assert.assertEquals(subscription.getEvents().size(), 3);
        Assert.assertEquals(subscription.getEvents().get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT.name());
        Assert.assertEquals(subscription.getEvents().get(0).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription.getEvents().get(0).getPhase(), "shotgun-monthly-1-trial");
        Assert.assertEquals(subscription.getEvents().get(0).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription.getEvents().get(0).getProduct(), "Shotgun");

        Assert.assertEquals(subscription.getEvents().get(1).getEventType(), SubscriptionEventType.START_BILLING.name());
        Assert.assertEquals(subscription.getEvents().get(1).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription.getEvents().get(1).getPhase(), "shotgun-monthly-1-trial");
        Assert.assertEquals(subscription.getEvents().get(1).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription.getEvents().get(1).getProduct(), "Shotgun");

        Assert.assertEquals(subscription.getEvents().get(2).getEventType(), SubscriptionEventType.PHASE.name());
        Assert.assertEquals(subscription.getEvents().get(2).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription.getEvents().get(2).getPhase(), "shotgun-monthly-1-evergreen");
        Assert.assertEquals(subscription.getEvents().get(2).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription.getEvents().get(2).getProduct(), "Shotgun");


        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, AuditLevel.FULL, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        // Move clock after phase
        clock.addDays(30);
        crappyWaitForLackOfProperSynchonization();

        final Subscription subscription2 = killBillClient.getSubscription(subscription.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription2.getEvents().size(), 3);

        clock.addDays(3);

        // Change Plan
        final Subscription newInput = new Subscription();
        newInput.setSubscriptionId(subscription2.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        final Subscription subscription3 = killBillClient.updateSubscription(newInput, null, BillingActionPolicy.IMMEDIATE, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);

        Assert.assertEquals(subscription3.getEvents().size(), 4);
        Assert.assertEquals(subscription3.getEvents().get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT.name());
        Assert.assertEquals(subscription3.getEvents().get(0).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription3.getEvents().get(0).getPhase(), "shotgun-monthly-1-trial");
        Assert.assertEquals(subscription3.getEvents().get(0).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(0).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(1).getEventType(), SubscriptionEventType.START_BILLING.name());
        Assert.assertEquals(subscription3.getEvents().get(1).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription3.getEvents().get(1).getPhase(), "shotgun-monthly-1-trial");
        Assert.assertEquals(subscription3.getEvents().get(1).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(1).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(2).getEventType(), SubscriptionEventType.PHASE.name());
        Assert.assertEquals(subscription3.getEvents().get(2).getPlan(), "shotgun-monthly-1");
        Assert.assertEquals(subscription3.getEvents().get(2).getPhase(), "shotgun-monthly-1-evergreen");
        Assert.assertEquals(subscription3.getEvents().get(2).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(2).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(3).getEventType(), SubscriptionEventType.CHANGE.name());
        Assert.assertEquals(subscription3.getEvents().get(3).getPlan(), "pistol-monthly");
        Assert.assertEquals(subscription3.getEvents().get(3).getPhase(), "pistol-monthly-evergreen");
        Assert.assertEquals(subscription3.getEvents().get(3).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(3).getProduct(), "Pistol");
    }

    @Test(groups = "slow", description = "Create a base entitlement and also addOns entitlements under the same bundle")
    public void testEntitlementWithAddOnsWithWRITTEN_OFF() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccount();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setExternalKey("base");
        base.setProductName("Shotgun");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setExternalKey("");
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setExternalKey("");
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final Bundle bundle = killBillClient.createSubscriptionWithAddOns(subscriptions, null, 10, requestOptions);
        assertNotNull(bundle);
        assertEquals(bundle.getExternalKey(), "base");
        assertEquals(bundle.getSubscriptions().size(), 3);

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getBalance().compareTo(BigDecimal.ZERO), 1);
        assertEquals(killBillClient.getInvoiceTags(invoices.get(0).getInvoiceId(), requestOptions).size(), 0);

        final Bundles accountBundles = killBillClient.getAccountBundles(accountJson.getAccountId(), requestOptions);
        assertEquals(accountBundles.size(), 1);
        for (final Subscription subscription : accountBundles.get(0).getSubscriptions()) {
            assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        }

        killBillClient.closeAccount(accountJson.getAccountId(), true, true, false, requestOptions);

        final Bundles accountBundlesAfterClose = killBillClient.getAccountBundles(accountJson.getAccountId(), requestOptions);
        assertEquals(accountBundlesAfterClose.size(), 1);
        for (final Subscription subscription : accountBundlesAfterClose.get(0).getSubscriptions()) {
            assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        }

        final List<Invoice> invoicesAfterClose = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, requestOptions);
        assertEquals(invoicesAfterClose.size(), 1);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(killBillClient.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 1);
    }

    @Test(groups = "slow", description = "Create a bulk of base entitlement and addOns under the same transaction")
    public void testCreateEntitlementsWithAddOnsThenCloseAccountWithItemAdjustment() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccount();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setProductName("Shotgun");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final List<BulkBaseSubscriptionAndAddOns> bulkList = new ArrayList<BulkBaseSubscriptionAndAddOns>();
        bulkList.add(new BulkBaseSubscriptionAndAddOns(subscriptions));
        bulkList.add(new BulkBaseSubscriptionAndAddOns(subscriptions));

        final Bundles bundles = killBillClient.createSubscriptionsWithAddOns(bulkList, null, 10, requestOptions);
        assertNotNull(bundles);
        assertEquals(bundles.size(), 2);
        assertFalse(bundles.get(0).getExternalKey().equals(bundles.get(1).getExternalKey()));

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getBalance().compareTo(BigDecimal.ZERO), 1);
        assertEquals(killBillClient.getInvoiceTags(invoices.get(0).getInvoiceId(), requestOptions).size(), 0);

        final Bundles accountBundles = killBillClient.getAccountBundles(accountJson.getAccountId(), requestOptions);
        assertEquals(accountBundles.size(), 2);
        for (final Bundle bundle : accountBundles) {
            for (final Subscription subscription : bundle.getSubscriptions()) {
                assertEquals(subscription.getState(), EntitlementState.ACTIVE);
            }
        }

        killBillClient.closeAccount(accountJson.getAccountId(), true, false, true, requestOptions);

        final Bundles accountBundlesAfterClose = killBillClient.getAccountBundles(accountJson.getAccountId(), requestOptions);
        assertEquals(accountBundlesAfterClose.size(), 2);
        for (final Bundle bundle : accountBundlesAfterClose) {
            for (final Subscription subscription : bundle.getSubscriptions()) {
                assertEquals(subscription.getState(), EntitlementState.CANCELLED);
            }
        }

        final List<Invoice> invoicesAfterClose = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, requestOptions);
        assertEquals(invoicesAfterClose.size(), 1);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(killBillClient.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Create a bulk of base entitlements and addOns under the same transaction",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = "Missing Base Subscription for bundle 12345")
    public void testCreateEntitlementsWithoutBase() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription bp = new Subscription();
        bp.setAccountId(accountJson.getAccountId());
        bp.setProductCategory(ProductCategory.BASE);
        bp.setExternalKey("12345");

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(bp);
        subscriptions.add(addOn1);

        final List<BulkBaseSubscriptionAndAddOns> bulkList = new ArrayList<BulkBaseSubscriptionAndAddOns>();
        bulkList.add(new BulkBaseSubscriptionAndAddOns(subscriptions));
        bulkList.add(new BulkBaseSubscriptionAndAddOns(subscriptions));

        killBillClient.createSubscriptionsWithAddOns(bulkList, null, 10, requestOptions);
    }

    @Test(groups = "slow", description = "Create addOns in a bundle where BP subscrsiptions already exist")
    public void testEntitlementsWithAddOnsAndAlreadyExistingBP() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setExternalKey("foobarxyz");
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription subscription = killBillClient.createSubscription(input, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());
        base.setProductCategory(ProductCategory.BASE);
        base.setExternalKey("foobarxyz");

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final Iterable<BulkBaseSubscriptionAndAddOns> bulkBaseSubscriptionAndAddOns = ImmutableList.of(new BulkBaseSubscriptionAndAddOns(subscriptions));

        final Bundles bundles = killBillClient.createSubscriptionsWithAddOns(bulkBaseSubscriptionAndAddOns, null, 10, requestOptions);
        assertNotNull(bundles);
        assertEquals(bundles.size(), 1);
        assertEquals(bundles.get(0).getSubscriptions().size(), 3);

        final List<Invoice> invoices = killBillClient.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, AuditLevel.FULL, requestOptions);
        assertEquals(invoices.size(), 2);
    }

    @Test(groups = "slow", description = "Can create an entitlement in the future")
    public void testCreateEntitlementInTheFuture() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = killBillClient.createSubscription(input, initialDate.toLocalDate().plusMonths(1), -1, requestOptions);

        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertTrue(subscription.equals(entitlementJson));
        Assert.assertEquals(subscription.getPriceOverrides().size(), 2);
        Assert.assertEquals(subscription.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(subscription.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(subscription.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(subscription.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));
    }

    @Test(groups = "slow", description = "Can create an entitlement with an account with autoPayOff")
    public void testCreateEntitlementWithAutoPayOff() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign autoPaymentOff tag to account
        Tags tags = killBillClient.createAccountTag(accountJson.getAccountId(), new UUID(0L, 1L), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");

        // verify that number of invoices and payments for account is still 0
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 0);
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");
        final Subscription subscriptionJson = killBillClient.createSubscription(input, null, 10, requestOptions);
        assertNotNull(subscriptionJson);

        // verify that number of invoices is now 1
        assertEquals(killBillClient.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 1);

        // verify that number of payments is still 0 (no attempts)
        assertEquals(killBillClient.getPaymentsForAccount(accountJson.getAccountId(), requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription")
    public void testMoveEntitlementBCD() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createEntitlement(accountJson.getAccountId(), "99999", productName,
                                                               ProductCategory.BASE, term, true);

        Assert.assertEquals(entitlementJson.getBillCycleDayLocal(), new Integer(25));

        final Subscription updatedSubscription = new Subscription();
        updatedSubscription.setSubscriptionId(entitlementJson.getSubscriptionId());
        updatedSubscription.setBillCycleDayLocal(9);
        killBillClient.updateSubscriptionBCD(updatedSubscription, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);


        final Subscription result = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result.getBillCycleDayLocal(), new Integer(25));

        // 2012, 5, 9
        clock.addDays(14);
        crappyWaitForLackOfProperSynchonization();

        final Subscription result2 = killBillClient.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result2.getBillCycleDayLocal(), new Integer(9));
    }

    @Test(groups = "slow", description = "Can create subscription and change plan using planName")
    public void testEntitlementUsingPlanName() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setExternalKey("somethingSpecial");
        input.setPlanName("shotgun-monthly");

        final Subscription entitlementJson = killBillClient.createSubscription(input, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);
        Assert.assertEquals(entitlementJson.getProductName(), "Shotgun");
        Assert.assertEquals(entitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(entitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(entitlementJson.getPlanName(), "shotgun-monthly");


        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        final Subscription newEntitlementJson = killBillClient.updateSubscription(newInput, null, null, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, requestOptions);
        Assert.assertEquals(newEntitlementJson.getProductName(), "Pistol");
        Assert.assertEquals(newEntitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(newEntitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(newEntitlementJson.getPlanName(), "pistol-monthly");

    }


}
