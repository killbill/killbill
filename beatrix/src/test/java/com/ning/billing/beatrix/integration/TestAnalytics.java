/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.MutableAccountData;
import com.ning.billing.analytics.api.BusinessAccount;
import com.ning.billing.analytics.api.BusinessInvoice;
import com.ning.billing.analytics.api.BusinessInvoice.BusinessInvoiceItem;
import com.ning.billing.analytics.api.BusinessInvoicePayment;
import com.ning.billing.analytics.api.BusinessOverdueStatus;
import com.ning.billing.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.analytics.api.BusinessTag;
import com.ning.billing.analytics.model.BusinessSubscriptionEvent;
import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.api.TagDefinitionApiException;
import com.ning.billing.util.config.catalog.XMLLoader;
import com.ning.billing.util.tag.TagDefinition;

import static org.testng.Assert.assertTrue;

public class TestAnalytics extends TestIntegrationBase {

    private Account account;

    private Plan subscriptionPlan;

    @Override
    @BeforeMethod(groups = "slow", enabled=false)
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final String configXml = "<overdueConfig>" +
                                 "   <bundleOverdueStates>" +
                                 "       <state name=\"OD3\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>50</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD3</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD2\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>40</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD2</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>true</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>5</number>" +
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "       <state name=\"OD1\">" +
                                 "           <condition>" +
                                 "               <timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "                   <unit>DAYS</unit><number>30</number>" +
                                 "               </timeSinceEarliestUnpaidInvoiceEqualsOrExceeds>" +
                                 "           </condition>" +
                                 "           <externalMessage>Reached OD1</externalMessage>" +
                                 "           <blockChanges>true</blockChanges>" +
                                 "           <disableEntitlementAndChangesBlocked>false</disableEntitlementAndChangesBlocked>" +
                                 "           <autoReevaluationInterval>" +
                                 "               <unit>DAYS</unit><number>100</number>" + // this number is intentionally too high
                                 "           </autoReevaluationInterval>" +
                                 "       </state>" +
                                 "   </bundleOverdueStates>" +
                                 "</overdueConfig>";
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        busService.getBus().register(analyticsListener);

        final DateTime initialDate = new DateTime(2012, 8, 1, 0, 15, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        account = verifyAccountCreation(initialDate.plusDays(30).getDayOfMonth());
    }

    @Override
    @AfterMethod(groups = "slow", enabled=false)
    public void afterMethod() throws Exception {
        super.afterMethod();
        busService.getBus().unregister(analyticsListener);
        // Reset the payment plugin for other tests
        paymentPlugin.clear();
    }

    @Test(groups = "slow", enabled=false)
    public void testVerifyRefresh() throws Exception {
        // Create a tag
        final TagDefinition tagDefinition = tagUserApi.create(UUID.randomUUID().toString().substring(0, 10), UUID.randomUUID().toString(), callContext);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, tagDefinition.getId(), callContext);

        // Refresh
        analyticsUserApi.rebuildAnalyticsForAccount(account, callContext);

        // Check the tag
        final List<BusinessTag> tagsForAccount = analyticsUserApi.getTagsForAccount(account.getExternalKey(), callContext);
        Assert.assertEquals(tagsForAccount.size(), 1);
        Assert.assertEquals(tagsForAccount.get(0).getName(), tagDefinition.getName());
    }

    @Test(groups = "slow", enabled=false)
    public void testCreateAndCancelSubscription() throws Exception {

        // Create a bundle
        final SubscriptionBundle bundle = verifyFirstBundle(account);

        // Add a subscription
        Subscription subscription = verifyFirstSubscription(account, bundle);

        // Move after trial
        busHandler.pushExpectedEvent(TestApiListener.NextEvent.PHASE);
        busHandler.pushExpectedEvent(TestApiListener.NextEvent.INVOICE);
        busHandler.pushExpectedEvent(TestApiListener.NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        Assert.assertTrue(busHandler.isCompleted(DELAY));

        // Check BST - nothing should have changed
        verifyBSTWithTrialAndEvergreenPhases(account, bundle, subscription);

        // Cancel end of term - refetch the subscription to have the CTD set
        // (otherwise, cancellation would be immediate)
        subscription = entitlementUserApi.getSubscriptionFromId(subscription.getId(), callContext);
        subscription.cancel(clock.getUTCNow(), callContext);

        waitALittle();

        verifyBSTWithTrialAndEvergreenPhasesAndCancellation(account, bundle, subscription);

        // Move after cancel date
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 100);
        assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();

        // Check BST received the system cancel event
        verifyBSTWithTrialAndEvergreenPhasesAndCancellationAndSystemCancellation(account, bundle, subscription);
    }

    @Test(groups = "slow", enabled=false)
    public void testCreateAndUpdateSubscription() throws Exception {

        // Update some fields
        verifyAccountUpdate(account);

        // Add a tag
        verifyAddTagToAccount(account);

        // Create a bundle
        final SubscriptionBundle bundle = verifyFirstBundle(account);

        // Add a subscription
        final Subscription subscription = verifyFirstSubscription(account, bundle);

        // Upgrade the subscription
        verifyChangePlan(account, bundle, subscription);
    }

    @Test(groups = "slow", enabled=false)
    public void testOverdue() throws Exception {

        paymentPlugin.makeAllInvoicesFailWithError(true);

        // Create a bundle
        final SubscriptionBundle bundle = verifyFirstBundle(account);

        // Add a subscription
        busHandler.pushExpectedEvents(TestApiListener.NextEvent.CREATE, TestApiListener.NextEvent.INVOICE);
        final Subscription subscription = verifyFirstSubscription(account, bundle);
        assertTrue(busHandler.isCompleted(DELAY));

        // Verify the initial state of payments
        Assert.assertEquals(analyticsUserApi.getInvoicePaymentsForAccount(account.getExternalKey(), callContext).size(), 0);

        // Verify the account payment fields
        Assert.assertEquals(analyticsUserApi.getAccountByKey(account.getExternalKey(), callContext).getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertNull(analyticsUserApi.getAccountByKey(account.getExternalKey(), callContext).getLastPaymentStatus());

        // Verify the initial overdue status
        Assert.assertEquals(analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext).size(), 0);

        // Move after trial
        busHandler.pushExpectedEvents(TestApiListener.NextEvent.PHASE, TestApiListener.NextEvent.INVOICE, TestApiListener.NextEvent.PAYMENT_ERROR);
        clock.addDays(30); // DAY 30 have to get out of trial before first payment
        Assert.assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();

        // Check BST - nothing should have changed
        verifyBSTWithTrialAndEvergreenPhases(account, bundle, subscription);

        // Verify the payments - we should have received one
        final List<BusinessInvoicePayment> invoicePaymentsForAccount = analyticsUserApi.getInvoicePaymentsForAccount(account.getExternalKey(), callContext);
        Assert.assertEquals(invoicePaymentsForAccount.size(), 1);
        Assert.assertEquals(invoicePaymentsForAccount.get(0).getAccountKey(), account.getExternalKey());
        Assert.assertTrue(invoicePaymentsForAccount.get(0).getAmount().compareTo(BigDecimal.ZERO) > 0);
        Assert.assertTrue(invoicePaymentsForAccount.get(0).getRequestedAmount().compareTo(BigDecimal.ZERO) > 0);
        Assert.assertNull(invoicePaymentsForAccount.get(0).getExtFirstPaymentRefId());
        Assert.assertNull(invoicePaymentsForAccount.get(0).getExtSecondPaymentRefId());
        Assert.assertEquals(invoicePaymentsForAccount.get(0).getProcessingStatus(), PaymentStatus.PAYMENT_FAILURE.toString());
        Assert.assertEquals(invoicePaymentsForAccount.get(0).getPluginName(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME);

        // Verify the account object has been updated
        Assert.assertEquals(analyticsUserApi.getAccountByKey(account.getExternalKey(), callContext).getBalance(),
                            invoicePaymentsForAccount.get(0).getAmount());

        // Verify the invoice balance isn't zero and is equal to the payment amount (don't look at the first, trial, invoice)
        Assert.assertTrue(analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext).get(1).getBalance().compareTo(BigDecimal.ZERO) > 0);
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext).get(1).getBalance(),
                            invoicePaymentsForAccount.get(0).getAmount());

        // Verify overdue status - we should still be in clear state
        Assert.assertEquals(analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext).size(), 0);

        clock.addDays(15); // DAY 45 - 15 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));

        // Check BST - nothing should have changed
        verifyBSTWithTrialAndEvergreenPhases(account, bundle, subscription);

        // Verify overdue status - we should still be in clear state
        Assert.assertEquals(analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext).size(), 0);

        busHandler.pushExpectedEvents(TestApiListener.NextEvent.INVOICE, TestApiListener.NextEvent.PAYMENT_ERROR);
        clock.addDays(20); // DAY 65 - 35 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();

        // Verify overdue status - we should be in OD1
        final List<BusinessOverdueStatus> od1Bundle = analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(od1Bundle.size(), 1);
        Assert.assertEquals(od1Bundle.get(0).getStatus(), "OD1");
        Assert.assertEquals(od1Bundle.get(0).getId(), bundle.getId());
        Assert.assertEquals(od1Bundle.get(0).getAccountKey(), account.getExternalKey());

        clock.addDays(2); // DAY 67 - 37 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();
        // Verify overdue status - we should still be in OD1
        final List<BusinessOverdueStatus> stillOd1Bundle = analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(stillOd1Bundle.size(), 1);
        Assert.assertEquals(stillOd1Bundle.get(0).getStatus(), "OD1");
        Assert.assertEquals(stillOd1Bundle.get(0).getId(), bundle.getId());
        Assert.assertEquals(stillOd1Bundle.get(0).getAccountKey(), account.getExternalKey());

        clock.addDays(8); // DAY 75 - 45 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();
        // Verify overdue status - we should be in OD2
        final List<BusinessOverdueStatus> od2Bundle = analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(od2Bundle.size(), 2);
        Assert.assertEquals(od2Bundle.get(0).getStatus(), "OD1");
        Assert.assertEquals(od2Bundle.get(1).getStatus(), "OD2");
        Assert.assertEquals(od2Bundle.get(0).getEndDate(),
                            od2Bundle.get(1).getStartDate());
        Assert.assertEquals(od2Bundle.get(0).getId(), bundle.getId());
        Assert.assertEquals(od2Bundle.get(0).getAccountKey(), account.getExternalKey());
        Assert.assertEquals(od2Bundle.get(1).getId(), bundle.getId());
        Assert.assertEquals(od2Bundle.get(1).getAccountKey(), account.getExternalKey());

        clock.addDays(10); // DAY 85 - 55 days after invoice
        assertTrue(busHandler.isCompleted(DELAY));
        waitALittle();
        // Verify overdue status - we should be in OD3
        final List<BusinessOverdueStatus> od3Bundle = analyticsUserApi.getOverdueStatusesForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(od3Bundle.size(), 3);
        Assert.assertEquals(od3Bundle.get(0).getStatus(), "OD1");
        Assert.assertEquals(od3Bundle.get(1).getStatus(), "OD2");
        Assert.assertEquals(od3Bundle.get(2).getStatus(), "OD3");
        Assert.assertEquals(od3Bundle.get(0).getEndDate(),
                            od3Bundle.get(1).getStartDate());
        Assert.assertEquals(od3Bundle.get(1).getEndDate(),
                            od3Bundle.get(2).getStartDate());
        Assert.assertEquals(od3Bundle.get(0).getId(), bundle.getId());
        Assert.assertEquals(od3Bundle.get(0).getAccountKey(), account.getExternalKey());
        Assert.assertEquals(od3Bundle.get(1).getId(), bundle.getId());
        Assert.assertEquals(od3Bundle.get(1).getAccountKey(), account.getExternalKey());
        Assert.assertEquals(od3Bundle.get(2).getId(), bundle.getId());
        Assert.assertEquals(od3Bundle.get(2).getAccountKey(), account.getExternalKey());
    }

    private Account verifyAccountCreation(final int billCycleDay) throws Exception {

        final AccountData accountData = getAccountData(billCycleDay);

        // Verify BAC is empty
        Assert.assertNull(analyticsUserApi.getAccountByKey(accountData.getExternalKey(), callContext));

        // Create an account
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        Assert.assertNotNull(account);

        waitALittle();

        // Verify Analytics got the account creation event
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(account.getExternalKey(), callContext);
        Assert.assertNotNull(businessAccount);
        // No balance yet
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getExternalKey(), account.getExternalKey());
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
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext).size(), 0);

        return account;
    }

    private void verifyAccountUpdate(final Account account) throws InterruptedException {
        final MutableAccountData mutableAccountData = account.toMutableAccountData();

        mutableAccountData.setName(UUID.randomUUID().toString().substring(0, 20));

        try {
            accountUserApi.updateAccount(account.getId(), mutableAccountData, callContext);
        } catch (AccountApiException e) {
            Assert.fail("Unable to update account", e);
        }

        waitALittle();

        // Verify Analytics got the account update event
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(mutableAccountData.getExternalKey(), callContext);
        Assert.assertNotNull(businessAccount);
        // No balance yet
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getExternalKey(), mutableAccountData.getExternalKey());
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
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext).size(), 0);
    }

    private void verifyAddTagToAccount(final Account account) throws TagDefinitionApiException, TagApiException, InterruptedException {
        Assert.assertEquals(analyticsUserApi.getTagsForAccount(account.getExternalKey(), callContext).size(), 0);

        final TagDefinition tagDefinition = tagUserApi.create(UUID.randomUUID().toString().substring(0, 10), UUID.randomUUID().toString(), callContext);
        tagUserApi.addTag(account.getId(), ObjectType.ACCOUNT, tagDefinition.getId(), callContext);

        waitALittle();

        final List<BusinessTag> tagsForAccount = analyticsUserApi.getTagsForAccount(account.getExternalKey(), callContext);
        Assert.assertEquals(tagsForAccount.size(), 1);
        Assert.assertEquals(tagsForAccount.get(0).getName(), tagDefinition.getName());
    }

    private SubscriptionBundle verifyFirstBundle(final Account account) throws EntitlementUserApiException, InterruptedException {
        // Add a bundle
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), UUID.randomUUID().toString(), callContext);
        Assert.assertNotNull(bundle);

        waitALittle();

        // Verify BST is still empty since no subscription has been added yet
        Assert.assertEquals(analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext).size(), 0);

        // The account should still not have any invoice
        Assert.assertEquals(analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext).size(), 0);

        return bundle;
    }

    private Subscription verifyFirstSubscription(final Account account, final SubscriptionBundle bundle) throws EntitlementUserApiException, InterruptedException, CatalogApiException {
        // Add a subscription
        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        final PlanPhaseSpecifier phaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final Subscription subscription = entitlementUserApi.createSubscription(bundle.getId(), phaseSpecifier, null, callContext);
        subscriptionPlan = subscription.getCurrentPlan();

        waitALittle();

        // Verify BST
        verifyBSTWithTrialAndEvergreenPhases(account, bundle, subscription);

        // Make sure the account balance is still zero
        final BusinessAccount businessAccount = analyticsUserApi.getAccountByKey(account.getExternalKey(), callContext);
        Assert.assertEquals(businessAccount.getBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));
        Assert.assertEquals(businessAccount.getTotalInvoiceBalance().doubleValue(), Rounder.round(BigDecimal.ZERO));

        // The account should have one invoice for the trial phase
        final List<BusinessInvoice> invoices = analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext);
        Assert.assertEquals(invoices.size(), 1);
        final BusinessInvoice invoice = invoices.get(0);
        Assert.assertEquals(invoice.getBalance().doubleValue(), 0.0);
        Assert.assertEquals(invoice.getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(invoice.getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(invoice.getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(invoice.getCurrency(), account.getCurrency());

        // The invoice should have a single item associated to it
        final List<BusinessInvoiceItem> invoiceItems = invoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 1);
        final BusinessInvoiceItem invoiceItem = invoiceItems.get(0);
        Assert.assertEquals(invoiceItem.getAmount().doubleValue(), 0.0);
        // No billing period for the trial item
        Assert.assertEquals(invoiceItem.getBillingPeriod(), subscription.getCurrentPhase().getBillingPeriod().toString());
        Assert.assertEquals(invoiceItem.getCurrency(), account.getCurrency());
        Assert.assertEquals(invoiceItem.getEndDate(), invoiceItem.getStartDate().plusDays(30));
        Assert.assertEquals(invoiceItem.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(invoiceItem.getInvoiceId(), invoice.getInvoiceId());
        Assert.assertEquals(invoiceItem.getItemType(), "FIXED");
        Assert.assertEquals(invoiceItem.getPhase(), subscription.getCurrentPhase().getPhaseType().toString());
        Assert.assertEquals(invoiceItem.getProductCategory(), subscription.getCurrentPlan().getProduct().getCategory().toString());
        Assert.assertEquals(invoiceItem.getProductName(), subscription.getCurrentPlan().getProduct().getName());
        Assert.assertEquals(invoiceItem.getProductType(), subscription.getCurrentPlan().getProduct().getCatalogName());
        Assert.assertEquals(invoiceItem.getSlug(), subscription.getCurrentPhase().getName());
        final DateTime subscriptionStartDate = subscription.getStartDate();
        Assert.assertEquals(invoiceItem.getStartDate(), new LocalDate(subscriptionStartDate.getYear(), subscriptionStartDate.getMonthOfYear(), subscriptionStartDate.getDayOfMonth()));

        return subscription;
    }

    private void verifyBSTWithTrialAndEvergreenPhases(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws CatalogApiException {
        // BST should have two transitions
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(transitions.size(), 2);

        verifyTrialAndEvergreenPhases(account, bundle, subscription);
    }

    private void verifyBSTWithTrialAndEvergreenPhasesAndCancellation(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws CatalogApiException {
        // BST should have three transitions
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(transitions.size(), 4);

        verifyTrialAndEvergreenPhases(account, bundle, subscription);
        verifyCancellationTransition(account, bundle);
    }

    private void verifyBSTWithTrialAndEvergreenPhasesAndCancellationAndSystemCancellation(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws CatalogApiException {
        // BST should have four transitions
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(transitions.size(), 4);

        verifyTrialAndEvergreenPhases(account, bundle, subscription);
        verifyCancellationTransition(account, bundle);
        verifySystemCancellationTransition(account, bundle);
    }

    private void verifyTrialAndEvergreenPhases(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws CatalogApiException {
        final Product currentProduct = subscriptionPlan.getProduct();
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);

        // Check the first transition (into trial phase)
        final BusinessSubscriptionTransition initialTransition = transitions.get(0);
        Assert.assertEquals(initialTransition.getBundleId(), bundle.getId());
        Assert.assertEquals(initialTransition.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(initialTransition.getAccountId(), account.getId());
        Assert.assertEquals(initialTransition.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(initialTransition.getSubscriptionId(), subscription.getId());
        Assert.assertEquals(initialTransition.getCategory(), currentProduct.getCategory().toString());
        Assert.assertEquals(initialTransition.getEventType(), BusinessSubscriptionEvent.EventType.ADD.toString());

        // This is the first transition
        Assert.assertNull(initialTransition.getPrevSlug());

        Assert.assertEquals(initialTransition.getNextBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD.toString());
        Assert.assertEquals(initialTransition.getNextCurrency(), account.getCurrency().toString());
        Assert.assertEquals(initialTransition.getNextPhase(), PhaseType.TRIAL.toString());
        // Trial: fixed price of zero
        Assert.assertEquals(initialTransition.getNextPrice().doubleValue(), (double) 0);
        Assert.assertEquals(initialTransition.getNextPriceList(), subscription.getCurrentPriceList().getName());
        Assert.assertEquals(initialTransition.getNextProductCategory(), currentProduct.getCategory().toString());
        Assert.assertEquals(initialTransition.getNextProductName(), currentProduct.getName());
        Assert.assertEquals(initialTransition.getNextProductType(), currentProduct.getCatalogName());
        Assert.assertEquals(initialTransition.getNextSlug(), currentProduct.getName().toLowerCase() + "-monthly-trial");
        Assert.assertEquals(initialTransition.getNextStartDate(), subscription.getStartDate());
        Assert.assertEquals(initialTransition.getNextState(), Subscription.SubscriptionState.ACTIVE.toString());

        // Check the second transition (from trial to evergreen)
        final BusinessSubscriptionTransition futureTransition = transitions.get(1);
        Assert.assertEquals(futureTransition.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(futureTransition.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(futureTransition.getCategory(), currentProduct.getCategory().toString());
        Assert.assertEquals(futureTransition.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CHANGE.toString());

        // The billing period should have changed (NO_BILLING_PERIOD for the trial period)
        Assert.assertEquals(futureTransition.getNextBillingPeriod(), BillingPeriod.MONTHLY.toString());
        Assert.assertEquals(initialTransition.getNextCurrency(), account.getCurrency().toString());
        // From trial to evergreen
        Assert.assertEquals(futureTransition.getNextPhase(), PhaseType.EVERGREEN.toString());
        Assert.assertTrue(futureTransition.getNextPrice().doubleValue() > 0);
        Assert.assertEquals(futureTransition.getNextPriceList(), subscription.getCurrentPriceList().getName());
        Assert.assertEquals(futureTransition.getNextProductCategory(), currentProduct.getCategory().toString());
        Assert.assertEquals(futureTransition.getNextProductName(), currentProduct.getName());
        Assert.assertEquals(futureTransition.getNextProductType(), currentProduct.getCatalogName());
        Assert.assertEquals(futureTransition.getNextSlug(), currentProduct.getName().toLowerCase() + "-monthly-evergreen");
        // 30 days trial
        Assert.assertEquals(futureTransition.getNextStartDate(), subscription.getStartDate().plusDays(30));
        Assert.assertEquals(futureTransition.getNextState(), Subscription.SubscriptionState.ACTIVE.toString());
    }

    private void verifyCancellationTransition(final Account account, final SubscriptionBundle bundle) throws CatalogApiException {
        final Product currentProduct = subscriptionPlan.getProduct();
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);

        final BusinessSubscriptionTransition cancellationRequest = transitions.get(2);
        Assert.assertEquals(cancellationRequest.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(cancellationRequest.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(cancellationRequest.getCategory(), currentProduct.getCategory().toString());
        Assert.assertEquals(cancellationRequest.getEventType(), BusinessSubscriptionEvent.EventType.CANCEL.toString());

        Assert.assertNull(cancellationRequest.getNextSlug());
    }

    private void verifySystemCancellationTransition(final Account account, final SubscriptionBundle bundle) throws CatalogApiException {
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);

        final BusinessSubscriptionTransition systemCancellation = transitions.get(3);
        Assert.assertEquals(systemCancellation.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(systemCancellation.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(systemCancellation.getCategory(), ProductCategory.BASE.toString());
        Assert.assertEquals(systemCancellation.getEventType(), BusinessSubscriptionEvent.EventType.SYSTEM_CANCEL.toString());

        Assert.assertNull(systemCancellation.getNextSlug());
    }

    private void verifyChangePlan(final Account account, final SubscriptionBundle bundle, final Subscription subscription) throws EntitlementUserApiException, InterruptedException {
        final String newProductName = "Assault-Rifle";
        final BillingPeriod newTerm = BillingPeriod.MONTHLY;
        final String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        final DateTime requestedDate = clock.getUTCNow();
        Assert.assertTrue(subscription.changePlan(newProductName, newTerm, newPlanSetName, requestedDate, callContext));

        waitALittle();

        // BST should have three transitions (a ADD_BASE, CHANGE_BASE and SYSTEM_CHANGE_BASE)
        final List<BusinessSubscriptionTransition> transitions = analyticsUserApi.getTransitionsForBundle(bundle.getExternalKey(), callContext);
        Assert.assertEquals(transitions.size(), 3);
        final BusinessSubscriptionTransition previousTransition = transitions.get(0);
        final BusinessSubscriptionTransition transition = transitions.get(1);
        Assert.assertEquals(transition.getBundleId(), bundle.getId());
        Assert.assertEquals(transition.getExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(transition.getAccountId(), account.getId());
        Assert.assertEquals(transition.getAccountKey(), account.getExternalKey());
        Assert.assertEquals(transition.getSubscriptionId(), subscription.getId());
        Assert.assertEquals(transition.getCategory(), ProductCategory.BASE.toString());
        Assert.assertEquals(transition.getEventType(), BusinessSubscriptionEvent.EventType.CHANGE.toString());

        // Verify the previous subscription is null
        Assert.assertNull(previousTransition.getPrevSlug());

        // Verify the next subscription
        // No billing period for the trial phase
        Assert.assertEquals(transition.getNextBillingPeriod(), BillingPeriod.NO_BILLING_PERIOD.toString());
        Assert.assertEquals(transition.getNextCurrency(), account.getCurrency().toString());
        Assert.assertEquals(transition.getNextPhase(), PhaseType.TRIAL.toString());
        // We're still in trial
        Assert.assertEquals(transition.getNextPrice().doubleValue(), 0.0);
        Assert.assertEquals(transition.getNextPriceList(), newPlanSetName);
        Assert.assertEquals(transition.getNextProductCategory(), ProductCategory.BASE.toString());
        Assert.assertEquals(transition.getNextProductName(), newProductName);
        Assert.assertEquals(transition.getNextProductType(), subscription.getCurrentPlan().getProduct().getCatalogName());
        Assert.assertEquals(transition.getNextSlug(), subscription.getCurrentPhase().getName());
        Assert.assertEquals(transition.getNextStartDate(), requestedDate);
        Assert.assertEquals(transition.getNextState(), Subscription.SubscriptionState.ACTIVE.toString());

        // The account should have two invoices for the trial phase of both subscriptions
        final List<BusinessInvoice> invoicesForAccount = analyticsUserApi.getInvoicesForAccount(account.getExternalKey(), callContext);
        Assert.assertEquals(invoicesForAccount.size(), 2);
        Assert.assertEquals(invoicesForAccount.get(0).getBalance().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(0).getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(0).getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(0).getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(0).getCurrency(), account.getCurrency());
        Assert.assertEquals(invoicesForAccount.get(1).getBalance().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(1).getAmountCharged().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(1).getAmountCredited().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(1).getAmountPaid().doubleValue(), 0.0);
        Assert.assertEquals(invoicesForAccount.get(1).getCurrency(), account.getCurrency());
    }

    private void waitALittle() throws InterruptedException {
        // We especially need to wait for entitlement events
        Thread.sleep(4000);
    }
}
