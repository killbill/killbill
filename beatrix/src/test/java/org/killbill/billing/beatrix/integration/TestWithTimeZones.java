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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalInstantException;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestWithTimeZones extends TestIntegrationBase {

    // Verify that recurring invoice items are correctly computed although we went through and out of daylight saving transitions
    @Test(groups = "slow")
    public void testWithDayLightSaving() throws Exception {
        // Start with a date in daylight saving period  and make sure we use a time of 8 hour so that we we reach standard time
        // the next month where the difference is 9 hours, a transformation from DateTime to LocalDate with the account time zone would bring us a day earlier
        clock.setTime(new DateTime("2015-09-01T08:01:01.000Z"));

        final DateTimeZone tz = DateTimeZone.forID("America/Juneau");
        final AccountData accountData = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                                                .firstNameLength(6)
                                                                .email(UUID.randomUUID().toString().substring(1, 8))
                                                                .phone(UUID.randomUUID().toString().substring(1, 8))
                                                                .migrated(false)
                                                                .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                                                .billingCycleDayLocal(1)
                                                                .currency(Currency.USD)
                                                                .paymentMethodId(UUID.randomUUID())
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();

        final TestDryRunArguments dryRun = new TestDryRunArguments(DryRunType.SUBSCRIPTION_ACTION, "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, null, null,
                                                                   SubscriptionEventType.START_BILLING, null, null, null, null);
        final Invoice dryRunInvoice = invoiceUserApi.triggerDryRunInvoiceGeneration(account.getId(), clock.getUTCToday(), dryRun, callContext);
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2015, 9, 1), null, InvoiceItemType.FIXED, new BigDecimal("0")));
        invoiceChecker.checkInvoiceNoAudits(dryRunInvoice, expectedInvoices);

        final DefaultEntitlement bpSubscription = createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        // Check bundle after BP got created otherwise we get an error from auditApi.
        subscriptionChecker.checkSubscriptionCreated(bpSubscription.getId(), internalCallContext);
        invoiceChecker.checkInvoice(account.getId(), 1, callContext, expectedInvoices);
        expectedInvoices.clear();

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();
        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2015, 10, 1), new LocalDate(2015, 11, 1), InvoiceItemType.RECURRING, new BigDecimal("249.95")));

        LocalDate startDate = new LocalDate(2015, 11, 1);
        // We loop 18 times to go over a year and transitions several times between winter and summer (daylight saving)
        for (int i = 0; i < 18; i++) {
            final LocalDate endDate = startDate.plusMonths(1);

            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addMonths(1);
            assertListenerStatus();
            invoiceChecker.checkInvoice(account.getId(), i + 3, callContext,
                                        new ExpectedInvoiceItemCheck(startDate, endDate, InvoiceItemType.RECURRING, new BigDecimal("249.95")));

            startDate = endDate;
        }
    }

    // Verify cancellation logic when we exit daylight saving period
    @Test(groups = "slow")
    public void testCancellationFrom_PDT_to_PST() throws Exception {
        // Start with a date in daylight saving period (PDT) and make sure we use a time of 7 hour so that we we reach standard time (PST)
        // the next month where the difference is 8 hours, a transformation from DateTime to LocalDate with the account time zone would bring us a day earlier
        // (e.g new LocalDate("2015-12-01T07:01:01.000Z", tz) -> "2015-11-30.
        clock.setTime(new DateTime("2015-11-01T07:01:01.000Z"));

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final AccountData accountData = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                                                .firstNameLength(6)
                                                                .email(UUID.randomUUID().toString().substring(1, 8))
                                                                .phone(UUID.randomUUID().toString().substring(1, 8))
                                                                .migrated(false)
                                                                .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                                                .billingCycleDayLocal(1)
                                                                .currency(Currency.USD)
                                                                .paymentMethodId(UUID.randomUUID())
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Cancel the next month specifying just a LocalDate
        final LocalDate cancellationDate = new LocalDate("2015-12-01", tz);
        entitlement = entitlement.cancelEntitlementWithDate(cancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // Verify first entitlement is correctly cancelled on the right date
        Assert.assertEquals(entitlement.getEffectiveEndDate(), cancellationDate);

        // We now move the clock to the date of the cancellation, which match the cancellation day from the client point of view
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE, NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE);
        clock.setTime(new DateTime("2015-12-01T07:01:02Z"));
        assertListenerStatus();

        // Verify second that there was no repair (so the cancellation did correctly happen on the "2015-12-01")
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        Assert.assertEquals(invoices.size(), 1);
    }

    // Same test as previous test but this time going from PST -> PDT (somehow not too interesting in that direction because we start with
    // an offset of 8 hours and then go through 7 hours so anyway we would stay in the same day.
    @Test(groups = "slow")
    public void testCancellationFrom_PST_to_PDT() throws Exception {
        clock.setTime(new DateTime("2015-02-01T08:01:01.000Z"));

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final AccountData accountData = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                                                .firstNameLength(6)
                                                                .email(UUID.randomUUID().toString().substring(1, 8))
                                                                .phone(UUID.randomUUID().toString().substring(1, 8))
                                                                .migrated(false)
                                                                .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                                                .billingCycleDayLocal(1)
                                                                .currency(Currency.USD)
                                                                .paymentMethodId(UUID.randomUUID())
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        // Cancel the next month specifying just a LocalDate
        final LocalDate cancellationDate = new LocalDate("2015-03-01", tz);
        entitlement = entitlement.cancelEntitlementWithDate(cancellationDate, true, ImmutableList.<PluginProperty>of(), callContext);

        // Verify first entitlement is correctly cancelled on the right date
        Assert.assertEquals(entitlement.getEffectiveEndDate(), cancellationDate);

        // We now move the clock to the date of the cancellation  which match the cancellation day from the client point of view
        busHandler.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK, NextEvent.NULL_INVOICE, NextEvent.NULL_INVOICE);
        clock.setTime(new DateTime("2015-03-01T08:01:02"));
        assertListenerStatus();

        // Verify second that there was no repair (so the cancellation did correctly happen on the "2015-12-01"
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(account.getId(), false, false, callContext);
        Assert.assertEquals(invoices.size(), 1);
    }

    @Test(groups = "slow")
    public void testReferenceTimeInDSTGap() throws Exception {
        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        clock.setTime(new DateTime(2015, 3, 7, 2, 0, 0, tz));

        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);
        accountChecker.checkAccount(account.getId(), accountData, callContext);
        Assert.assertEquals(account.getTimeZone(), tz);
        Assert.assertEquals(account.getFixedOffsetTimeZone(), DateTimeZone.forOffsetHours(-8));

        // Note the gap: 2015-03-07T02:00:00.000-08:00 to 2015-03-08T03:00:00.000-07:00
        clock.addDays(1);

        try {
            // See TimeAwareContext#toUTCDateTime (which uses account.getFixedOffsetTimeZone() instead)
            new DateTime(clock.getUTCToday().getYear(),
                         clock.getUTCToday().getMonthOfYear(),
                         clock.getUTCToday().getDayOfMonth(),
                         account.getReferenceTime().toDateTime(tz).getHourOfDay(),
                         account.getReferenceTime().toDateTime(tz).getMinuteOfHour(),
                         account.getReferenceTime().toDateTime(tz).getSecondOfMinute(),
                         account.getTimeZone());
            Assert.fail();
        } catch (final IllegalInstantException e) {
            // Illegal instant due to time zone offset transition (daylight savings time 'gap'): 2015-03-08T10:00:00.000 (America/Los_Angeles)
        }

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        // Pass a date of today, to trigger TimeAwareContext#toUTCDateTime
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(),  new DefaultEntitlementSpecifier(spec), "Something", clock.getUTCToday(), clock.getUTCToday(), false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);

        Assert.assertEquals(entitlement.getEffectiveStartDate().compareTo(new LocalDate("2015-03-08")), 0);
        Assert.assertEquals(((DefaultEntitlement) entitlement).getBasePlanSubscriptionBase().getStartDate().compareTo(new DateTime("2015-03-08T02:00:00.000-08:00")), 0);

        invoiceChecker.checkChargedThroughDate(entitlement.getId(), new LocalDate("2015-04-08"), callContext);

        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(31);
        assertListenerStatus();

        invoiceChecker.checkChargedThroughDate(entitlement.getId(), new LocalDate("2015-05-08"), callContext);

        for (int i = 0; i < 25 ; i++) {
            busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
            clock.addMonths(1);
            assertListenerStatus();

            invoiceChecker.checkChargedThroughDate(entitlement.getId(), new LocalDate("2015-03-08").plusMonths(3 + i), callContext);
        }
    }


    @Test(groups = "slow")
    public void testIntoDaylightSavingTransition() throws Exception {

        // Daylight saving happened on March 12th.
        //
        // Because we use 30 days trial, we start a bit before and that way we can check that computation of BCD crossing into daylight saving works as expected.
        //
        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        clock.setTime(new DateTime(2017, 3, 1, 23, 30, 0, tz));

        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY,  NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final Account accountWithBCD = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(accountWithBCD.getBillCycleDayLocal().intValue(), 31);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 3, 31), new LocalDate(2017, 4, 30), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();
    }




    @Test(groups = "slow")
    public void testIntoDaylightSavingTransition2() throws Exception {

        //
        // Nov 6th Transition date from DST -> ST
        //
        // Because we use 30 days trial, we start a bit before and that way we can check that computation of BCD crossing into daylight saving works as expected.
        //
        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        clock.setTime(new DateTime(2016, 11, 5, 23, 30, 0, tz));

        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(tz)
                                                                .build();

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        clock.setTime(new DateTime(2017, 3, 1, 23, 30, 0, tz));

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY,  NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final Account accountWithBCD = accountUserApi.getAccountById(account.getId(), callContext);
        //
        // Ok, it's a little bit tricky here:
        //
        // Intuitively we would expect a BCD of 31 (same as previous test testIntoDaylightSavingTransition1), however our implementation relies on TimeAwareContext for all TZ
        // computation. This context stores an offset from UTC which is then used as a reference and because we created an account before Nov 6 (DST), the offest is (-7)
        // so the result is different than previous test (where offset was -8)
        // What 's important is the fact that BCD and invoiceDate align in such a way that:
        // 1. we see no leading pro-ration
        // 2. Invoice is correctly generated (don't miss it because too early or don't invoice loop)
        //
        assertEquals(accountWithBCD.getBillCycleDayLocal().intValue(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 4, 1), new LocalDate(2017, 5, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();
    }


    @Test(groups = "slow")
    public void testIntoDaylightSavingTransition3() throws Exception {

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        clock.setTime(new DateTime(2017, 3, 1, 23, 30, 0, tz));


        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .timeZone(tz)
                                                                .referenceTime(clock.getUTCNow())
                                                                .build();

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("pistol-monthly-notrial",null);
        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.INVOICE_PAYMENT, NextEvent.PAYMENT);
        entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "bundleExternalKey", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        // Technically, we should see the invoice at the end of day in the account timezone, but this is not the case, the invoice comes slightly after (30')
        // clock.setTime(new DateTime(2017, 4, 1, 23, 59, 0, tz));
        //
        // But this is really not very important as long as invoicing is correct
        clock.addMonths(1);
        // Add one hour to hit the notification date of 2017-04-02T07:30:00.000Z
        clock.addDeltaFromReality(3600 * 1000);
        assertListenerStatus();
    }



    @Test(groups = "slow")
    public void testOutOfDaylightSavingTransition1() throws Exception {

        // Transition out of daylight saving is set for Nov 5
        //
        // Because we use 30 days trial, we start a bit before and that way we can check that computation of BCD crossing out of of daylight saving works as expected.
        //
        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        clock.setTime(new DateTime(2017, 11, 1, 00, 30, 0, tz));

        final AccountData accountData = new MockAccountBuilder().currency(Currency.USD)
                                                                .timeZone(tz)
                                                                .referenceTime(clock.getUTCNow())
                                                                .build();

        // Create account with non BCD to force junction BCD logic to activate
        final Account account = createAccountWithNonOsgiPaymentMethod(accountData);

        createBaseEntitlementAndCheckForCompletion(account.getId(), "bundleKey", "Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY,  NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);

        final Account accountWithBCD = accountUserApi.getAccountById(account.getId(), callContext);
        assertEquals(accountWithBCD.getBillCycleDayLocal().intValue(), 1);

        busHandler.pushExpectedEvents(NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        clock.addDays(30);
        assertListenerStatus();


        final List<ExpectedInvoiceItemCheck> expectedInvoices = new ArrayList<ExpectedInvoiceItemCheck>();
        expectedInvoices.add(new ExpectedInvoiceItemCheck(new LocalDate(2017, 12, 1), new LocalDate(2018, 1, 1), InvoiceItemType.RECURRING, new BigDecimal("29.95")));
        invoiceChecker.checkInvoice(account.getId(), 2, callContext, expectedInvoices);
        expectedInvoices.clear();
    }

}
