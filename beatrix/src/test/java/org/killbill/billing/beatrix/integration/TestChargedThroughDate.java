/*
 * Copyright 2020-2024 Equinix, Inc
 * Copyright 2014-2024 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestChargedThroughDate extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/testChargedThroughDate");
        return super.getConfigSource(null, allExtraProperties);
    }

    @Test(groups = "slow", description="https://github.com/killbill/killbill/issues/1739")
    public void testFixedTermWithOneTimeCharges() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //FIXEDTERM phase of 3 MONTHS  with a fixed price, no recurring price
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-fixedterm-one-time-no-recurring");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2024-04-01 (end of FIXEDTERM phase)
        final Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 4, 1)), 0);
    }

    @Test(groups = "slow", description="https://github.com/killbill/killbill/issues/1739")
    public void testFixedTermWithOneTimeAndRecurringCharges() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //FIXEDTERM phase of 12 MONTHS with both fixed and recurring charges
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-fixedterm-one-time-and-recurring");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set 2024-02-01 (since recurring price is present, it is NOT set to end of the FIXEDTERM phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 2, 1)), 0);

        //Move clock by a month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        // CTD set to 2024-03-01
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 3, 1)), 0);

    }

    @Test(groups = "slow", description="https://github.com/killbill/killbill/issues/1739")
    public void testFixedTermWithFixedPriceOnlyAndEvergreen() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(1));

        //FIXEDTERM phase of 12 MONTHS with fixed price only (no recurring price) followed by EVERGREEN phase
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-fixedterm-no-recurring-and-evergreen");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2025-01-01 (end of FIXEDTERM phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2025, 1, 1)), 0);

        //move clock by a year
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addYears(1);
        assertListenerStatus();

        //CTD set to 2025-02-01 (as per recurring phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2025, 2, 1)), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1739")
    public void testTrialWithFixedPriceOnlyAndEvergreen() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        //10 DAY TRIAL phase with fixed price only (no recurring price) followed by EVERGREEN phase
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-trial-with-fixed-price-only-and-evergreen");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2024-01-11 (end of TRIAL phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 1, 11)), 0);

        //Move clock by 10 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(10);
        assertListenerStatus();

        //CTD set to 2024-02-11 (as per recurring phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 2, 11)), 0);

        //move clock by a month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        //CTD set to 2024-03-11 (as per recurring phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 3, 11)), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1739")
    public void testTrialWithNoFixedAndRecurringPriceAndEvergreen() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        //10 DAY TRIAL phase with no fixed/recurring price followed by EVERGREEN phase
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-trial-with-no-fixed-and-recurring-price-and-evergreen");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2024-01-11 (end of TRIAL phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 1, 11)), 0);

        //Move clock by 10 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(10);
        assertListenerStatus();

        //CTD set to 2024-02-11 (as per recurring phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 2, 11)), 0);

        //move clock by a month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        //CTD set to 2024-03-11 (as per recurring phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 3, 11)), 0);
    }


    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1739")
    public void testTrialAndFixedTermWithRecurringPrice() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        //10 DAY TRIAL phase with fixed price only (no recurring price) followed by a 3-MONTH FIXEDTERM phase with no fixed price and a MONTHLY recurring price
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-trial-and-fixedterm-with-recurring-price");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2024-01-11 (end of TRIAL phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 1, 11)), 0);

        //Move clock by 10 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(10);
        assertListenerStatus();

        //CTD set to 2024-02-11 (as per recurring price of FIXEDTERM phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 2, 11)), 0);

        //Move clock by a month
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addMonths(1);
        assertListenerStatus();

        //CTD set to 2024-03-11 (as per recurring price of FIXEDTERM phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 3, 11)), 0);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1739")
    public void testDiscountAndFixedTermWithFixedPrice() throws Exception {

        final LocalDate today = new LocalDate(2024, 1, 1);
        clock.setDay(today);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(11));

        //10 DAY DISCOUNT phase with fixed price only (no recurring price) followed by a 3-MONTH FIXEDTERM phase with a fixed price only (no recurring price)
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("p1-discount-and-fixedterm-with-fixed-price");
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), null, null, null, false, true, Collections.emptyList(), callContext);
        assertListenerStatus();

        //CTD set to 2024-01-11 (end of DISCOUNT phase)
        Subscription subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 1, 11)), 0);

        //Move clock by 10 days
        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        clock.addDays(10);
        assertListenerStatus();

        //CTD set to 2024-04-11 (end of FIXEDTERM phase)
        subscription = subscriptionApi.getSubscriptionForEntitlementId(entitlementId, false, callContext);
        Assert.assertEquals(subscription.getChargedThroughDate().compareTo(new LocalDate(2024, 4, 11)), 0);
    }

}
