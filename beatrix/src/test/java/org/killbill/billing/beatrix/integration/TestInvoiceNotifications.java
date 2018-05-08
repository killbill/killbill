/*
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

package org.killbill.billing.beatrix.integration;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestInvoiceNotifications extends TestIntegrationBase {

    @Override
    protected KillbillConfigSource getConfigSource() {
        ImmutableMap additionalProperties = new ImmutableMap.Builder()
                .put("org.killbill.invoice.dryRunNotificationSchedule", "7d")
                .build();
        return getConfigSource("/beatrix.properties", additionalProperties);
    }

    @Test(groups = "slow")
    public void testInvoiceNotificationBasic() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        // Move to end of trial =>  2012, 4, 24
        addDaysAndCheckForCompletion(23, NextEvent.INVOICE_NOTIFICATION);

        // Move to end of trial =>  2012, 5, 1
        addDaysAndCheckForCompletion(7, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Next invoice is scheduled for 2012, 6, 1 so we should have a NOTIFICATION event 7 days before, on 2012, 5, 25
        addDaysAndCheckForCompletion(24, NextEvent.INVOICE_NOTIFICATION);

        // And then verify the invoice is correctly generated
        addDaysAndCheckForCompletion(7, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
    }

    @Test(groups = "slow")
    public void testInvoiceNotificationWithFutureSubscriptionEvents() throws Exception {
        clock.setDay(new LocalDate(2018, 1, 31));

        final AccountData accountData = getAccountData(28);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final LocalDate billingDate = new LocalDate(2018, 2, 28);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial");

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, "bundleKey", null, null, billingDate, false, true, ImmutableList.<PluginProperty>of(), callContext);
        busHandler.assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Move to the notification before the start date =>  2018, 2, 21
        addDaysAndCheckForCompletion(21, NextEvent.INVOICE_NOTIFICATION);

        // Move to the start date => 2018, 2, 28
        addDaysAndCheckForCompletion(7, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        final LocalDate futureChangeDate = new LocalDate(2018, 3, 28);

        entitlement.changePlanWithDate(new PlanPhaseSpecifier("shotgun-monthly"), null, futureChangeDate, null, callContext);
        assertListenerStatus();

        // Move to the notification before the start date =>  2018, 3, 21
        addDaysAndCheckForCompletion(21, NextEvent.INVOICE_NOTIFICATION);

        // Move to the change date => 2018, 3, 28
        addDaysAndCheckForCompletion(7, NextEvent.CHANGE, NextEvent.INVOICE, NextEvent.NULL_INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
    }

    @Test(groups = "slow")
    public void testInvoiceNotificationInThePast() throws Exception {
        // We take april as it has 30 days (easier to play with BCD)
        // Set clock to the initial start date - we implicitly assume here that the account timezone is UTC
        clock.setDay(new LocalDate(2012, 4, 1));

        final AccountData accountData = getAccountData(1);
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        // Future create the entitlement
        // Note: we need to use a plan without a trial to verify the fix, because we don't send invoice notifications for $0 invoices
        // Also, even though we have the 7d dryRunNotificationSchedule configured, the system will not to set a dry run notification in the past (3/26/2012)
        final DefaultEntitlement bpSubscription = createBaseEntitlementWithPriceOverrideAndCheckForCompletion(account.getId(),
                                                                                                              "bundleKey",
                                                                                                              "Pistol",
                                                                                                              ProductCategory.BASE,
                                                                                                              BillingPeriod.MONTHLY,
                                                                                                              null,
                                                                                                              new LocalDate(2012, 4, 2),
                                                                                                              "notrial",
                                                                                                              NextEvent.BLOCK);

        // Move to subscription start date
        addDaysAndCheckForCompletion(1, NextEvent.CREATE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Move to notification date
        addDaysAndCheckForCompletion(23, NextEvent.INVOICE_NOTIFICATION);

        // Move to next invoice
        addDaysAndCheckForCompletion(7, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);

        // Move to notification date
        addDaysAndCheckForCompletion(24, NextEvent.INVOICE_NOTIFICATION);

        // Move to next invoice
        addDaysAndCheckForCompletion(7, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
    }
}
