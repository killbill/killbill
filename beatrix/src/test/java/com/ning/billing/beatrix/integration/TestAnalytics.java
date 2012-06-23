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

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;
import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;

@Guice(modules = BeatrixModule.class)
public class TestAnalytics extends TestIntegrationBase {
    @BeforeMethod(groups = "slow")
    public void setUpAnalyticsHandler() throws Exception {
        busService.getBus().register(analyticsListener);
    }

    @AfterMethod(groups = "slow")
    public void tearDownAnalyticsHandler() throws Exception {
        busService.getBus().unregister(analyticsListener);
    }

    @Test(groups = "slow")
    public void testAnalyticsEvents() throws Exception {
        // Create an account
        final Account account = verifyAccountCreation();

        // Update some fields
        verifyAccountUpdate(account);

        // Create a bundle
        final SubscriptionBundle bundle = verifyFirstBundle(account);

        // Add a subscription
        final Subscription subscription = verifyFirstSubscription(account, bundle);

        // Upgrade the subscription
        verifyChangePlan(account, bundle, subscription);
    }

    private Account verifyAccountCreation() throws Exception {
        final AccountData accountData = getAccountData(1);

        // Verify BAC is empty
        Assert.assertNull(analyticsUserApi.getAccountByKey(accountData.getExternalKey()));

        // Create an account
        final Account account = createAccountWithPaymentMethod(accountData);
        Assert.assertNotNull(account);

        waitALittle();

        // Verify Analytics got the account creation event
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(account.getExternalKey());
        Assert.assertNotNull(businessAccount);
        // No balance yet
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getKey(), account.getExternalKey());
        // No invoice yet
        Assert.assertNull(businessAccount.getLastInvoiceDate());
        // No payment yet
        Assert.assertNull(businessAccount.getLastPaymentStatus());
        Assert.assertEquals(businessAccount.getName(), account.getName());
        // No invoice balance yet
        Assert.assertEquals(businessAccount.getTotalInvoiceBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        // TODO - payment fields
        //Assert.assertNotNull(businessAccount.getBillingAddressCountry());
        //Assert.assertNotNull(businessAccount.getCreditCardType());
        //Assert.assertNotNull(businessAccount.getPaymentMethod());

        // The account shouldn't have any invoice yet
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).size(), 0);

        return account;
    }

    private void verifyAccountUpdate(final Account account) throws InterruptedException {
        final MutableAccountData mutableAccountData = account.toMutableAccountData();

        mutableAccountData.setName(UUID.randomUUID().toString().substring(0, 20));

        try {
            accountUserApi.updateAccount(account.getId(), mutableAccountData, context);
        } catch (AccountApiException e) {
            Assert.fail("Unable to update account", e);
        }

        waitALittle();

        // Verify Analytics got the account update event
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(mutableAccountData.getExternalKey());
        Assert.assertNotNull(businessAccount);
        // No balance yet
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getKey(), mutableAccountData.getExternalKey());
        // No invoice yet
        Assert.assertNull(businessAccount.getLastInvoiceDate());
        // No payment yet
        Assert.assertNull(businessAccount.getLastPaymentStatus());
        Assert.assertEquals(businessAccount.getName(), mutableAccountData.getName());
        // No invoice balance yet
        Assert.assertEquals(businessAccount.getTotalInvoiceBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        // TODO - payment fields
        //Assert.assertNotNull(businessAccount.getBillingAddressCountry());
        //Assert.assertNotNull(businessAccount.getCreditCardType());
        //Assert.assertNotNull(businessAccount.getPaymentMethod());

        // The account should still not have any invoice
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).size(), 0);
    }

    private SubscriptionBundle verifyFirstBundle(final Account account) throws EntitlementUserApiException, InterruptedException {
        // Add a bundle
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), UUID.randomUUID().toString(), context);
        Assert.assertNotNull(bundle);

        waitALittle();

        // Verify BST is still empty since no subscription has been added yet
        Assert.assertEquals(analyticsUserApi.getTransitionsForBundle(bundle.getKey()).size(), 0);

        // The account should still not have any invoice
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).size(), 0);

        return bundle;
    }

    private Subscription verifyFirstSubscription(final Account account, final SubscriptionBundle bundle) throws EntitlementUserApiException, InterruptedException, CatalogApiException {
        // Add a subscription
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        final PlanPhaseSpecifier phaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final Subscription subscription = entitlementUserApi.createSubscription(bundle.getId(), phaseSpecifier, null, context);

        waitALittle();

        // BST should have one transition
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getKey());
        Assert.assertEquals(transitions.size(), 1);
        final BusinessSubscriptionTransition transition = transitions.get(0);
        Assert.assertEquals(transition.getExternalKey(), bundle.getKey());
        Assert.assertEquals(transition.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(transition.getEvent().getCategory(), phaseSpecifier.getProductCategory());
        Assert.assertEquals(transition.getEvent().getEventType(), BusinessSubscriptionEvent.EventType.ADD);

        // This is the first transition
        Assert.assertNull(transition.getPreviousSubscription());

        Assert.assertEquals(transition.getNextSubscription().getBillingPeriod(), subscription.getCurrentPhase().getBillingPeriod().toString());
        Assert.assertEquals(transition.getNextSubscription().getBundleId(), subscription.getBundleId());
        Assert.assertEquals(transition.getNextSubscription().getCurrency(), account.getCurrency().toString());
        Assert.assertEquals(transition.getNextSubscription().getPhase(), subscription.getCurrentPhase().getPhaseType().toString());
        // Trial: fixed price of zero
        Assert.assertEquals(transition.getNextSubscription().getPrice().doubleValue(), subscription.getCurrentPhase().getFixedPrice().getPrice(account.getCurrency()).doubleValue());
        Assert.assertEquals(transition.getNextSubscription().getPriceList(), subscription.getCurrentPriceList().getName());
        Assert.assertEquals(transition.getNextSubscription().getProductCategory(), subscription.getCurrentPlan().getProduct().getCategory());
        Assert.assertEquals(transition.getNextSubscription().getProductName(), subscription.getCurrentPlan().getProduct().getName());
        Assert.assertEquals(transition.getNextSubscription().getProductType(), subscription.getCurrentPlan().getProduct().getCatalogName());
        Assert.assertEquals(transition.getNextSubscription().getSlug(), subscription.getCurrentPhase().getName());
        Assert.assertEquals(transition.getNextSubscription().getStartDate(), subscription.getStartDate());
        Assert.assertEquals(transition.getNextSubscription().getState(), subscription.getState());
        Assert.assertEquals(transition.getNextSubscription().getSubscriptionId(), subscription.getId());

        // Make sure the account balance is still zero
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(account.getExternalKey());
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getTotalInvoiceBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));

        // The account should have one invoice for the trial phase
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).size(), 1);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getBalance().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getCurrency(), account.getCurrency());

        return subscription;
    }

    private void verifyChangePlan(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws EntitlementUserApiException, InterruptedException {
        final String newProductName = "Assault-Rifle";
        final BillingPeriod newTerm = BillingPeriod.MONTHLY;
        final String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        final DateTime requestedDate = clock.getUTCNow();
        Assert.assertTrue(subscription.changePlan(newProductName, newTerm, newPlanSetName, requestedDate, context));

        waitALittle();

        // BST should have two transitions
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getKey());
        Assert.assertEquals(transitions.size(), 2);
        final BusinessSubscriptionTransition previousTransition = transitions.get(0);
        final BusinessSubscriptionTransition transition = transitions.get(1);
        Assert.assertEquals(transition.getExternalKey(), bundle.getKey());
        Assert.assertEquals(transition.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(transition.getEvent().getCategory(), ProductCategory.BASE);
        Assert.assertEquals(transition.getEvent().getEventType(), BusinessSubscriptionEvent.EventType.CHANGE);

        // Verify the previous subscription matches
        Assert.assertNull(previousTransition.getPreviousSubscription());
        Assert.assertEquals(previousTransition.getNextSubscription(), transition.getPreviousSubscription());

        // Verify the next subscription
        // No billing period for the trial phase
        Assert.assertEquals(transition.getNextSubscription().getBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD.toString());
        Assert.assertEquals(transition.getNextSubscription().getBundleId(), subscription.getBundleId());
        Assert.assertEquals(transition.getNextSubscription().getCurrency(), account.getCurrency().toString());
        Assert.assertEquals(transition.getNextSubscription().getPhase(), PhaseType.TRIAL.toString());
        // We're still in trial
        Assert.assertEquals(transition.getNextSubscription().getPrice().doubleValue(), 0.0);
        Assert.assertEquals(transition.getNextSubscription().getPriceList(), newPlanSetName);
        Assert.assertEquals(transition.getNextSubscription().getProductCategory(), ProductCategory.BASE);
        Assert.assertEquals(transition.getNextSubscription().getProductName(), newProductName);
        Assert.assertEquals(transition.getNextSubscription().getProductType(), subscription.getCurrentPlan().getProduct().getCatalogName());
        Assert.assertEquals(transition.getNextSubscription().getSlug(), subscription.getCurrentPhase().getName());
        Assert.assertEquals(transition.getNextSubscription().getStartDate(), requestedDate);
        Assert.assertEquals(transition.getNextSubscription().getState(), Subscription.SubscriptionState.ACTIVE);
        // It's still the same subscription
        Assert.assertEquals(transition.getNextSubscription().getSubscriptionId(), subscription.getId());

        // The account should have two invoices for the trial phase of both subscriptions
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).size(), 2);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getBalance().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(1).getBalance().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(1).getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(1).getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(1).getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey()).get(1).getCurrency(), account.getCurrency());
    }

    private void waitALittle() throws InterruptedException {
        // We especially need to wait for entitlement events
        Thread.sleep(1000);
    }
}
