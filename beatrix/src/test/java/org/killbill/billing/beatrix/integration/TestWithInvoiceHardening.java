/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.util.InvoiceChecker.ExpectedInvoiceItemCheck;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestWithInvoiceHardening extends TestIntegrationBase {

    @Inject
    protected InvoiceDao invoiceDao;

    @Test(groups = "slow")
    public void testBasicBlockUnblock() throws Exception {

        final DateTimeZone testTimeZone = DateTimeZone.UTC;

        final DateTime initialDate = new DateTime(2019, 4, 27, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 1, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27), InvoiceItemType.RECURRING, new BigDecimal("29.95")));

        // 2019-05-03
        clock.addDays(6);

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState1 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_BLOCK", "company.a.b.c", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 27), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-23.96")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 03), InvoiceItemType.CBA_ADJ, new BigDecimal("23.96")));

        final BlockingState blockingState2 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_UNBLOCK", "company.a.b.c", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);

        // 2019-05-17
        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        clock.addDays(14);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 27), InvoiceItemType.RECURRING, new BigDecimal("9.98")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("-9.98")));



         int remainingBlockUnblockIterations = 3;
        int curInvoiceNumber = 3;

        do {

            busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
            final BlockingState somethingBlockAgain = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_BLOCK_AGAIN", "company.a.b.c", true, true, true, null);
            subscriptionApi.addBlockingState(somethingBlockAgain, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);
            assertListenerStatus();

            // Block again on the same date
            invoiceChecker.checkInvoice(account.getId(), ++curInvoiceNumber, callContext,
                                        new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 27), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-9.98")),
                                        new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("9.98")));

            verifyNoInvoiceDueOnDate(account.getId(), new LocalDate(2019, 5, 27));
            verifyNoInvoiceDueOnDate(account.getId(), new LocalDate(2019, 6, 27));


            // Unblock again on the same date
            busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
            final BlockingState somethingUnblockAgain = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_UNBLOCK_AGAIN", "company.a.b.c", false, false, false, null);
            subscriptionApi.addBlockingState(somethingUnblockAgain, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);
            assertListenerStatus();

            invoiceChecker.checkInvoice(account.getId(), ++curInvoiceNumber, callContext,
                                        new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 27), InvoiceItemType.RECURRING, new BigDecimal("9.98")),
                                        new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("-9.98")));

        } while (remainingBlockUnblockIterations-- > 0);


    }


    @Test(groups = "slow")
    public void testBasicBlockUnblockWithBadData() throws Exception {

        final DateTimeZone testTimeZone = DateTimeZone.UTC;

        final DateTime initialDate = new DateTime(2019, 4, 27, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        // 2019-05-03
        clock.addDays(6);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState1 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_BLOCK", "company.a.b.c", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        final BlockingState blockingState2 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_UNBLOCK", "company.a.b.c", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState2, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);

        // 2019-05-17
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(14);
        assertListenerStatus();

        //
        // Simulate some bad data on disk
        //

        // Invoice 1: RECURRING 2019-4-27 -> 2019-5-27
        final InvoiceModelDao firstInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 4, 27), new LocalDate(2019, 4, 27), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        final UUID initialRecuringItemId = UUID.randomUUID();
        firstInvoice.addInvoiceItem(new InvoiceItemModelDao(initialRecuringItemId, clock.getUTCNow(), InvoiceItemType.RECURRING, firstInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27),
                                                            new BigDecimal("29.95"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        insertInvoiceItems(firstInvoice);


        // Invoice 2: REPAIR_ADJ 2019-5-3 -> 2019-5-27 => Simulate the BLOCK billing on 2019-5-3
        final InvoiceModelDao secondInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 3), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        secondInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, secondInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                             null, null, null, null,
                                                             new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 27),
                                                             new BigDecimal("-23.96"), null, account.getCurrency(), initialRecuringItemId, null, null));
        insertInvoiceItems(secondInvoice);


        // Invoice 3: REPAIR_ADJ 2019-4-27 -> 2019-5-03 => Simulate the UNBLOCK billing on 2019-5-3 and RECURRING 2019-4-27 -> 2019-5-27
        //            Original initialRecuringItemId is now fully repaired and we have re-invoiced for the full period
        //
        final InvoiceModelDao thirdInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 3), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        final UUID secondRecurringItemId = UUID.randomUUID();
        thirdInvoice.addInvoiceItem(new InvoiceItemModelDao(secondRecurringItemId, clock.getUTCNow(), InvoiceItemType.RECURRING, thirdInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27),
                                                            new BigDecimal("29.95"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        thirdInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, thirdInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            null, null, null, null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 03),
                                                            new BigDecimal("-5.99"), null, account.getCurrency(), initialRecuringItemId, null, null));
        insertInvoiceItems(thirdInvoice);


        // Invoice 4: REPAIR_ADJ 2019-5-17 -> 2019-5-27 => Simulate the BLOCK billing on 2019-5-17
        final InvoiceModelDao fourthInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);

        fourthInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, fourthInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                             null, null, null, null,
                                                             new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 27),
                                                             new BigDecimal("9.98"), null, account.getCurrency(), secondRecurringItemId, null, null));

        insertInvoiceItems(fourthInvoice);

        // Invoice 5: REPAIR_ADJ 2019-5-17 -> 2019-5-27 => Simulate the UNBLOCK billing on 2019-5-17

        final InvoiceModelDao fifthInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        fifthInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.RECURRING, fifthInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 3),
                                                            new BigDecimal("5.99"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        insertInvoiceItems(fifthInvoice);


        // Trigger IllegalStateException: Double billing detected
        remove_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);
    }


    private void insertInvoiceItems(final InvoiceModelDao invoice) {

        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();

        invoiceDao.createInvoice(invoice, ImmutableSet.<InvoiceTrackingModelDao>of(), callbackDateTimePerSubscriptions, internalCallContext);
    }

    private void verifyNoInvoiceDueOnDate(final UUID accountId, final LocalDate targetDate) {
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        try {

            invoiceUserApi.triggerInvoiceGeneration(accountId, targetDate, callContext);
            Assert.fail("Unexpected to generate a new invoice for date " + targetDate);
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
        assertListenerStatus();

    }


}
