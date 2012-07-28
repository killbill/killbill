/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.entitlement.api.transfer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestTransfer extends TestApiBase {

    protected static final Logger log = LoggerFactory.getLogger(TestTransfer.class);


    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(groups = "slow")
    public void testTransferBPInTrialWithNoCTD() throws Exception {

        final UUID newAccountId = UUID.randomUUID();

        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        final DateTime evergreenPhaseDate = baseSubscription.getPendingTransition().getEffectiveTransitionTime();

        // MOVE A LITTLE, STILL IN TRIAL
        clock.addDays(20);

        final DateTime beforeTransferDate = clock.getUTCNow();
        final DateTime transferRequestedDate = clock.getUTCNow();

        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getKey(), transferRequestedDate, false, context);
        assertTrue(testListener.isCompleted(3000));
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final Subscription oldBaseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertNotNull(oldBaseSubscription.getEndDate());
        assertDateWithin(oldBaseSubscription.getEndDate(), beforeTransferDate, afterTransferDate);
        assertTrue(oldBaseSubscription.getEndDate().compareTo(transferRequestedDate) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION SARTING ON TRANSFER_DATE
        SubscriptionBundle newBundle = entitlementApi.getBundleForAccountAndKey(newAccountId, bundle.getKey());

        List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(newBundle.getId());
        assertEquals(subscriptions.size(), 1);

        final Subscription newBaseSubscription = subscriptions.get(0);
        assertTrue(newBaseSubscription.getStartDate().compareTo(oldBaseSubscription.getStartDate()) == 0);

        // CHECK NEXT PENDING PHASE IS ALIGNED WITH OLD SUBSCRIPTION START DATE
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertTrue(newBaseSubscription.getAllTransitions().get(1).getEffectiveTransitionTime().compareTo(evergreenPhaseDate) == 0);

        Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
    }


    @Test(groups = "slow")
    public void testTransferBPInTrialWithCTD() throws Exception {

        final UUID newAccountId = UUID.randomUUID();

        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30);

        billingApi.setChargedThroughDate(baseSubscription.getId(), ctd, context);

        final DateTime evergreenPhaseDate = baseSubscription.getPendingTransition().getEffectiveTransitionTime();

        // MOVE A LITTLE, STILL IN TRIAL
        clock.addDays(20);

        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        final DateTime transferRequestedDate = clock.getUTCNow();
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getKey(), transferRequestedDate, false, context);
        assertTrue(testListener.isCompleted(3000));

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final Subscription oldBaseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertNotNull(oldBaseSubscription.getFutureEndDate());
        assertTrue(oldBaseSubscription.getFutureEndDate().compareTo(ctd) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION SARTING ON TRANSFER_DATE
        SubscriptionBundle newBundle = entitlementApi.getBundleForAccountAndKey(newAccountId, bundle.getKey());

        List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(newBundle.getId());
        assertEquals(subscriptions.size(), 1);

        final Subscription newBaseSubscription = subscriptions.get(0);
        assertTrue(newBaseSubscription.getStartDate().compareTo(oldBaseSubscription.getStartDate()) == 0);

        // CHECK NEXT PENDING PHASE IS ALIGNED WITH OLD SUBSCRIPTION START DATE
        assertEquals(newBaseSubscription.getAllTransitions().size(), 2);
        assertTrue(newBaseSubscription.getAllTransitions().get(1).getEffectiveTransitionTime().compareTo(evergreenPhaseDate) == 0);

        Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.TRIAL);
    }


    @Test(groups = "slow")
    public void testTransferBPNoTrialWithNoCTD() throws Exception {

        final UUID newAccountId = UUID.randomUUID();

        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(40);
        assertTrue(testListener.isCompleted(3000));

        final DateTime beforeTransferDate = clock.getUTCNow();
        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getKey(), transferRequestedDate, false, context);
        assertTrue(testListener.isCompleted(3000));
        final DateTime afterTransferDate = clock.getUTCNow();

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final Subscription oldBaseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertNotNull(oldBaseSubscription.getEndDate());
        assertDateWithin(oldBaseSubscription.getEndDate(), beforeTransferDate, afterTransferDate);
        assertTrue(oldBaseSubscription.getEndDate().compareTo(transferRequestedDate) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION SARTING ON TRANSFER_DATE
        SubscriptionBundle newBundle = entitlementApi.getBundleForAccountAndKey(newAccountId, bundle.getKey());

        List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(newBundle.getId());
        assertEquals(subscriptions.size(), 1);

        final Subscription newBaseSubscription = subscriptions.get(0);
        assertTrue(newBaseSubscription.getStartDate().compareTo(baseSubscription.getStartDate()) == 0);

        // CHECK ONLY ONE PHASE EXISTS
        assertEquals(newBaseSubscription.getAllTransitions().size(), 1);

        Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);
    }

    @Test(groups = "slow")
    public void testTransferBPNoTrialWithCTD() throws Exception {

        final UUID newAccountId = UUID.randomUUID();

        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE AFTER TRIAL
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(40);
        assertTrue(testListener.isCompleted(3000));

        // SET CTD
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        billingApi.setChargedThroughDate(baseSubscription.getId(), ctd, context);


        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getKey(), transferRequestedDate, false, context);
        assertTrue(testListener.isCompleted(3000));

        // CHECK OLD BASE IS CANCEL AT THE TRANSFER DATE
        final Subscription oldBaseSubscription = entitlementApi.getSubscriptionFromId(baseSubscription.getId());
        assertNotNull(oldBaseSubscription.getFutureEndDate());
        assertTrue(oldBaseSubscription.getFutureEndDate().compareTo(ctd) == 0);

        // CHECK NEW BUNDLE EXIST, WITH ONE SUBSCRIPTION SARTING ON TRANSFER_DATE
        SubscriptionBundle newBundle = entitlementApi.getBundleForAccountAndKey(newAccountId, bundle.getKey());

        List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(newBundle.getId());
        assertEquals(subscriptions.size(), 1);

        final Subscription newBaseSubscription = subscriptions.get(0);
        assertTrue(newBaseSubscription.getStartDate().compareTo(baseSubscription.getStartDate()) == 0);

        // CHECK ONLY ONE PHASE EXISTS
        assertEquals(newBaseSubscription.getAllTransitions().size(), 1);

        Plan newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), baseProduct);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        // MAKE A PLAN CHANGE IMM
        clock.addDays(5);

        final String newBaseProduct1 = "Assault-Rifle";
        final BillingPeriod newBaseTerm1 = BillingPeriod.ANNUAL;
        final DateTime changeDate1 = clock.getUTCNow();
        newBaseSubscription.changePlan(newBaseProduct1, newBaseTerm1, basePriceList, changeDate1, context);

        newPlan = newBaseSubscription.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), newBaseProduct1);
        assertEquals(newBaseSubscription.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        // SET CTD AND MAKE CHANGE EOT
        clock.addDays(2);

        DateTime newCtd = newBaseSubscription.getStartDate().plusYears(1);
        billingApi.setChargedThroughDate(newBaseSubscription.getId(), newCtd, context);
        final Subscription newBaseSubscriptionWithCtd = entitlementApi.getSubscriptionFromId(newBaseSubscription.getId());

        final String newBaseProduct2 = "Pistol";
        final BillingPeriod newBaseTerm2 = BillingPeriod.ANNUAL;
        final DateTime changeDate2 = clock.getUTCNow();
        newBaseSubscriptionWithCtd.changePlan(newBaseProduct2, newBaseTerm2, basePriceList, changeDate2, context);

        newPlan = newBaseSubscriptionWithCtd.getCurrentPlan();
        assertEquals(newPlan.getProduct().getName(), newBaseProduct1);
        assertEquals(newBaseSubscriptionWithCtd.getCurrentPhase().getPhaseType(), PhaseType.EVERGREEN);

        assertNotNull(newBaseSubscriptionWithCtd.getPendingTransition());
        assertEquals(newBaseSubscriptionWithCtd.getPendingTransition().getEffectiveTransitionTime(), newCtd);
    }

    @Test(groups = "slow")
    public void testTransferWithAO() throws Exception {

        final UUID newAccountId = UUID.randomUUID();

        final String baseProduct = "Shotgun";
        final BillingPeriod baseTerm = BillingPeriod.MONTHLY;
        final String basePriceList = PriceListSet.DEFAULT_PRICELIST_NAME;

        // CREATE BP
        final Subscription baseSubscription = createSubscription(baseProduct, baseTerm, basePriceList);

        // MOVE 3 DAYS AND CREATE AO1
        clock.addDays(3);
        final String aoProduct1 = "Telescopic-Scope";
        final BillingPeriod aoTerm1 = BillingPeriod.MONTHLY;
        final SubscriptionData aoSubscription1 = createSubscription(aoProduct1, aoTerm1, basePriceList);
        assertEquals(aoSubscription1.getState(), SubscriptionState.ACTIVE);

        // MOVE ANOTHER 25 DAYS AND CREATE AO2 [ BP STILL IN TRIAL]
        // LASER-SCOPE IS SUBSCRIPTION ALIGN SO EVERGREN WILL ONLY START IN A MONTH
        clock.addDays(25);
        final String aoProduct2 = "Laser-Scope";
        final BillingPeriod aoTerm2 = BillingPeriod.MONTHLY;
        final SubscriptionData aoSubscription2 = createSubscription(aoProduct2, aoTerm2, basePriceList);
        assertEquals(aoSubscription2.getState(), SubscriptionState.ACTIVE);

        // MOVE AFTER TRIAL AND AO DISCOUNT PHASE [LASER SCOPE STILL IN DISCOUNT]
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(5);
        assertTrue(testListener.isCompleted(3000));

        // SET CTD TO TRIGGER CANCELLATION EOT
        final DateTime ctd = baseSubscription.getStartDate().plusDays(30).plusMonths(1);
        billingApi.setChargedThroughDate(baseSubscription.getId(), ctd, context);

        final DateTime transferRequestedDate = clock.getUTCNow();
        testListener.pushExpectedEvent(NextEvent.TRANSFER);
        transferApi.transferBundle(bundle.getAccountId(), newAccountId, bundle.getKey(), transferRequestedDate, true, context);
        assertTrue(testListener.isCompleted(3000));

        // RETRIEVE NEW BUNDLE AND CHECK SUBSCRIPTIONS
        SubscriptionBundle newBundle = entitlementApi.getBundleForAccountAndKey(newAccountId, bundle.getKey());
        List<Subscription> subscriptions = entitlementApi.getSubscriptionsForBundle(newBundle.getId());
        assertEquals(subscriptions.size(), 3);
        boolean foundBP = false;
        boolean foundAO1 = false;
        boolean foundAO2 = false;
        for (Subscription cur : subscriptions) {
            Plan curPlan = cur.getCurrentPlan();
            Product curProduct = curPlan.getProduct();
            if (curProduct.getName().equals(baseProduct)) {
                foundBP = true;
                assertTrue(cur.getStartDate().compareTo(baseSubscription.getStartDate()) == 0);
                assertNull(cur.getPendingTransition());
            } else if (curProduct.getName().equals(aoProduct1)) {
                foundAO1 = true;
                assertTrue(cur.getStartDate().compareTo(aoSubscription1.getStartDate()) == 0);
                assertNull(cur.getPendingTransition());
            } else if (curProduct.getName().equals(aoProduct2)) {
                foundAO2 = true;
                assertTrue(cur.getStartDate().compareTo(aoSubscription2.getStartDate()) == 0);
                assertNotNull(cur.getPendingTransition());
            } else {
                Assert.fail("Unexpected product " + curProduct.getName());
            }
        }
        assertTrue(foundBP);
        assertTrue(foundAO1);
        assertTrue(foundAO2);

        // MOVE AFTER CANCEL DATE TO TRIGGER OLD SUBSCRIPTIONS CANCELLATION + LASER_SCOPE PHASE EVENT
        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        clock.addMonths(1);
        assertTrue(testListener.isCompleted(3000));
    }

}
