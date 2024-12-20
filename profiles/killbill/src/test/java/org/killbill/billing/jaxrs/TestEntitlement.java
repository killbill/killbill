/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.DefaultPriceListSet;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.UsageType;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.BlockingStates;
import org.killbill.billing.client.model.BulkSubscriptionsBundles;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Subscriptions;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AccountTimeline;
import org.killbill.billing.client.model.gen.BlockPrice;
import org.killbill.billing.client.model.gen.BlockingState;
import org.killbill.billing.client.model.gen.BulkSubscriptionsBundle;
import org.killbill.billing.client.model.gen.Bundle;
import org.killbill.billing.client.model.gen.EventSubscription;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.PhasePrice;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.client.model.gen.TierPrice;
import org.killbill.billing.client.model.gen.UsagePrice;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.util.api.AuditLevel;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestEntitlement extends TestJaxrsBase {

    @Test(groups = "slow", description = "Can change plan and cancel a subscription")
    public void testEntitlementInTrialOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term);

        // Retrieves with GET
        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getPrices().size(), 2);
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());

        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        Assert.assertEquals(subscription, entitlementJson);

        final Subscription subscription2 = subscriptionApi.getSubscriptionByKey(subscription.getExternalKey(), requestOptions);
        assertEquals(subscription2.getBundleExternalKey(), "99999");
        assertEquals(subscription2.getExternalKey(), subscription.getExternalKey());

        // Change the clock otherwise the CREATE event might be replaced (instead of having a CHANGE event)
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Change plan IMM
        final String newProductName = "Assault-Rifle";

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setProductName(newProductName);
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(entitlementJson.getBillingPeriod());
        newInput.setPriceList(entitlementJson.getPriceList());
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE, ExtBusEventType.SUBSCRIPTION_CHANGE, ExtBusEventType.INVOICE_CREATION);
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newInput, (LocalDate) null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(subscription);

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // Cancel IMM (Billing EOT)
        subscriptionApi.cancelSubscriptionPlan(newInput.getSubscriptionId(), (LocalDate) null, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves to check EndDate
        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNotNull(subscription.getCancelledDate());
        assertEquals(internalCallContext.toLocalDate(subscription.getCancelledDate()).compareTo(new LocalDate(clock.getUTCNow())), 0);

        final Bundles accountBundles = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        assertEquals(accountBundles.size(), 1);
        assertEquals(accountBundles.get(0).getSubscriptions().size(), 1);
        assertEquals(accountBundles.get(0).getSubscriptions().get(0).getState(), EntitlementState.CANCELLED);

        final Bundles bundlesByKey1 = bundleApi.getBundleByKey(entitlementJson.getBundleExternalKey(), true, AuditLevel.NONE, requestOptions);
        assertEquals(bundlesByKey1.size(), 1);
        assertEquals(bundlesByKey1.get(0).getSubscriptions().size(), 1);
        assertEquals(bundlesByKey1.get(0).getSubscriptions().get(0).getState(), EntitlementState.CANCELLED);

        final Bundles bundlesByKey2 = bundleApi.getBundleByKey(entitlementJson.getBundleExternalKey(), requestOptions);
        assertNotNull(bundlesByKey2);
        assertEquals(bundlesByKey2.size(), 0);

    }

    @Test(groups = "slow", description = "Can cancel and uncancel a subscription")
    public void testEntitlementUncancel() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term);

        // Retrieves with GET
        Subscription objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(objFromJson.getPrices().size(), 2);
        Assert.assertEquals(objFromJson.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);

        Assert.assertTrue(objFromJson.equals(entitlementJson));

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(31);
        callbackServlet.assertListenerStatus();

        // Cancel EOT
        subscriptionApi.cancelSubscriptionPlan(entitlementJson.getSubscriptionId(), (LocalDate) null, EntitlementActionPolicy.END_OF_TERM,
                                               BillingActionPolicy.END_OF_TERM, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves to check EndDate
        objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNotNull(objFromJson.getCancelledDate());
        Assert.assertEquals(objFromJson.getPrices().size(), 2);
        Assert.assertEquals(objFromJson.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Uncancel
        subscriptionApi.uncancelSubscriptionPlan(entitlementJson.getSubscriptionId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNull(objFromJson.getCancelledDate());
        Assert.assertEquals(objFromJson.getPrices().size(), 2);
        Assert.assertEquals(objFromJson.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));
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

        subscriptionApi.changeSubscriptionPlan(subscriptionId, subscription, (LocalDate) null, null, null, requestOptions);

        subscriptionApi.cancelSubscriptionPlan(subscriptionId, (LocalDate) null, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        assertNull(subscriptionApi.getSubscription(subscriptionId, requestOptions));
    }

    @Test(groups = "slow", description = "Can override billing policy on change")
    public void testOverridePolicy() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;

        final Subscription subscriptionJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                 ProductCategory.BASE, term);

        // Retrieves with GET
        Subscription objFromJson = subscriptionApi.getSubscription(subscriptionJson.getSubscriptionId(), requestOptions);
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
        subscriptionApi.changeSubscriptionPlan(subscriptionJson.getSubscriptionId(), newInput, (LocalDate) null, BillingActionPolicy.IMMEDIATE, NULL_PLUGIN_PROPERTIES, requestOptions);

        objFromJson = subscriptionApi.getSubscription(subscriptionJson.getSubscriptionId(), requestOptions);
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
        input.setBundleExternalKey("identical");
        input.setProductName(productName);
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final List<PhasePrice> overrides = new ArrayList<PhasePrice>();
        overrides.add(new PhasePrice(null, null, PhaseType.TRIAL.toString(), BigDecimal.TEN, null, null));
        input.setPriceOverrides(overrides);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        final Subscription subscription = subscriptionApi.createSubscription(input, (LocalDate) null, (LocalDate) null, null, null, false, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(subscription.getPrices().size(), 2);

        Assert.assertEquals(subscription.getEvents().size(), 3);
        Assert.assertEquals(subscription.getEvents().get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertMatches(subscription.getEvents().get(0).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription.getEvents().get(0).getPhase(), "shotgun-monthly-[1-9]+-trial");
        Assert.assertEquals(subscription.getEvents().get(0).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(subscription.getEvents().get(0).getProduct(), "Shotgun");

        Assert.assertEquals(subscription.getEvents().get(1).getEventType(), SubscriptionEventType.START_BILLING);
        assertMatches(subscription.getEvents().get(1).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription.getEvents().get(1).getPhase(), "shotgun-monthly-[1-9]+-trial");
        Assert.assertEquals(subscription.getEvents().get(1).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(subscription.getEvents().get(1).getProduct(), "Shotgun");

        Assert.assertEquals(subscription.getEvents().get(2).getEventType(), SubscriptionEventType.PHASE);
        assertMatches(subscription.getEvents().get(2).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription.getEvents().get(2).getPhase(), "shotgun-monthly-[1-9]+-evergreen");
        Assert.assertEquals(subscription.getEvents().get(2).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(subscription.getEvents().get(2).getProduct(), "Shotgun");

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.FULL, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getAmount().compareTo(BigDecimal.TEN), 0);

        // Move clock after phase
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        final Subscription subscription2 = subscriptionApi.getSubscription(subscription.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription2.getEvents().size(), 3);

        clock.addDays(3);

        // Change Plan
        final Subscription newInput = new Subscription();
        newInput.setSubscriptionId(subscription2.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        subscriptionApi.changeSubscriptionPlan(subscription2.getSubscriptionId(), newInput, (LocalDate) null, BillingActionPolicy.IMMEDIATE, NULL_PLUGIN_PROPERTIES, requestOptions);
        final Subscription subscription3 = subscriptionApi.getSubscription(subscription.getSubscriptionId(), requestOptions);

        Assert.assertEquals(subscription3.getEvents().size(), 4);
        Assert.assertEquals(subscription3.getEvents().get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertMatches(subscription3.getEvents().get(0).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription3.getEvents().get(0).getPhase(), "shotgun-monthly-[1-9]+-trial");
        Assert.assertEquals(subscription3.getEvents().get(0).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(0).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(1).getEventType(), SubscriptionEventType.START_BILLING);
        assertMatches(subscription3.getEvents().get(1).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription3.getEvents().get(1).getPhase(), "shotgun-monthly-[1-9]+-trial");
        Assert.assertEquals(subscription3.getEvents().get(1).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(1).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(2).getEventType(), SubscriptionEventType.PHASE);
        assertMatches(subscription3.getEvents().get(2).getPlan(), "shotgun-monthly-[1-9]+");
        assertMatches(subscription3.getEvents().get(2).getPhase(), "shotgun-monthly-[1-9]+-evergreen");
        Assert.assertEquals(subscription3.getEvents().get(2).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(2).getProduct(), "Shotgun");

        Assert.assertEquals(subscription3.getEvents().get(3).getEventType(), SubscriptionEventType.CHANGE);
        Assert.assertEquals(subscription3.getEvents().get(3).getPlan(), "pistol-monthly");
        Assert.assertEquals(subscription3.getEvents().get(3).getPhase(), "pistol-monthly-evergreen");
        Assert.assertEquals(subscription3.getEvents().get(3).getPriceList(), PriceListSet.DEFAULT_PRICELIST_NAME.toString());
        Assert.assertEquals(subscription3.getEvents().get(3).getProduct(), "Pistol");
    }

    private void assertMatches(final String actual, final String regexp) {
        Assert.assertTrue(Pattern.compile(regexp).matcher(actual).matches(), String.format("%s doesn't match pattern %s", actual, regexp));
    }

    @Test(groups = "slow", description = "Create a base entitlement and also addOns entitlements under the same bundle")
    public void testEntitlementWithAddOnsWithWRITTEN_OFF() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final String bundleExternalKey = "bundleKey12346542";

        final Account accountJson = createAccount();

        final Subscription base = new Subscription();
        base.setAccountId(accountJson.getAccountId());

        base.setBundleExternalKey(bundleExternalKey);
        base.setExternalKey("Base");
        base.setProductName("Shotgun");
        base.setProductCategory(ProductCategory.BASE);
        base.setBillingPeriod(BillingPeriod.MONTHLY);
        base.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setExternalKey("addOn1");
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setExternalKey("addOn2");
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscriptions subscriptions = new Subscriptions();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(subscriptions, (LocalDate) null, (LocalDate) null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(bundle);
        assertEquals(bundle.getExternalKey(), bundleExternalKey);
        assertEquals(bundle.getSubscriptions().size(), 3);
        int found = 0;
        for (int i = 0; i < 3; i++) {
            final Subscription cur = bundle.getSubscriptions().get(i);
            assertEquals(cur.getBundleExternalKey(), bundleExternalKey);
            if ("Shotgun".equals(cur.getProductName())) {
                assertEquals(cur.getExternalKey(), "Base");
                found++;
            } else if ("Telescopic-Scope".equals(cur.getProductName())) {
                assertEquals(cur.getExternalKey(), "addOn1");
                found++;
            } else if ("Laser-Scope".equals(cur.getProductName())) {
                assertEquals(cur.getExternalKey(), "addOn2");
                found++;
            }
        }
        assertEquals(found, 3);

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getBalance().compareTo(BigDecimal.ZERO), 1);
        assertEquals(invoiceApi.getInvoiceTags(invoices.get(0).getInvoiceId(), requestOptions).size(), 0);

        final Bundles accountBundles = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        assertEquals(accountBundles.size(), 1);
        for (final Subscription subscription : accountBundles.get(0).getSubscriptions()) {
            assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        }

        accountApi.closeAccount(accountJson.getAccountId(), true, true, false, true, requestOptions);

        final Bundles accountBundlesAfterClose = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        assertEquals(accountBundlesAfterClose.size(), 1);
        for (final Subscription subscription : accountBundlesAfterClose.get(0).getSubscriptions()) {
            assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        }

        final List<Invoice> invoicesAfterClose = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions);
        assertEquals(invoicesAfterClose.size(), 1);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceApi.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 1);
    }

    @Test(groups = "slow", description = "Create a bulk of base entitlement and addOns under the same transaction")
    public void testCreateEntitlementsWithAddOnsThenCloseAccountWithItemAdjustment() throws Exception { //TODO_1739 - Test disabled due to behavior change, revisit
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

        final BulkSubscriptionsBundle bulkList = new BulkSubscriptionsBundle();
        bulkList.setBaseEntitlementAndAddOns(subscriptions);

        final BulkSubscriptionsBundles input = new BulkSubscriptionsBundles();
        input.add(bulkList);
        input.add(bulkList);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED);
        final Bundles bundles = subscriptionApi.createSubscriptionsWithAddOns(input, null, null, false, false, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        assertNotNull(bundles);
        assertEquals(bundles.size(), 2);
        assertFalse(bundles.get(0).getExternalKey().equals(bundles.get(1).getExternalKey()));

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 1);
        assertEquals(invoices.get(0).getBalance().compareTo(BigDecimal.ZERO), 1);
        assertEquals(invoiceApi.getInvoiceTags(invoices.get(0).getInvoiceId(), requestOptions).size(), 0);

        final Bundles accountBundles = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        assertEquals(accountBundles.size(), 2);
        for (final Bundle bundle : accountBundles) {
            for (final Subscription subscription : bundle.getSubscriptions()) {
                assertEquals(subscription.getState(), EntitlementState.ACTIVE);
            }
        }

        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.INVOICE_ADJUSTMENT,
                                           ExtBusEventType.INVOICE_ADJUSTMENT,
                                           ExtBusEventType.INVOICE_ADJUSTMENT,
                                           ExtBusEventType.INVOICE_ADJUSTMENT,
                                           ExtBusEventType.BLOCKING_STATE);
        accountApi.closeAccount(accountJson.getAccountId(), true, false, true, true, requestOptions);
        callbackServlet.assertListenerStatus();

        final Bundles accountBundlesAfterClose = accountApi.getAccountBundles(accountJson.getAccountId(), null, null, requestOptions);
        assertEquals(accountBundlesAfterClose.size(), 2);
        for (final Bundle bundle : accountBundlesAfterClose) {
            for (final Subscription subscription : bundle.getSubscriptions()) {
                assertEquals(subscription.getState(), EntitlementState.CANCELLED);
            }
        }

        final List<Invoice> invoicesAfterClose = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoicesAfterClose.size(), 1);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceApi.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Create a bulk of base entitlements and addOns under the same transaction",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = "SubscriptionJson productName needs to be set when no planName is specified")
    public void testCreateSubscriptionsWithoutBase() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription bp1 = new Subscription();
        bp1.setAccountId(accountJson.getAccountId());
        bp1.setProductCategory(ProductCategory.BASE);
        bp1.setBundleExternalKey("12345");

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions1 = new ArrayList<Subscription>();
        subscriptions1.add(bp1);
        subscriptions1.add(addOn1);

        final Subscription bp2 = new Subscription();
        bp2.setAccountId(accountJson.getAccountId());
        bp2.setProductCategory(ProductCategory.BASE);
        bp2.setBundleExternalKey("54321");

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setProductName("Telescopic-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions2 = new ArrayList<Subscription>();
        subscriptions2.add(bp1);
        subscriptions2.add(addOn1);


        final BulkSubscriptionsBundle bulkList1 = new BulkSubscriptionsBundle();
        bulkList1.setBaseEntitlementAndAddOns(subscriptions1);
        final BulkSubscriptionsBundle bulkList2 = new BulkSubscriptionsBundle();
        bulkList2.setBaseEntitlementAndAddOns(subscriptions2);

        final BulkSubscriptionsBundles input = new BulkSubscriptionsBundles();
        input.add(bulkList1);
        input.add(bulkList2);

        final Bundles res = subscriptionApi.createSubscriptionsWithAddOns(input, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(res.size(), 2);
    }

    @Test(groups = "slow", description = "Create addOns in a bundle where BP subscription already exist")
    public void testEntitlementsWithAddOnsAndAlreadyExistingBP() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setBundleExternalKey("foobarxyz");
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription subscription = subscriptionApi.createSubscription(input, (LocalDate) null, (LocalDate) null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);

        final Subscription addOn1 = new Subscription();
        addOn1.setAccountId(accountJson.getAccountId());
        addOn1.setBundleId(subscription.getBundleId());
        addOn1.setProductName("Telescopic-Scope");
        addOn1.setProductCategory(ProductCategory.ADD_ON);
        addOn1.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn1.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final Subscription addOn2 = new Subscription();
        addOn2.setAccountId(accountJson.getAccountId());
        addOn2.setBundleId(subscription.getBundleId());
        addOn2.setProductName("Laser-Scope");
        addOn2.setProductCategory(ProductCategory.ADD_ON);
        addOn2.setBillingPeriod(BillingPeriod.MONTHLY);
        addOn2.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final BulkSubscriptionsBundles bulkSubscriptionsBundles = new BulkSubscriptionsBundles();

        final BulkSubscriptionsBundle bulkSubscriptionsBundle = new BulkSubscriptionsBundle();
        bulkSubscriptionsBundle.setBaseEntitlementAndAddOns(subscriptions);
        bulkSubscriptionsBundles.add(bulkSubscriptionsBundle);

        final Bundles bundles = subscriptionApi.createSubscriptionsWithAddOns(bulkSubscriptionsBundles, null, null, false, false, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(bundles);
        assertEquals(bundles.size(), 1);
        assertEquals(bundles.get(0).getSubscriptions().size(), 3);

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, false, false, false, true, null, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 2);
    }

    @Test(groups = "slow", description = "Can create an entitlement with a future entitlement date")
    public void testCreateSubscriptionEntitlementInTheFuture() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        // Verify callCompletion works (related to https://github.com/killbill/killbill/issues/1193)
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate.toLocalDate().plusMonths(1),
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.PENDING);
        Assert.assertEquals(entitlementJson.getChargedThroughDate(), initialDate.plusMonths(1).toLocalDate());
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate.toLocalDate());
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate.toLocalDate().plusMonths(1));
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription, entitlementJson);
        Assert.assertEquals(subscription.getPrices().size(), 2);
        Assert.assertEquals(subscription.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));
    }

    @Test(groups = "slow", description = "Can create an entitlement with a future billing date")
    public void testCreateSubscriptionBillingInTheFuture() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        // Verify callCompletion works (related to https://github.com/killbill/killbill/issues/1193)
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                null,
                                                                                initialDate.toLocalDate().plusMonths(1),
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertNull(entitlementJson.getChargedThroughDate());
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate.toLocalDate().plusMonths(1));
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate.toLocalDate());
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription, entitlementJson);
        Assert.assertEquals(subscription.getPrices().size(), 2);
        Assert.assertEquals(subscription.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));
    }

    @Test(groups = "slow", description = "Can create an entitlement with a future billing date v2 -- see https://github.com/killbill/killbill/pull/1234#discussion_r332148759")
    public void testCreateSubscriptionBillingInTheFutureV2() throws Exception {
        // 2012-04-25T00:03:42.000Z
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        // Move clock back to 2012-04-25T00:01:42.000Z (we want a reference time in the future)
        clock.setTime(new DateTime(2012, 4, 25, 0, 1, 42, 0));

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        // Verify callCompletion works (related to https://github.com/killbill/killbill/issues/1193)
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                null,
                                                                                new LocalDate(2012, 4, 25),
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertNull(entitlementJson.getChargedThroughDate());
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate.toLocalDate());
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate.toLocalDate());
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription, entitlementJson);
        Assert.assertEquals(subscription.getPrices().size(), 2);
        Assert.assertEquals(subscription.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));
    }



    @Test(groups = "slow", description = "Can create an entitlement with a past billing date")
    public void testCreateSubscriptionBillingInThePast() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        // Verify callCompletion works (related to https://github.com/killbill/killbill/issues/1193)
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                null,
                                                                                initialDate.toLocalDate().minusMonths(1),
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);


        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        verifyChargedThroughDate(entitlementJson.getSubscriptionId(), new LocalDate("2012-05-24"));
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate.toLocalDate().minusMonths(1));
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate.toLocalDate());
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);

        // See https://github.com/killbill/killbill/issues/1694
        Assert.assertEquals(subscription.getAccountId(), entitlementJson.getAccountId());
        Assert.assertEquals(subscription.getBundleId(), entitlementJson.getBundleId());
        Assert.assertEquals(subscription.getBundleExternalKey(), entitlementJson.getBundleExternalKey());
        Assert.assertEquals(subscription.getSubscriptionId(), entitlementJson.getSubscriptionId());
        Assert.assertEquals(subscription.getExternalKey(), entitlementJson.getExternalKey());
        Assert.assertEquals(subscription.getStartDate(), entitlementJson.getStartDate());
        Assert.assertEquals(subscription.getProductName(), entitlementJson.getProductName());
        Assert.assertEquals(subscription.getProductCategory(), entitlementJson.getProductCategory());
        Assert.assertEquals(subscription.getBillingPeriod(), entitlementJson.getBillingPeriod());
        Assert.assertEquals(subscription.getPhaseType(), entitlementJson.getPhaseType());
        Assert.assertEquals(subscription.getPriceList(), entitlementJson.getPriceList());
        Assert.assertEquals(subscription.getPlanName(), entitlementJson.getPlanName());
        Assert.assertEquals(subscription.getState(), entitlementJson.getState());
        Assert.assertEquals(subscription.getSourceType(), entitlementJson.getSourceType());
        Assert.assertEquals(subscription.getCancelledDate(), entitlementJson.getCancelledDate());
        Assert.assertEquals(subscription.getBillingStartDate(), entitlementJson.getBillingStartDate());
        Assert.assertEquals(subscription.getBillingEndDate(), entitlementJson.getBillingEndDate());
        Assert.assertEquals(subscription.getBillCycleDayLocal(), entitlementJson.getBillCycleDayLocal());
        Assert.assertEquals(subscription.getEvents(), entitlementJson.getEvents());
        Assert.assertEquals(subscription.getPriceOverrides(), entitlementJson.getPriceOverrides());
        Assert.assertEquals(subscription.getPrices(), entitlementJson.getPrices());

        Assert.assertEquals(subscription.getPrices().size(), 2);
        Assert.assertEquals(subscription.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));
    }

    @Test(groups = "slow", description = "Can create an entitlement with an account with autoPayOff -- https://github.com/killbill/killbill/issues/1193")
    public void testCreateChangeAndCancelSubscriptionWithAutoPayOff() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign autoPaymentOff tag to account
        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION);
        final Tags tags = accountApi.createAccountTags(accountJson.getAccountId(), List.of(new UUID(0L, 1L)), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");
        callbackServlet.assertListenerStatus();

        // verify that number of invoices and payments for account is still 0
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).size(), 0);
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.INVOICE_CREATION); // The BCD is updated in that case
        final Subscription subscriptionJson = subscriptionApi.createSubscription(input, (LocalDate) null, (LocalDate) null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(subscriptionJson);
        callbackServlet.assertListenerStatus();

        // verify that number of invoices is now 1
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).size(), 1);

        // verify that number of payments is still 0 (no attempts)
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Change Plan
        final Subscription newInput = new Subscription();
        newInput.setSubscriptionId(subscriptionJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE,
                                           ExtBusEventType.SUBSCRIPTION_CHANGE,
                                           ExtBusEventType.INVOICE_CREATION);
        subscriptionApi.changeSubscriptionPlan(subscriptionJson.getSubscriptionId(),
                                               newInput,
                                               (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               BillingActionPolicy.IMMEDIATE,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Cancel subscription (entitlement IMM, billing EOT)
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL);
        subscriptionApi.cancelSubscriptionPlan(newInput.getSubscriptionId(),
                                               (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               null,
                                               null,
                                               null,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();
    }

    @Test(groups = "slow", description = "Can create an entitlement with an account with autoInvoicingOff -- https://github.com/killbill/killbill/pull/1196")
    public void testCreateChangeAndCancelSubscriptionWithAutoInvoicingOff() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign autoInvoicingOff tag to account
        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION);
        final Tags tags = accountApi.createAccountTags(accountJson.getAccountId(), List.of(new UUID(0L, 2L)), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_INVOICING_OFF");
        callbackServlet.assertListenerStatus();

        // verify that number of invoices and payments for account is still 0
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).size(), 0);
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE, /* BCD Update */
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION); // Note that the BCD isn't set
        final Subscription subscriptionJson = subscriptionApi.createSubscription(input,
        		                                                                 (LocalDate) null,
        		                                                                 (LocalDate) null,
                                                                                 false,
                                                                                 false,
                                                                                 false,
                                                                                 true,
                                                                                 DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                 NULL_PLUGIN_PROPERTIES,
                                                                                 requestOptions);
        assertNotNull(subscriptionJson);
        callbackServlet.assertListenerStatus();

        // verify that number of invoices is still 0
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).size(), 0);

        // verify that number of payments is still 0 (no attempts)
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Change Plan
        final Subscription newInput = new Subscription();
        newInput.setSubscriptionId(subscriptionJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE,
                                           ExtBusEventType.SUBSCRIPTION_CHANGE);
        subscriptionApi.changeSubscriptionPlan(subscriptionJson.getSubscriptionId(),
                                               newInput,
                                               (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               BillingActionPolicy.IMMEDIATE,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Cancel subscription (entitlement and billing IMM since there is no BCD set)
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL);
        subscriptionApi.cancelSubscriptionPlan(newInput.getSubscriptionId(),
                                               (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               null,
                                               null,
                                               null,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();
    }

    @Test(groups = "slow", description = "Can create an entitlement with an account with autoInvoicingDraft")
    public void testCreateChangeAndCancelSubscriptionWithAutoInvoicingDraft() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign AUTO_INVOICING_DRAFT tag to account
        callbackServlet.pushExpectedEvents(ExtBusEventType.TAG_CREATION);
        final Tags tags = accountApi.createAccountTags(accountJson.getAccountId(), List.of(new UUID(0L, 8L)), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_INVOICING_DRAFT");
        callbackServlet.assertListenerStatus();

        // verify that number of invoices and payments for account is still 0
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions).size(), 0);
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ACCOUNT_CHANGE); // The BCD is updated in that case
        final Subscription subscriptionJson = subscriptionApi.createSubscription(input,
        		                                                                 (LocalDate) null,
        		                                                                 (LocalDate) null,
                                                                                 false,
                                                                                 false,
                                                                                 false,
                                                                                 true,
                                                                                 DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                 NULL_PLUGIN_PROPERTIES,
                                                                                 requestOptions);
        assertNotNull(subscriptionJson);
        callbackServlet.assertListenerStatus();

        // verify that number of invoices is 1 (DRAFT). Note that we have to poll because callCompletion will return
        // before the DRAFT invoice is generated (there is no event to synchronize on)
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final Invoices invoicesForAccount = accountApi.getInvoicesForAccount(accountJson.getAccountId(), null, null, null, requestOptions);
                return invoicesForAccount.size() == 1 && invoicesForAccount.get(0).getStatus() == InvoiceStatus.DRAFT;
            }
        });

        // verify that number of payments is still 0 (no attempts)
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Change Plan
        final Subscription newInput = new Subscription();
        newInput.setSubscriptionId(subscriptionJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE,
                                           ExtBusEventType.SUBSCRIPTION_CHANGE);
        subscriptionApi.changeSubscriptionPlan(subscriptionJson.getSubscriptionId(),
                                               newInput,
                                               (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               BillingActionPolicy.IMMEDIATE,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();

        // Avoid test timing issues
        clock.addDays(1);
        callbackServlet.assertListenerStatus();

        // Cancel subscription (entitlement IMM, billing now() since the CTD is NULL)
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL);
        subscriptionApi.cancelSubscriptionPlan(newInput.getSubscriptionId(),
        									   (LocalDate) null,
                                               true,
                                               DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                               null,
                                               null,
                                               null,
                                               NULL_PLUGIN_PROPERTIES,
                                               requestOptions);
        callbackServlet.assertListenerStatus();
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription")
    public void testOverwriteEntitlementBCDOnCreate() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE, ExtBusEventType.ENTITLEMENT_CREATION, ExtBusEventType.SUBSCRIPTION_CREATION, ExtBusEventType.SUBSCRIPTION_CREATION, ExtBusEventType.SUBSCRIPTION_BCD_CHANGE, ExtBusEventType.INVOICE_CREATION);
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setPlanName("shotgun-monthly");
        input.setBillCycleDayLocal(28);
        final Subscription subscription = subscriptionApi.createSubscription(input, (LocalDate) null, (LocalDate) null, true, false, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(subscription.getBillCycleDayLocal().intValue(), 28);
        callbackServlet.assertListenerStatus();


        final AccountTimeline timeline = accountApi.getAccountTimeline(accountJson.getAccountId(), false, AuditLevel.FULL, requestOptions);
        Assert.assertEquals(timeline.getBundles().size(), 1);
        Assert.assertEquals(timeline.getBundles().get(0).getSubscriptions().size(), 1);

        final Subscription subscriptionT = timeline.getBundles().get(0).getSubscriptions().get(0);
        Assert.assertEquals(subscriptionT.getBillCycleDayLocal().intValue(), 28);
        // Note that we are START_ENTITLEMENT, START_BILLING, PHASE
        // TODO : we should add the BCD_CHANGE event in the timeline (does not exist in SubscriptionEventType)
        Assert.assertEquals(subscriptionT.getEvents().size(), 3);
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription when changing plan")
    public void testOverwriteEntitlementBCDOnChange() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();


        final Subscription subscription = createSubscription(accountJson.getAccountId(), "99999", "Shotgun",
                                                                ProductCategory.BASE, BillingPeriod.MONTHLY);

        // 2012-05-25
        // Move outside of trial
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);

        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // 2012-05-28
        clock.addDays(3);

        final Subscription newInput = new Subscription();
        newInput.setAccountId(subscription.getAccountId());
        newInput.setSubscriptionId(subscription.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        // Update BCD
        newInput.setBillCycleDayLocal(28);

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE,  ExtBusEventType.SUBSCRIPTION_CHANGE, ExtBusEventType.SUBSCRIPTION_BCD_CHANGE, ExtBusEventType.INVOICE_CREATION);
        subscriptionApi.changeSubscriptionPlan(subscription.getSubscriptionId(), newInput, new LocalDate(2012, 5, 28), null, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        Subscription refreshedSubscription = subscriptionApi.getSubscription(subscription.getSubscriptionId(), requestOptions);
        Assert.assertNotNull(refreshedSubscription);

        // We charged a full period 2012-05-28 - 2012-06-28 based on the new BCD
        verifyChargedThroughDate(refreshedSubscription.getSubscriptionId(), new LocalDate(2012, 6, 28));
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription")
    public void testMoveEntitlementBCD() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term);

        Assert.assertEquals(entitlementJson.getBillCycleDayLocal(), new Integer(25));

        final Subscription updatedSubscription = new Subscription();
        updatedSubscription.setSubscriptionId(entitlementJson.getSubscriptionId());
        updatedSubscription.setBillCycleDayLocal(9);
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_BCD_CHANGE);
        subscriptionApi.updateSubscriptionBCD(entitlementJson.getSubscriptionId(), updatedSubscription, null, requestOptions);
        callbackServlet.assertListenerStatus();

        final Subscription result = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        // Still shows as the 25 (BCD did not take effect)
        Assert.assertEquals(result.getBillCycleDayLocal(), new Integer(25));

        // 2012, 5, 9
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_BCD_CHANGE);
        clock.addDays(14);
        callbackServlet.assertListenerStatus();

        final Subscription result2 = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result2.getBillCycleDayLocal(), new Integer(9));
    }


    @Test(groups = "slow", description = "Verify we can update the quantity associated with the subscription")
    public void testUpdateEntitlementQuantity() throws Exception {
        final DateTime initialDate = new DateTime(2022, 11, 28, 15, 7, 00, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "22112815", productName,
                                                                ProductCategory.BASE, term);

        Assert.assertEquals(entitlementJson.getBillCycleDayLocal(), Integer.valueOf(28));

        final Subscription updatedSubscription = new Subscription();
        updatedSubscription.setSubscriptionId(entitlementJson.getSubscriptionId());
        updatedSubscription.setQuantity(3);
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_QUANTITY);
        subscriptionApi.updateSubscriptionQuantity(entitlementJson.getSubscriptionId(), updatedSubscription, null, requestOptions);
        callbackServlet.assertListenerStatus();

        final Subscription result = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(result.getQuantity(), Integer.valueOf(3));
    }

    @Test(groups = "slow", description = "Can create subscription and change plan using planName")
    public void testEntitlementUsingPlanName() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setBundleExternalKey("somethingSpecial");
        input.setPlanName("shotgun-monthly");

        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
        		                                                               (LocalDate) null,
                                                                               (LocalDate) null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);
        Assert.assertEquals(entitlementJson.getProductName(), "Shotgun");
        Assert.assertEquals(entitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(entitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(entitlementJson.getPlanName(), "shotgun-monthly");

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newInput, (LocalDate) null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        final Subscription newEntitlementJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(newEntitlementJson.getProductName(), "Pistol");
        Assert.assertEquals(newEntitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(newEntitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(newEntitlementJson.getPlanName(), "pistol-monthly");
    }

    @Test(groups = "slow", description = "Can changePlan and undo changePlan on a subscription")
    public void testEntitlementUndoChangePlan() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term);

        // Change plan in the future
        final String newProductName = "Assault-Rifle";

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setProductName(newProductName);
        newInput.setProductCategory(ProductCategory.BASE);
        newInput.setBillingPeriod(entitlementJson.getBillingPeriod());
        newInput.setPriceList(entitlementJson.getPriceList());

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE);
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newInput, new LocalDate(2012, 4, 28), null, NULL_PLUGIN_PROPERTIES, requestOptions);
        Subscription refreshedSubscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(refreshedSubscription);

        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(1));
        clock.addDeltaFromReality(it.toDurationMillis());

        // We get 2 SUBSCRIPTION_CHANGE events, one for requested and one or effective, which are the same.
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE, ExtBusEventType.SUBSCRIPTION_CHANGE);
        subscriptionApi.undoChangeSubscriptionPlan(refreshedSubscription.getSubscriptionId(), NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // Retrieves to check EndDate
        refreshedSubscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(refreshedSubscription.getPrices().size(), 2);
        Assert.assertEquals(refreshedSubscription.getPrices().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(refreshedSubscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(refreshedSubscription.getPrices().get(0).getRecurringPrice());
        Assert.assertEquals(refreshedSubscription.getPrices().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(refreshedSubscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(refreshedSubscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("249.95"));

    }


    @Test(groups = "slow", description = "See https://github.com/killbill/killbill/issues/1397")
    public void testSubscriptionEventsWithCatalogChange() throws Exception {

        // effDt = 2013-02-08T00:00:00+00:00
        callbackServlet.pushExpectedEvents(ExtBusEventType.TENANT_CONFIG_CHANGE);
        String catalog1 = uploadTenantCatalog("org/killbill/billing/server/SpyCarBasic.xml", true);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(catalog1);

        // effDt = 2014-02-08T00:00:00+00:00
        callbackServlet.pushExpectedEvents(ExtBusEventType.TENANT_CONFIG_CHANGE);
        String catalog2 = uploadTenantCatalog("org/killbill/billing/server/SpyCarBasic.v2.xml", true);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(catalog2);


        final DateTime initialDate = new DateTime(2014, 1, 2, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setPlanName("super-monthly");

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.ACCOUNT_CHANGE,  // The BCD is updated in that case
                                           ExtBusEventType.INVOICE_CREATION); // $0 Fixed price
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
        		                                                                (LocalDate) null,
        		                                                                (LocalDate) null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);
        Assert.assertEquals(entitlementJson.getPlanName(), "super-monthly");
        callbackServlet.assertListenerStatus();

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30); // 2014-02-01
        callbackServlet.assertListenerStatus();

        // Go to catalog V2
        clock.addDays(8); // 2014-02-09

        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_CHANGE, ExtBusEventType.SUBSCRIPTION_CHANGE);
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), input, (LocalDate) null, BillingActionPolicy.IMMEDIATE, NULL_PLUGIN_PROPERTIES, requestOptions);
        Subscription refreshedSubscription1 = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(refreshedSubscription1);

        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getPrices().size(), 3);
        Assert.assertEquals(subscription.getPrices().get(0).getPhaseName(), "super-monthly-trial");
        Assert.assertEquals(subscription.getPrices().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(subscription.getPrices().get(0).getRecurringPrice());

        Assert.assertEquals(subscription.getPrices().get(1).getPhaseName(), "super-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(1).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(1).getRecurringPrice(), new BigDecimal("1000.00"));

        // Should reflect the catalog change price
        Assert.assertEquals(subscription.getPrices().get(2).getPhaseName(), "super-monthly-evergreen");
        Assert.assertNull(subscription.getPrices().get(2).getFixedPrice());
        Assert.assertEquals(subscription.getPrices().get(2).getRecurringPrice(), new BigDecimal("1200.00"));
    }
    
    @Test(groups = "slow")
    public void testCreateSubscriptionWithDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final LocalDate creationDate = initialDate.plusDays(5);

        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                creationDate,
                                                                                creationDate,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.PENDING); // Still PENDING since creationDate is not reached
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), creationDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), creationDate);

        clock.setDay(creationDate);

        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //now ACTIVE

    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithDateTime() throws Exception {
        final DateTime initialDateTime = new DateTime(2012, 4, 25, 10, 30);
        clock.setTime(initialDateTime);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DateTime futureDateTime = new DateTime(2012, 4, 30, 11, 30);

        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                futureDateTime,
                                                                                futureDateTime,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.PENDING); // Still PENDING since creationDate is not reached
        Assert.assertEquals(entitlementJson.getBillingStartDate().compareTo(futureDateTime), 0);
        Assert.assertEquals(entitlementJson.getStartDate().compareTo(futureDateTime), 0);

        clock.setTime(futureDateTime);

        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //now ACTIVE

    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithDateTimeDifferentDatesForBillingAndEntitlement() throws Exception {
        final DateTime initialDateTime = new DateTime(2012, 4, 25, 10, 30);
        clock.setTime(initialDateTime);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final DateTime billingDateTime = new DateTime(2012, 4, 28, 11, 30);
        final DateTime entitlementDateTime = new DateTime(2012, 4, 30, 8, 45);

        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                entitlementDateTime,
                                                                                billingDateTime,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.PENDING); // Still PENDING since entitlementDateTime is not reached
        Assert.assertEquals(entitlementJson.getBillingStartDate().compareTo(billingDateTime), 0);
        Assert.assertEquals(entitlementJson.getStartDate().compareTo(entitlementDateTime), 0);

        clock.setTime(billingDateTime);

        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.PENDING); // Still PENDING since entitlementDateTime is not reached

        clock.setTime(entitlementDateTime);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //now ACTIVE

    }

    @Test(groups = "slow")
    public void testCancelSubscriptionWithDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Pistol");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final LocalDate cancelDate = clock.getUTCToday().plusDays(5);

        subscriptionApi.cancelSubscriptionPlan(entitlementJson.getSubscriptionId(), cancelDate, null, null, NULL_PLUGIN_PROPERTIES, requestOptions); // useRequestedDateForBilling defaults to false, so billing will be cancelled as per the default billing policy in the catalog
        //subscriptionApi.cancelSubscriptionPlan(entitlementJson.getSubscriptionId(), cancelDate, false, null, null, null, true, NULL_PLUGIN_PROPERTIES, requestOptions); // use this to set useRequestedDateForBilling=true

        // Retrieves with GET
        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //Still active since cancelDate is not yet reached

        clock.setDay(cancelDate);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);

        Assert.assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        Assert.assertEquals(internalCallContext.toLocalDate(subscription.getCancelledDate()), cancelDate);
        Assert.assertEquals(internalCallContext.toLocalDate(subscription.getBillingEndDate()), initialDate.plusMonths(1));  //since default billing policy is EOT, billing end date is 1 month from start date
    }

    @Test(groups = "slow")
    public void testCancelSubscriptionWithDateTime() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Pistol");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final DateTime cancelDateTime = new DateTime(2012, 4, 30, 11, 30);

        subscriptionApi.cancelSubscriptionPlan(entitlementJson.getSubscriptionId(), cancelDateTime, null, null, NULL_PLUGIN_PROPERTIES, requestOptions); // Unlike cancelWithDate, in this case, the cancelDateTime is used for both entitlement and billing

        // Retrieves with GET
        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //Still active since cancelDateTime is not yet reached

        clock.setTime(cancelDateTime);

        // Retrieves with GET
        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);

        Assert.assertEquals(subscription.getState(), EntitlementState.CANCELLED);
        Assert.assertEquals(subscription.getCancelledDate().compareTo(cancelDateTime), 0);
        Assert.assertEquals(subscription.getBillingEndDate().compareTo(cancelDateTime), 0);
    }

    @Test(groups = "slow")
    public void testChangeSubscriptionPlanWithDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());

        final LocalDate changeDate = clock.getUTCToday().plusDays(5);

        final Subscription newSubscriptionPlan = new Subscription();
        newSubscriptionPlan.setSubscriptionId(entitlementJson.getSubscriptionId());
        newSubscriptionPlan.setPlanName("pistol-monthly");

        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newSubscriptionPlan, changeDate, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves with GET
        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName()); //still old plan since changeDate is not reached

        clock.setDay(changeDate);
        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(newSubscriptionPlan.getPlanName(), subscription.getPlanName()); //now on new plan
        
        final List<EventSubscription> events = subscription.getEvents();
        assertNotNull(events);
        assertEquals(events.get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getEventType(), SubscriptionEventType.CHANGE);
        assertEquals(internalCallContext.toLocalDate(events.get(2).getEffectiveDate()), changeDate);  
        
    }

    @Test(groups = "slow")
    public void testChangeSubscriptionPlanWithDateTime() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());

        final DateTime changeDateTime = new DateTime(2012, 4, 25, 10, 50);

        final Subscription newSubscriptionPlan = new Subscription();
        newSubscriptionPlan.setSubscriptionId(entitlementJson.getSubscriptionId());
        newSubscriptionPlan.setPlanName("pistol-monthly");

        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newSubscriptionPlan, changeDateTime, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves with GET
        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName()); //still old plan since changeDate is not reached

        clock.setTime(changeDateTime);
        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(newSubscriptionPlan.getPlanName(), subscription.getPlanName()); //now on new plan
        
        final List<EventSubscription> events = subscription.getEvents();
        assertNotNull(events);
        assertEquals(events.get(0).getEventType(), SubscriptionEventType.START_ENTITLEMENT);
        assertEquals(events.get(1).getEventType(), SubscriptionEventType.START_BILLING);
        assertEquals(events.get(2).getEventType(), SubscriptionEventType.CHANGE);
        assertEquals(events.get(2).getEffectiveDate().compareTo(changeDateTime), 0);  
    }

    @Test(groups = "slow")
    public void testBlockBundleWithDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final LocalDate blockDate = clock.getUTCToday().plusDays(5);

        BlockingState state = new BlockingState();
        state.setIsBlockBilling(true);
        state.setIsBlockChange(true);
        state.setIsBlockEntitlement(true);
        state.setService("service1");
        state.setStateName("STATE1");
        bundleApi.addBundleBlockingState(entitlementJson.getBundleId(), state, blockDate, NULL_PLUGIN_PROPERTIES, requestOptions);

        //Retrieve account blocking states
        final BlockingStates blockingStates = accountApi.getBlockingStates(accountJson.getAccountId(),
                                                                           List.of(BlockingStateType.SUBSCRIPTION_BUNDLE),
                                                                           null,
                                                                           AuditLevel.FULL,
                                                                           requestOptions);
        assertNotNull(blockingStates);
        Assert.assertEquals(blockingStates.size(), 1);

        state = blockingStates.iterator().next();
        Assert.assertEquals(state.getStateName(), "STATE1");
        Assert.assertEquals(internalCallContext.toLocalDate(state.getEffectiveDate()), blockDate);

        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //state still ACTIVE since blockDate is not reached

        clock.addDays(5);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.BLOCKED); //now blocked

    }
    
 

    @Test(groups = "slow")
    public void testBlockBundleWithDateTime() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final DateTime blockDateTime = new DateTime(2012, 4, 30, 11, 45);

        BlockingState state = new BlockingState();
        state.setIsBlockBilling(true);
        state.setIsBlockChange(true);
        state.setIsBlockEntitlement(true);
        state.setService("service1");
        state.setStateName("STATE1");
        bundleApi.addBundleBlockingState(entitlementJson.getBundleId(), state, blockDateTime, NULL_PLUGIN_PROPERTIES, requestOptions);

        //Retrieve account blocking states
        final BlockingStates blockingStates = accountApi.getBlockingStates(accountJson.getAccountId(),
                                                                           List.of(BlockingStateType.SUBSCRIPTION_BUNDLE),
                                                                           null,
                                                                           AuditLevel.FULL,
                                                                           requestOptions);
        assertNotNull(blockingStates);
        Assert.assertEquals(blockingStates.size(), 1);

        state = blockingStates.iterator().next();
        Assert.assertEquals(state.getStateName(), "STATE1");
        Assert.assertEquals(state.getEffectiveDate().compareTo(blockDateTime), 0);

        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //state still ACTIVE since blockDateTime is not reached

        clock.setTime(blockDateTime);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.BLOCKED); //now blocked

    }
    
    @Test(groups = "slow")
    public void testBlockSubscriptionWithDate() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final LocalDate blockDate = clock.getUTCToday().plusDays(5);

        BlockingState state = new BlockingState();
        state.setIsBlockBilling(true);
        state.setIsBlockChange(true);
        state.setIsBlockEntitlement(true);
        state.setService("service1");
        state.setStateName("STATE1");
        subscriptionApi.addSubscriptionBlockingState(entitlementJson.getSubscriptionId(), state, blockDate, NULL_PLUGIN_PROPERTIES, requestOptions);

        //Retrieve account blocking states
        final BlockingStates blockingStates = accountApi.getBlockingStates(accountJson.getAccountId(),
                                                                           List.of(BlockingStateType.SUBSCRIPTION),
                                                                           null,
                                                                           AuditLevel.FULL,
                                                                           requestOptions);
        assertNotNull(blockingStates);
        Assert.assertEquals(blockingStates.size(), 2);
        
        Iterator<BlockingState> itr = blockingStates.iterator();

        state = itr.next();
        Assert.assertEquals(state.getStateName(), "ENT_STARTED");
        Assert.assertEquals(internalCallContext.toLocalDate(state.getEffectiveDate()), initialDate);
        
        state = itr.next();
        Assert.assertEquals(state.getStateName(), "STATE1");
        Assert.assertEquals(internalCallContext.toLocalDate(state.getEffectiveDate()), blockDate);        

        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //state still ACTIVE since blockDate is not reached

        clock.addDays(5);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.BLOCKED); //now blocked

    }    
    
    @Test(groups = "slow")
    public void testBlockSubscriptionWithDateTime() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                initialDate,
                                                                                null,
                                                                                false,
                                                                                false,
                                                                                false,
                                                                                true,
                                                                                DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);

        Assert.assertEquals(entitlementJson.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getBillingStartDate()), initialDate);
        Assert.assertEquals(internalCallContext.toLocalDate(entitlementJson.getStartDate()), initialDate);

        final DateTime blockDateTime = new DateTime(2012, 4, 30, 11, 45);

        BlockingState state = new BlockingState();
        state.setIsBlockBilling(true);
        state.setIsBlockChange(true);
        state.setIsBlockEntitlement(true);
        state.setService("service1");
        state.setStateName("STATE1");
        subscriptionApi.addSubscriptionBlockingState(entitlementJson.getSubscriptionId(), state, blockDateTime, NULL_PLUGIN_PROPERTIES, requestOptions);

        //Retrieve account blocking states
        final BlockingStates blockingStates = accountApi.getBlockingStates(accountJson.getAccountId(),
                                                                           List.of(BlockingStateType.SUBSCRIPTION),
                                                                           null,
                                                                           AuditLevel.FULL,
                                                                           requestOptions);
        assertNotNull(blockingStates);
        Assert.assertEquals(blockingStates.size(), 2);
        
        Iterator<BlockingState> itr = blockingStates.iterator();

        state = itr.next();
        Assert.assertEquals(state.getStateName(), "ENT_STARTED");
        Assert.assertEquals(internalCallContext.toLocalDate(state.getEffectiveDate()), initialDate);
        
        state = itr.next();
        Assert.assertEquals(state.getStateName(), "STATE1");
        Assert.assertEquals(state.getEffectiveDate().compareTo(blockDateTime), 0);        

        Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE); //state still ACTIVE since blockDate is not reached

        clock.setTime(blockDateTime);

        subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.BLOCKED); //now blocked

    }        

    private void verifyChargedThroughDate(final UUID subscriptionId, final LocalDate ctd) {
        // The call completion may return after the INVOICE event was received and prior the CTD was updated as it
        // done outside and after the transaction: See https://github.com/killbill/killbill/blob/killbill-0.22.27/invoice/src/main/java/org/killbill/billing/invoice/api/user/DefaultInvoiceUserApi.java#L686
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                final Subscription refreshedSubscription = subscriptionApi.getSubscription(subscriptionId, requestOptions);
                return refreshedSubscription != null && refreshedSubscription.getChargedThroughDate().compareTo(ctd) == 0;
            }
        });
    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithEmptyOverrides() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setPlanName("trebuchet-usage-in-arrear");

        final PhasePrice recurringOverride = new PhasePrice();
        recurringOverride.setPlanName("trebuchet-usage-in-arrear");
        recurringOverride.setPhaseType("EVERGREEN");

        input.setPriceOverrides(List.of(recurringOverride));

        try {
            callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                               ExtBusEventType.ENTITLEMENT_CREATION,
                                               ExtBusEventType.SUBSCRIPTION_CREATION,
                                               ExtBusEventType.SUBSCRIPTION_CREATION);
            final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                    null,
                                                                                    (LocalDate) null,
                                                                                    NULL_PLUGIN_PROPERTIES,
                                                                                    requestOptions);
            callbackServlet.assertListenerStatus();
            Assert.fail();
        } catch (final KillBillClientException e) {
            Assert.assertEquals(e.getBillingException().getMessage(), "At least one fixed price, one recurring price or one usage price must be overridden");
        }

    }

    @Test(groups = "slow")
    public void testCreateSubscriptionWithOnlyUsageOverrides() throws Exception {
        final LocalDate initialDate = new LocalDate(2012, 4, 25);
        clock.setDay(initialDate);

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setPlanName("trebuchet-usage-in-arrear");

        final PhasePrice recurringOverride = new PhasePrice();
        recurringOverride.setPlanName("trebuchet-usage-in-arrear");
        recurringOverride.setPhaseType("EVERGREEN");

        final UsagePrice usagePrice = new UsagePrice();
        usagePrice.setUsageName("u1");
        usagePrice.setUsageType(UsageType.CAPACITY);

        final List<TierPrice> tierPrices = new ArrayList<>();

        BlockPrice blockPrice = new BlockPrice();
        blockPrice.setPrice(new BigDecimal("120"));
        blockPrice.setMax(new BigDecimal(100));
        blockPrice.setUnitName("stones");
        TierPrice tierPrice = new TierPrice();
        tierPrice.setBlockPrices(List.of(blockPrice));
        tierPrices.add(tierPrice);

        blockPrice = new BlockPrice();
        blockPrice.setPrice(new BigDecimal("1100"));
        blockPrice.setMax(new BigDecimal(-1));
        blockPrice.setUnitName("stones");
        tierPrice = new TierPrice();
        tierPrice.setBlockPrices(List.of(blockPrice));
        tierPrices.add(tierPrice);

        usagePrice.setTierPrices(tierPrices);

        recurringOverride.setUsagePrices(List.of(usagePrice));
        input.setPriceOverrides(List.of(recurringOverride));

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION);

        final Subscription entitlementJson = subscriptionApi.createSubscription(input,
                                                                                null,
                                                                                (LocalDate) null,
                                                                                NULL_PLUGIN_PROPERTIES,
                                                                                requestOptions);
        callbackServlet.assertListenerStatus();
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(subscription.getState(), EntitlementState.ACTIVE);
        Assert.assertEquals(subscription.getPlanName(), "trebuchet-usage-in-arrear-1"); //overridden plan name

    }

}
