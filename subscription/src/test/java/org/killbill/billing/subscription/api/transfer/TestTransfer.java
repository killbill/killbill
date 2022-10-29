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

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.callcontext.InternalCallContext;
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
import org.killbill.billing.util.callcontext.CallContext;
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
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
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

        setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final DateTime evergreenPhaseDate = ((DefaultSubscriptionBase) baseSubscription).getPendingTransition().getEffectiveTransitionTime();

        // MOVE A LITTLE, STILL IN TRIAL
        clock.addDays(20);

        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        final DateTime transferRequestedDate = clock.getUTCNow();
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
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
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
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
        setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, false, false, callContext);
        assertListenerStatus();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final SubscriptionBase oldBaseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
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
        newBaseSubscription.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier, null, null, null), callContext);
        assertListenerStatus();

        newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), newBaseProduct1);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        // SET CTD AND MAKE CHANGE EOT
        clock.addDays(2);

        final DateTime newCtd = newBaseSubscription.getStartDate().plusYears(1);
        setChargedThroughDate(newBaseSubscription.getId(), newCtd, internalCallContext);
        final SubscriptionBase newBaseSubscriptionWithCtd = subscriptionInternalApi.getSubscriptionFromId(newBaseSubscription.getId(), false, internalCallContext);

        final String newBaseProduct2 = "Pistol";
        final BillingPeriod newBaseTerm2 = BillingPeriod.ANNUAL;
        final PlanPhaseSpecifier planPhaseSpecifier1 = new PlanPhaseSpecifier(newBaseProduct2, newBaseTerm2, basePriceList);
        newBaseSubscriptionWithCtd.changePlan(new DefaultEntitlementSpecifier(planPhaseSpecifier1, null, null, null), callContext);

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
        setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        final HashMap<UUID, String> subExtKeysMap = new HashMap<>();
        subExtKeysMap.put(baseSubscription.getId(), "new-base-key");
        subExtKeysMap.put(aoSubscription1.getId(), "new-ao1-key");
        subExtKeysMap.put(aoSubscription2.getId(), "new-ao2-key");

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), subExtKeysMap, transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        // RETRIEVE NEW BUNDLE AND CHECK SUBSCRIPTIONS
        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 3);

        SubscriptionBase newBaseSubscription = null;
        SubscriptionBase newAoSubscription1 = null;
        SubscriptionBase newAoSubscription2 = null;
        for (final SubscriptionBase cur : subscriptions) {
            final Plan curPlan = cur.getCurrentPlan();
            final Product curProduct = curPlan.getProduct();
            if (curProduct.getName().equals(baseProduct)) {
                newBaseSubscription = cur;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo(((DefaultSubscriptionBase) baseSubscription).getAlignStartDate()) == 0);
                assertNull(cur.getPendingTransition());
                assertEquals(cur.getExternalKey(), "new-base-key");
            } else if (curProduct.getName().equals(aoProduct1)) {
                newAoSubscription1 = cur;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo((aoSubscription1).getAlignStartDate()) == 0);
                assertNull(cur.getPendingTransition());
                assertEquals(cur.getExternalKey(), "new-ao1-key");
            } else if (curProduct.getName().equals(aoProduct2)) {
                newAoSubscription2 = cur;
                assertTrue(((DefaultSubscriptionBase) cur).getAlignStartDate().compareTo((aoSubscription2).getAlignStartDate()) == 0);
                assertNotNull(cur.getPendingTransition());
                assertEquals(cur.getExternalKey(), "new-ao2-key");
            } else {
                Assert.fail("Unexpected product " + curProduct.getName());
            }
        }
        assertNotNull(newBaseSubscription);
        assertNotNull(newAoSubscription1);
        assertNotNull(newAoSubscription2);

        // MOVE AFTER CANCEL DATE TO TRIGGER OLD SUBSCRIPTIONS CANCELLATION + LASER_SCOPE PHASE EVENT
        testListener.pushExpectedEvents(NextEvent.PHASE, NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        clock.addMonths(1);
        assertListenerStatus();

        // ISSUE ANOTHER TRANSFER TO CHECK THAT WE CAN TRANSFER AGAIN-- NOTE WILL NOT WORK ON PREVIOUS ACCOUNT (LIMITATION)

        subExtKeysMap.clear();
        subExtKeysMap.put(newBaseSubscription.getId(), "latest-base-key");
        subExtKeysMap.put(newAoSubscription1.getId(), "latest-ao1-key");
        subExtKeysMap.put(newAoSubscription2.getId(), "latest-ao2-key");

        final DateTime newTransferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(newBundle.getAccountId(), finalNewAccountId, newBundle.getExternalKey(), subExtKeysMap, newTransferRequestedDate, true, false, callContext);
        assertListenerStatus();

        final List<SubscriptionBaseBundle> latestBundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(finalNewAccountId, newBundle.getExternalKey(), internalCallContext);
        assertEquals(latestBundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle latestSubscriptionBundle = latestBundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> latestSubscriptions = subscriptionInternalApi.getSubscriptionsForBundle(latestSubscriptionBundle.getId(), null, internalCallContext);
        assertEquals(latestSubscriptions.size(), 3);

        final SubscriptionBase latestBaseSubscription = subscriptionInternalApi.getSubscriptionFromExternalKey("latest-base-key", internalCallContext);
        assertNotNull(latestBaseSubscription);
        assertEquals(latestBaseSubscription.getBundleId(), latestSubscriptionBundle.getId());

        final SubscriptionBase latestAoSubscription1 = subscriptionInternalApi.getSubscriptionFromExternalKey("latest-ao1-key", internalCallContext);
        assertNotNull(latestAoSubscription1);
        assertEquals(latestAoSubscription1.getBundleId(), latestSubscriptionBundle.getId());

        final SubscriptionBase latestAoSubscription2 = subscriptionInternalApi.getSubscriptionFromExternalKey("latest-ao2-key", internalCallContext);
        assertNotNull(latestAoSubscription2);
        assertEquals(latestAoSubscription2.getBundleId(), latestSubscriptionBundle.getId());
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
        setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        // SET CTD TO TRIGGER CANCELLATION EOT
        setChargedThroughDate(aoSubscription1.getId(), ctd, internalCallContext);

        // CANCEL ADDON
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        aoSubscription1.cancelWithDate(clock.getUTCNow(), callContext);
        assertListenerStatus();

        clock.addDays(1);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, true, false, callContext);
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
        setChargedThroughDate(baseSubscription.getId(), ctd, internalCallContext);

        // CANCEL BP
        baseSubscription = subscriptionInternalApi.getSubscriptionFromId(baseSubscription.getId(), false, internalCallContext);
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
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        final List<SubscriptionBaseBundle> bundlesForAccountAndKey = subscriptionInternalApi.getBundlesForAccountAndKey(newAccountId, bundle.getExternalKey(), internalCallContext);
        assertEquals(bundlesForAccountAndKey.size(), 1);

        final SubscriptionBaseBundle newBundle = bundlesForAccountAndKey.get(0);
        final List<SubscriptionBase> subscriptions = subscriptionInternalApi.getSubscriptionsForBundle(newBundle.getId(), null, internalCallContext);
        assertEquals(subscriptions.size(), 1);
    }

    @Test(groups = "slow")
    public void testWithBCDUpdatePriorTransfer() throws Exception {
        final String baseProduct = "Pistol";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = "notrial";

        final SubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList);

        // Create context for the updateBCD as it looks like test default internalCallContext to be on newAccountId
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(),
                                                                                                         ObjectType.ACCOUNT,
                                                                                                         this.internalCallContext.getUpdatedBy(),
                                                                                                         this.internalCallContext.getCallOrigin(),
                                                                                                         this.internalCallContext.getContextUserType(),
                                                                                                         this.internalCallContext.getUserToken(),
                                                                                                         this.internalCallContext.getTenantRecordId());

        subscriptionInternalApi.updateBCD(baseSubscription.getId(), 8, clock.getUTCToday(), internalCallContext);

        // 2012-5-8
        testListener.pushExpectedEvent(NextEvent.BCD_CHANGE);
        clock.addDays(1);
        assertListenerStatus();


        // Do a second BCD_UPDATE
        subscriptionInternalApi.updateBCD(baseSubscription.getId(), 9, clock.getUTCToday(), internalCallContext);

        // 2012-5-9
        testListener.pushExpectedEvent(NextEvent.BCD_CHANGE);
        clock.addDays(1);
        assertListenerStatus();

        // Transfer date = 2012-5-12
        clock.addDays(3);
        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.BCD_CHANGE, NextEvent.CANCEL);
        final SubscriptionBaseBundle newBundle = transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();

        // Check the transferred subscription has the latest value for the BCD
        final DefaultSubscriptionBase newSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(newBundle.getId(), internalCallContext);
        assertEquals(newSubscription.getBillCycleDayLocal().intValue(), 9);
    }



    @Test(groups = "slow")
    public void testTransferWithPendingSubscription() throws Exception {
        final String baseProduct = "Pistol";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = "notrial";

        final LocalDate init = clock.getUTCToday(); // 2012-05-07
        final LocalDate futureCreationDate = init.plusDays(10);  // 2012-05-17

        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList, futureCreationDate);
        assertListenerStatus();
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.PENDING);


        // Update BCD to align to future start date
        subscriptionInternalApi.updateBCD(baseSubscription.getId(), 17, futureCreationDate, internalCallContext);

        clock.addDays(3);
        final DateTime transferRequestedDate = clock.getUTCNow();
        final SubscriptionBaseBundle newBundle = transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, true, false, callContext);


        // Create context for the old Subscription as it looks like test default initialInternalCallContext to be on newAccountId
        final InternalCallContext initialInternalCallContext = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(),
                                                                                                             ObjectType.ACCOUNT,
                                                                                                             this.internalCallContext.getUpdatedBy(),
                                                                                                             this.internalCallContext.getCallOrigin(),
                                                                                                             this.internalCallContext.getContextUserType(),
                                                                                                             this.internalCallContext.getUserToken(),
                                                                                                             this.internalCallContext.getTenantRecordId());



        // Check transfer was realigned to futureCreationDate, i.e both the CANCEL an old subscription and CREATE on the new one
        DefaultSubscriptionBase oldSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(bundle.getId(), initialInternalCallContext);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getStartDate()), futureCreationDate);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getFutureEndDate()), futureCreationDate);
        assertNull(oldSubscription.getEndDate());
        assertEquals(oldSubscription.getState(), EntitlementState.PENDING);


        DefaultSubscriptionBase newSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(newBundle.getId(), internalCallContext);
        assertEquals(internalCallContext.toLocalDate(newSubscription.getStartDate()), futureCreationDate);
        assertNull(newSubscription.getEndDate());
        assertEquals(newSubscription.getState(), EntitlementState.PENDING);

        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.CANCEL, NextEvent.BCD_CHANGE, NextEvent.TRANSFER);
        clock.addDays(7);
        assertListenerStatus();

        oldSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(bundle.getId(), initialInternalCallContext);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getStartDate()), futureCreationDate);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getEndDate()), futureCreationDate);
        assertEquals(oldSubscription.getState(), EntitlementState.CANCELLED);

        newSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(newBundle.getId(), internalCallContext);
        assertEquals(internalCallContext.toLocalDate(newSubscription.getStartDate()), futureCreationDate);
        assertEquals(newSubscription.getState(), EntitlementState.ACTIVE);
        assertEquals(newSubscription.getBillCycleDayLocal().intValue(), 17);

    }

    @Test(groups = "slow")
    public void testTransferPriorCreationDate() throws Exception {
        final String baseProduct = "Pistol";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = "notrial";

        final LocalDate init = clock.getUTCToday(); // 2012-05-07
        final LocalDate creationDate = init; // 2012-05-07

        DefaultSubscriptionBase baseSubscription = testUtil.createSubscription(bundle, baseProduct, baseTerm, basePriceList, creationDate);
        assertListenerStatus();
        assertNotNull(baseSubscription);
        assertEquals(baseSubscription.getState(), EntitlementState.ACTIVE);

        // Update BCD to align to future start date
        testListener.pushExpectedEvent(NextEvent.BCD_CHANGE);
        subscriptionInternalApi.updateBCD(baseSubscription.getId(), 7, creationDate, internalCallContext);
        assertListenerStatus();

        clock.addDays(3);

        // Transfer with a date prior the start date of the subscription
        final DateTime transferRequestedDate = creationDate.minusDays(3).toDateTimeAtCurrentTime();
        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.BCD_CHANGE, NextEvent.CANCEL);
        final SubscriptionBaseBundle newBundle = transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getExternalKey(), new HashMap<>(), transferRequestedDate, true, false, callContext);
        assertListenerStatus();
        // Create context for the old Subscription as it looks like test default initialInternalCallContext to be on newAccountId
        final InternalCallContext initialInternalCallContext = internalCallContextFactory.createInternalCallContext(bundle.getAccountId(),
                                                                                                                    ObjectType.ACCOUNT,
                                                                                                                    this.internalCallContext.getUpdatedBy(),
                                                                                                                    this.internalCallContext.getCallOrigin(),
                                                                                                                    this.internalCallContext.getContextUserType(),
                                                                                                                    this.internalCallContext.getUserToken(),
                                                                                                                    this.internalCallContext.getTenantRecordId());

        // We verify everything got realigned to the start date of the subscription.
        DefaultSubscriptionBase newSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(newBundle.getId(), internalCallContext);
        assertEquals(internalCallContext.toLocalDate(newSubscription.getStartDate()), creationDate);
        assertNull(newSubscription.getEndDate());
        assertEquals(newSubscription.getState(), EntitlementState.ACTIVE);

        DefaultSubscriptionBase oldSubscription = (DefaultSubscriptionBase) subscriptionInternalApi.getBaseSubscription(bundle.getId(), initialInternalCallContext);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getStartDate()), creationDate);
        assertEquals(initialInternalCallContext.toLocalDate(oldSubscription.getEndDate()), creationDate);
        assertEquals(oldSubscription.getState(), EntitlementState.CANCELLED);

    }
}
