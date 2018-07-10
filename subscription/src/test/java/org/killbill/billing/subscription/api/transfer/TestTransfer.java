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

package org.killbill.billing.subscription.api.transfer;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.SubscriptionTestSuiteWithEmbeddedDB;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestTransfer extends SubscriptionTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(TestTransfer.class);

    protected UUID newAccountId;
    protected UUID finalNewAccountId;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        // Note: this will cleanup all tables
        super.beforeMethod();

        final AccountData accountData2 = subscriptionTestInitializer.initAccountData(clock);
        final Account account2 = createAccount(accountData2);
        finalNewAccountId = account2.getId();

        // internal context will be configured for accountId
        final AccountData accountData = subscriptionTestInitializer.initAccountData(clock);
        final Account account = createAccount(accountData);
        newAccountId = account.getId();
    }

    @Test(groups = "slow")
    public void testTransferBPInTrialWithNoCTD() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        final DateTime evergreenPhaseDate = ((DefaultSubscriptionBase) baseSubscription).getPendingTransition().getEffectiveTransitionTime();

        // MOVE A LITTLE, STILL IN TRIAL
        clock.addDays(20);

        final DateTime beforeTransferDate = clock.getUTCNow();
        final DateTime transferRequestedDate = clock.getUTCNow();

        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertNotNull(oldBaseSubscription.getEndDate());
        testUtil.assertDateWithin(oldBaseSubscription.getEndDate(), beforeTransferDate, afterTransferDate);
        assertTrue(oldBaseSubscription.getEndDate().compareTo(transferRequestedDate) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION STARTING ON TRANSFER_DATE
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);

        final SubscriptionBase newBaseSubscription = subscriptions.get(0);
        assertTrue(((DefaultSubscriptionBase) newBaseSubscription).getAlignStartDate().compareTo(((DefaultSubscriptionBase) oldBaseSubscription).getAlignStartDate()) == 0);

        // CHECK NEXT PENDING PHASE IS ALIGNED WITH OLD SUBSCRIPTION START DATE
        assertEquals(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).size(), 2);
        assertTrue(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).get(1).getEffectiveTransitionTime().compareTo(evergreenPhaseDate) == 0);

        final Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
    }

    @Test(groups = "slow")
    public void testTransferBPInTrialWithCTD() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30);

        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final DateTime evergreenPhaseDate = ((DefaultSubscriptionBase) baseSubscription).getPendingTransition().getEffectiveTransitionTime();

        // MOVE A LITTLE, STILL IN TRIAL
        clock.addDays(20);

        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        final DateTime transferRequestedDate = clock.getUTCNow();
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertNotNull(oldBaseSubscription.getFutureEndDate());
        assertTrue(oldBaseSubscription.getFutureEndDate().compareTo(ctd) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION STARTING ON TRANSFER_DATE
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);

        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);

        final SubscriptionBase newBaseSubscription = subscriptions.get(0);
        assertTrue(((DefaultSubscriptionBase) newBaseSubscription).getAlignStartDate().compareTo(((DefaultSubscriptionBase) oldBaseSubscription).getAlignStartDate()) == 0);

        // CHECK NEXT PENDING PHASE IS ALIGNED WITH OLD SUBSCRIPTION START DATE
        assertEquals(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).size(), 2);
        assertTrue(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).get(1).getEffectiveTransitionTime().compareTo(evergreenPhaseDate) == 0);

        final Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
    }

    @Test(groups = "slow")
    public void testTransferBPNoTrialWithNoCTD() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(40);
        assertListenerStatus();

        final DateTime beforeTransferDate = clock.getUTCNow();
        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertNotNull(oldBaseSubscription.getEndDate());
        testUtil.assertDateWithin(oldBaseSubscription.getEndDate(), beforeTransferDate, afterTransferDate);
        assertTrue(oldBaseSubscription.getEndDate().compareTo(transferRequestedDate) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION STARTING ON TRANSFER_DATE
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);

        final SubscriptionBase newBaseSubscription = subscriptions.get(0);
        assertTrue(((DefaultSubscriptionBase) newBaseSubscription).getAlignStartDate().compareTo(((DefaultSubscriptionBase) baseSubscription).getAlignStartDate()) == 0);

        // CHECK ONLY ONE PHASE EXISTS
        assertEquals(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).size(), 1);

        final Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
    }

    @Test(groups = "slow")
    public void testTransferBPNoTrialWithCTD() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(40);
        assertListenerStatus();

        // SET CTD
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        assertNotNull(oldBaseSubscription.getFutureEndDate());
        assertTrue(oldBaseSubscription.getFutureEndDate().compareTo(ctd) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION STARTING ON TRANSFER_DATE
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);

        final SubscriptionBase newBaseSubscription = subscriptions.get(0);
        assertTrue(((DefaultSubscriptionBase) newBaseSubscription).getAlignStartDate().compareTo(((DefaultSubscriptionBase) baseSubscription).getAlignStartDate()) == 0);

        // CHECK ONLY ONE PHASE EXISTS
        assertEquals(subscriptionInternalApi.getAllTransitions(newBaseSubscription, internalCallContext).size(), 1);

        Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        // MAKE A PLAN CHANGE IMM
        clock.addDays(5);

        final String newBaseProduct1 = "Assault-Rifle";
        final BillingPeriod newBaseTerm1 = BillingPeriod.ANNUAL;
        testListener.pushExpectedEvent(NextEvent.CHANGE);
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(newBaseProduct1, newBaseTerm1, basePriceList);
        newBaseSubscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier, null, null), callContext);
        assertListenerStatus();

        newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), newBaseProduct1);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        // SET CTD AND MAKE CHANGE EOT
        clock.addDays(2);

        final DateTime newCtd = newBaseSubscription.getStartDate().plusYears(1);
        subscriptionInternalApi.setChargedThroughDate(newBaseSubscription.getId(), newCtd, internalCallContext);
        final SubscriptionBase newBaseSubscriptionWithCtd = subscriptionInternalApi.getSubscriptionFromId(newBaseSubscription.getId(), internalCallContext);

        final String newBaseProduct2 = "Pistol";
        final BillingPeriod newBaseTerm2 = BillingPeriod.ANNUAL;
        final PlanPhaseSpecifier planPhaseSpecifier1 = new PlanPhaseSpecifier(newBaseProduct2, newBaseTerm2, basePriceList);
        newBaseSubscriptionWithCtd.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier1, null, null), callContext);

        newPlan = newBaseSubscriptionWithCtd.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), newBaseProduct1);
        assertEquals(newBaseSubscriptionWithCtd.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        assertNotNull(newBaseSubscriptionWithCtd.getPendingTransition());
        assertEquals(newBaseSubscriptionWithCtd.getPendingTransition().getEffectiveTransitionTime(), newCtd);
    }

    @Test(groups = "slow")
    public void testTransferWithAO() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE 3 DAYS AND CREATE AO1
        clock.addDays(3);
        final String aoProduct1 = "Telescopic-Scope";
        final BillingPeriod aoTerm1 = BillingPeriod.MONTHLY;
        final DefaultSubscriptionBase aoSubscription1 = testUtil.createSubscription(bundle, aoProduct1, aoTerm1, basePriceList);
        assertEquals(aoSubscription1.getState(), EntitlementState.ACTIVE);

        // MOVE ANOTHER 25 DAYS AND CREATE AO2 [ BP STILL IN TRIAL]
        // LASER-SCOPE IS SUBSCRIPTION ALIGN SO EVERGREN WILL ONLY START IN A MONTH
        clock.addDays(25);
        final String aoProduct2 = "Laser-Scope";
        final BillingPeriod aoTerm2 = BillingPeriod.MONTHLY;
        final DefaultSubscriptionBase aoSubscription2 = testUtil.createSubscription(bundle, aoProduct2, aoTerm2, basePriceList);
        assertEquals(aoSubscription2.getState(), EntitlementState.ACTIVE);

        // MOVE AFTER TRIAL AND AO DISCOUNT PHASE [LASER SCOPE STILL IN DISCOUNT]
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(5);
        assertListenerStatus();

        // SET CTD TO TRIGGER CANCELLATION EOT
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        // RETRIEVE NEW BUNDLE AND CHECK SUBSCRIPTIONS
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 3);
        boolean foundBP = false;
        boolean foundAO1 = false;
        boolean foundAO2 = false;
        for (final SubscriptionBase cur : subscriptions) {
            final Plan curPlan = cur.getCurrentPlan();
            final Product curProduct = curPlan.getProduct();
            if (curProduct.getName().equals(baseProduct)) {
                foundBP = true;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo(((DefaultSubscriptionBase) baseSubscription).getAlignStartDate()) == 0);
                assertNull(cur.getPendingTransition());
            } else if (curProduct.getName().equals(aoProduct1)) {
                foundAO1 = true;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo((aoSubscription1).getAlignStartDate()) == 0);
                assertNull(cur.getPendingTransition());
            } else if (curProduct.getName().equals(aoProduct2)) {
                foundAO2 = true;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo((aoSubscription2).getAlignStartDate()) == 0);
                assertNotNull(cur.getPendingTransition());
            } else {
                Assert.fail("Unexpected product " + curProduct.getName());
            }
        }
        assertTrue(foundBP);
        assertTrue(foundAO1);
        assertTrue(foundAO2);

        // MOVE AFTER CANCEL DATE TO TRIGGER OLD SUBSCRIPTIONS CANCELLATION + LASER_SCOPE PHASE EVENT
        testListener.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        clock.addMonths(1);
        assertListenerStatus();

        // ISSUE ANOTHER TRANSFER TO CHECK THAT WE CAN TRANSFER AGAIN-- NOTE WILL NOT WORK ON PREVIOUS ACCOUNT (LIMITATION)
        final DateTime newTransferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(newBundle.getAccountId(), finalNewAccountId, newBundle.getExternalKey(), newTransferRequestedDate, true, false, callContext);
        assertListenerStatus();
    }

    @Test(groups = "slow")
    public void testTransferWithAOCancelled() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // MOVE 3 DAYS AND CREATE AO1
        clock.addDays(3);
        final String aoProduct1 = "Telescopic-Scope";
        final BillingPeriod aoTerm1 = BillingPeriod.MONTHLY;
        final DefaultSubscriptionBase aoSubscription1 = testUtil.createSubscription(bundle, aoProduct1, aoTerm1, basePriceList);
        assertEquals(aoSubscription1.getState(), EntitlementState.ACTIVE);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        // SET CTD TO TRIGGER CANCELLATION EOT
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        // SET CTD TO TRIGGER CANCELLATION EOT
        subscriptionInternalApi.setChargedThroughDate(aoSubscription1.getId(), ctd, internalCallContext);

        // CANCEL ADDON
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        aoSubscription1.cancelWithDate(clock.getUTCNow(), callContext);
        assertListenerStatus();

        clock.addDays(1);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);
    }

    @Test(groups = "slow")
    public void testTransferWithUncancel() throws Exception {
        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(30);
        assertListenerStatus();

        // SET CTD TO TRIGGER CANCELLATION EOT
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        subscriptionInternalApi.setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        // CANCEL BP
        baseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), internalCallContext);
        baseSubscription.cancel(callContext);

        // MOVE CLOCK one day AHEAD AND UNCANCEL BP
        clock.addDays(1);
        testListener.pushExpectedEvent(NextEvent.UNCANCEL);
        baseSubscription.uncancel(callContext);
        assertListenerStatus();

        // MOVE CLOCK one day AHEAD AND UNCANCEL BP
        clock.addDays(1);
        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);
    }
}
