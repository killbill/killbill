/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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
import java.util.regex.Pattern;

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
import org.killbill.billing.client.model.BulkSubscriptionsBundles;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.Subscriptions;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.BulkSubscriptionsBundle;
import org.killbill.billing.client.model.gen.Bundle;
import org.killbill.billing.client.model.gen.Invoice;
import org.killbill.billing.client.model.gen.PhasePriceOverride;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
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

    @Test(groups = "slow", description = "Can change plan and cancel a subscription")
    public void testEntitlementInTrialOk() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());

        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Equality in java client is not correctly implemented so manually check PriceOverrides section and then reset before equality
        objFromJson.setPriceOverrides(null);
        entitlementJson.setPriceOverrides(null);
        Assert.assertTrue(objFromJson.equals(entitlementJson));

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
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newInput, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertNotNull(objFromJson);

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // Cancel IMM (Billing EOT)
        subscriptionApi.cancelSubscriptionPlan(newInput.getSubscriptionId(), null, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves to check EndDate
        objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term, true);

        // Retrieves with GET
        Subscription objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(31);
        callbackServlet.assertListenerStatus();

        // Cancel EOT
        subscriptionApi.cancelSubscriptionPlan(entitlementJson.getSubscriptionId(), null, EntitlementActionPolicy.END_OF_TERM,
                                               BillingActionPolicy.END_OF_TERM, NULL_PLUGIN_PROPERTIES, requestOptions);

        // Retrieves to check EndDate
        objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        assertNotNull(objFromJson.getCancelledDate());
        Assert.assertEquals(objFromJson.getPriceOverrides().size(), 2);
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(objFromJson.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(objFromJson.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(objFromJson.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(objFromJson.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

        // Uncancel
        subscriptionApi.uncancelSubscriptionPlan(entitlementJson.getSubscriptionId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        objFromJson = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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

        subscriptionApi.changeSubscriptionPlan(subscriptionId, subscription, null, null, null, requestOptions);

        subscriptionApi.cancelSubscriptionPlan(subscriptionId, null, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);

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
                                                                 ProductCategory.BASE, term, true);

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
        subscriptionApi.changeSubscriptionPlan(subscriptionJson.getSubscriptionId(), newInput, null, BillingActionPolicy.IMMEDIATE, NULL_PLUGIN_PROPERTIES, requestOptions);

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
        input.setExternalKey("identical");
        input.setProductName(productName);
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final List<PhasePriceOverride> overrides = new ArrayList<PhasePriceOverride>();
        overrides.add(new PhasePriceOverride(null, null, PhaseType.TRIAL.toString(), BigDecimal.TEN, null, null));
        input.setPriceOverrides(overrides);

        callbackServlet.pushExpectedEvents(ExtBusEventType.ACCOUNT_CHANGE,
                                           ExtBusEventType.ENTITLEMENT_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.SUBSCRIPTION_CREATION,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        final Subscription subscription = subscriptionApi.createSubscription(input, null, null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();
        Assert.assertEquals(subscription.getPriceOverrides().size(), 2);

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

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, false, AuditLevel.FULL, requestOptions);
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
        subscriptionApi.changeSubscriptionPlan(subscription2.getSubscriptionId(), newInput, null, BillingActionPolicy.IMMEDIATE, NULL_PLUGIN_PROPERTIES, requestOptions);
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

        final Subscriptions subscriptions = new Subscriptions();
        subscriptions.add(base);
        subscriptions.add(addOn1);
        subscriptions.add(addOn2);

        final Bundle bundle = subscriptionApi.createSubscriptionWithAddOns(subscriptions, null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(bundle);
        assertEquals(bundle.getExternalKey(), "base");
        assertEquals(bundle.getSubscriptions().size(), 3);

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, false, AuditLevel.NONE, requestOptions);
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

        final List<Invoice> invoicesAfterClose = accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
        assertEquals(invoicesAfterClose.size(), 1);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceApi.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 1);
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
        final Bundles bundles = subscriptionApi.createSubscriptionsWithAddOns(input, null, null, false, false, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        callbackServlet.assertListenerStatus();

        assertNotNull(bundles);
        assertEquals(bundles.size(), 2);
        assertFalse(bundles.get(0).getExternalKey().equals(bundles.get(1).getExternalKey()));

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions);
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

        callbackServlet.pushExpectedEvents(ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.ENTITLEMENT_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.SUBSCRIPTION_CANCEL,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_FAILED,
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

        final List<Invoice> invoicesAfterClose = accountApi.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(invoicesAfterClose.size(), 2);
        assertEquals(invoicesAfterClose.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
        assertEquals(invoiceApi.getInvoiceTags(invoicesAfterClose.get(0).getInvoiceId(), requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Create a bulk of base entitlements and addOns under the same transaction",
            expectedExceptions = KillBillClientException.class, expectedExceptionsMessageRegExp = "SubscriptionJson productName needs to be set when no planName is specified")
    public void testcreateSubscriptionsWithoutBase() throws Exception {
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

        final BulkSubscriptionsBundle bulkList1 = new BulkSubscriptionsBundle();
        bulkList1.setBaseEntitlementAndAddOns(subscriptions);
        final BulkSubscriptionsBundle bulkList2 = new BulkSubscriptionsBundle();
        bulkList2.setBaseEntitlementAndAddOns(subscriptions);

        final BulkSubscriptionsBundles input = new BulkSubscriptionsBundles();
        input.add(bulkList1);
        input.add(bulkList2);

        subscriptionApi.createSubscriptionsWithAddOns(input, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
    }

    @Test(groups = "slow", description = "Create addOns in a bundle where BP subscription already exist")
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
        final Subscription subscription = subscriptionApi.createSubscription(input, null, null, null, null, null, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);

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

        final Bundles bundles = subscriptionApi.createSubscriptionsWithAddOns(bulkSubscriptionsBundles, null, null, false, false, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(bundles);
        assertEquals(bundles.size(), 1);
        assertEquals(bundles.get(0).getSubscriptions().size(), 3);

        final List<Invoice> invoices = accountApi.getInvoicesForAccount(accountJson.getAccountId(), true, false, false, false, AuditLevel.NONE, requestOptions);
        assertEquals(invoices.size(), 2);
    }

    @Test(groups = "slow", description = "Can create an entitlement in the future")
    public void testcreateSubscriptionInTheFuture() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Shotgun");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription entitlementJson = subscriptionApi.createSubscription(input, initialDate.toLocalDate().plusMonths(1), null, null, NULL_PLUGIN_PROPERTIES, requestOptions);

        Assert.assertEquals(entitlementJson.getProductName(), input.getProductName());
        Assert.assertEquals(entitlementJson.getProductCategory(), input.getProductCategory());
        Assert.assertEquals(entitlementJson.getBillingPeriod(), input.getBillingPeriod());
        Assert.assertEquals(entitlementJson.getPriceList(), input.getPriceList());

        // Retrieves with GET
        final Subscription subscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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
    public void testcreateSubscriptionWithAutoPayOff() throws Exception {
        final Account accountJson = createAccount();
        assertNotNull(accountJson);

        // assign autoPaymentOff tag to account
        Tags tags = accountApi.createAccountTags(accountJson.getAccountId(), ImmutableList.<UUID>of(new UUID(0L, 1L)), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");

        // verify that number of invoices and payments for account is still 0
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 0);
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);

        // create a subscription with no trial plan
        final Subscription input = new Subscription();
        input.setAccountId(accountJson.getAccountId());
        input.setProductName("Blowdart");
        input.setProductCategory(ProductCategory.BASE);
        input.setBillingPeriod(BillingPeriod.MONTHLY);
        input.setPriceList("notrial");

        final Subscription subscriptionJson = subscriptionApi.createSubscription(input, null, null, null, null, null, true, 10L, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(subscriptionJson);

        // verify that number of invoices is now 1
        assertEquals(accountApi.getInvoicesForAccount(accountJson.getAccountId(), requestOptions).size(), 1);

        // verify that number of payments is still 0 (no attempts)
        assertEquals(accountApi.getPaymentsForAccount(accountJson.getAccountId(), NULL_PLUGIN_PROPERTIES, requestOptions).size(), 0);
    }

    @Test(groups = "slow", description = "Verify we can move the BCD associated with the subscription")
    public void testMoveEntitlementBCD() throws Exception {
        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account accountJson = createAccountWithDefaultPaymentMethod();

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;

        final Subscription entitlementJson = createSubscription(accountJson.getAccountId(), "99999", productName,
                                                                ProductCategory.BASE, term, true);

        Assert.assertEquals(entitlementJson.getBillCycleDayLocal(), new Integer(25));

        final Subscription updatedSubscription = new Subscription();
        updatedSubscription.setSubscriptionId(entitlementJson.getSubscriptionId());
        updatedSubscription.setBillCycleDayLocal(9);
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_BCD_CHANGE);
        subscriptionApi.updateSubscriptionBCD(entitlementJson.getSubscriptionId(), updatedSubscription, null, requestOptions);
        callbackServlet.assertListenerStatus();

        final Subscription result = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        // Still shows as the 4 (BCD did not take effect)
        Assert.assertEquals(result.getBillCycleDayLocal(), new Integer(25));

        // 2012, 5, 9
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_BCD_CHANGE);
        clock.addDays(14);
        callbackServlet.assertListenerStatus();

        final Subscription result2 = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
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

        final Subscription entitlementJson = subscriptionApi.createSubscription(input, null, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        Assert.assertEquals(entitlementJson.getProductName(), "Shotgun");
        Assert.assertEquals(entitlementJson.getBillingPeriod(), BillingPeriod.MONTHLY);
        Assert.assertEquals(entitlementJson.getPriceList(), DefaultPriceListSet.DEFAULT_PRICELIST_NAME);
        Assert.assertEquals(entitlementJson.getPlanName(), "shotgun-monthly");

        final Subscription newInput = new Subscription();
        newInput.setAccountId(entitlementJson.getAccountId());
        newInput.setSubscriptionId(entitlementJson.getSubscriptionId());
        newInput.setPlanName("pistol-monthly");
        subscriptionApi.changeSubscriptionPlan(entitlementJson.getSubscriptionId(), newInput, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
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
                                                                ProductCategory.BASE, term, true);

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

        subscriptionApi.undoChangeSubscriptionPlan(refreshedSubscription.getSubscriptionId(), NULL_PLUGIN_PROPERTIES, requestOptions);

        // MOVE AFTER TRIAL
        callbackServlet.pushExpectedEvents(ExtBusEventType.SUBSCRIPTION_PHASE,
                                           ExtBusEventType.INVOICE_CREATION,
                                           ExtBusEventType.INVOICE_PAYMENT_SUCCESS,
                                           ExtBusEventType.PAYMENT_SUCCESS);
        clock.addDays(30);
        callbackServlet.assertListenerStatus();

        // Retrieves to check EndDate
        refreshedSubscription = subscriptionApi.getSubscription(entitlementJson.getSubscriptionId(), requestOptions);
        Assert.assertEquals(refreshedSubscription.getPriceOverrides().size(), 2);
        Assert.assertEquals(refreshedSubscription.getPriceOverrides().get(0).getPhaseName(), "shotgun-monthly-trial");
        Assert.assertEquals(refreshedSubscription.getPriceOverrides().get(0).getFixedPrice(), BigDecimal.ZERO);
        Assert.assertNull(refreshedSubscription.getPriceOverrides().get(0).getRecurringPrice());
        Assert.assertEquals(refreshedSubscription.getPriceOverrides().get(1).getPhaseName(), "shotgun-monthly-evergreen");
        Assert.assertNull(refreshedSubscription.getPriceOverrides().get(1).getFixedPrice());
        Assert.assertEquals(refreshedSubscription.getPriceOverrides().get(1).getRecurringPrice(), new BigDecimal("249.95"));

    }

}
