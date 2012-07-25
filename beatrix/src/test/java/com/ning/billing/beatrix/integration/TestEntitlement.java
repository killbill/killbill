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

import org.joda.time.LocalDate;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Guice(modules = {BeatrixModule.class})
public class TestEntitlement extends TestIntegrationBase {

    @Test(groups = "slow")
    public void testForcePolicy() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        final LocalDate today = new LocalDate(2012, 4, 1);
        final Account account = createAccountWithPaymentMethod(getAccountData(1));

        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDeltaFromReality(today.toDateTimeAtCurrentTime().getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.ANNUAL;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.INVOICE);
        final PlanPhaseSpecifier bpPlanPhaseSpecifier = new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null);
        final SubscriptionData bpSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                       bpPlanPhaseSpecifier,
                                                                                                                       null,
                                                                                                                       context));
        assertNotNull(bpSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
        assertEquals(invoiceUserApi.getInvoicesByAccount(account.getId()).size(), 1);

        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId()).getCurrentPlan().getBillingPeriod(), BillingPeriod.ANNUAL);

        // Move out of trials for interesting invoices adjustments
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        clock.addDays(40);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        //
        // FORCE AN IMMEDIATE CHANGE OF THE BILLING PERIOD
        //
        assertTrue(bpSubscription.changePlanWithPolicy(productName, BillingPeriod.MONTHLY, planSetName, clock.getUTCNow(), ActionPolicy.IMMEDIATE, context));
        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId()).getCurrentPlan().getBillingPeriod(), BillingPeriod.MONTHLY);

        //
        // FORCE ANOTHER CHANGE
        //
        assertTrue(bpSubscription.changePlanWithPolicy(productName, BillingPeriod.ANNUAL, planSetName, clock.getUTCNow(), ActionPolicy.IMMEDIATE, context));
        assertEquals(entitlementUserApi.getSubscriptionFromId(bpSubscription.getId()).getCurrentPlan().getBillingPeriod(), BillingPeriod.ANNUAL);
    }
}
