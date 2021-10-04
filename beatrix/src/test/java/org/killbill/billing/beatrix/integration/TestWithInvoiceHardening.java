/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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
import java.util.List;
import java.util.UUID;

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
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceTrackingModelDao;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.tag.ControlTagType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import static org.killbill.billing.ErrorCode.INVOICE_NOTHING_TO_DO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestWithInvoiceHardening extends TestIntegrationBase {


    @Inject
    protected InvoiceDao invoiceDao;


    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1205")
    public void testFor1205() throws Exception {

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
        final BlockingState blockingState1 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_5_3", "SERVICE_5_3", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState1, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 2, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 27), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-23.96")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 03), InvoiceItemType.CBA_ADJ, new BigDecimal("23.96")));

        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState2 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_4_27", "SERVICE_4_27", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState2, new LocalDate(2019, 4, 27), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();

        invoiceChecker.checkInvoice(account.getId(), 3, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 03), InvoiceItemType.REPAIR_ADJ, new BigDecimal("-5.99")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 03), new LocalDate(2019, 5, 03), InvoiceItemType.CBA_ADJ, new BigDecimal("5.99")));

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState4 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "UNBLOCK_5_3", "SERVICE_5_3", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState4, new LocalDate(2019, 5, 3), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        // 2019-05-17
        clock.addDays(14);

        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        final BlockingState blockingState3 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "BLOCK_5_17", "SERVICE_5_17", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState3, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.BLOCK, NextEvent.INVOICE);
        final BlockingState blockingState5 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "UNBLOCK_4_27", "SERVICE_4_27", false, false, false, null);
        subscriptionApi.addBlockingState(blockingState5, new LocalDate(2019, 4, 27), ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();




        invoiceChecker.checkInvoice(account.getId(), 4, callContext,
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 17), InvoiceItemType.RECURRING, new BigDecimal("19.97")),
                                    new ExpectedInvoiceItemCheck(new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), InvoiceItemType.CBA_ADJ, new BigDecimal("-19.97")));


        // 2019-05-27
        busHandler.pushExpectedEvents(NextEvent.NULL_INVOICE);
        clock.addDays(10);
        assertListenerStatus();
    }

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

    private void deleteUsedCredit(final UUID accountId, final BigDecimal targetAmount) throws InvoiceApiException {
        final List<Invoice> allInvoices = invoiceUserApi.getInvoicesByAccount(accountId, false, false, callContext);
        final Iterable<InvoiceItem> allItems = Iterables.concat(Iterables.transform(allInvoices, new Function<Invoice, List<InvoiceItem>>() {

            @Override
            public List<InvoiceItem> apply(final Invoice input) {
                return input.getInvoiceItems();
            }
        }));
        final InvoiceItem usedCredit = Iterables.tryFind(allItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceItemType() == InvoiceItemType.CBA_ADJ &&
                       input.getAmount().compareTo(targetAmount) == 0;
            }
        }).orNull();
        Assert.assertNotNull(usedCredit);

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.deleteCBA(accountId, usedCredit.getInvoiceId(), usedCredit.getId(), callContext);
        assertListenerStatus();


    }

    //
    // Complicated scenario where we want to test that given some bad data
    //  1/ leading the account to be parked and
    //  2/ leading the system to have both generated and used some credit
    //  we are able using our 'void' and credit 'deletion api' to come back to a good state, i.e
    //  credit was reclaimed, and account is back into a good state
    //
    @Test(groups = "slow")
    public void testFixParkedAccountByVoidingInvoices() throws Exception {

        final DateTimeZone testTimeZone = DateTimeZone.UTC;

        final DateTime initialDate = new DateTime(2019, 4, 27, 0, 13, 42, 0, testTimeZone);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        add_AUTO_PAY_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        add_AUTO_INVOICING_OFF_Tag(account.getId(), ObjectType.ACCOUNT);

        busHandler.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Blowdart", BillingPeriod.MONTHLY, "notrial", null);
        UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), new DefaultEntitlementSpecifier(spec), "Something", null, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        assertListenerStatus();


        final BlockingState blockingState2 = new DefaultBlockingState(entitlementId, BlockingStateType.SUBSCRIPTION, "SOMETHING_BLOCK", "company.a.b.c", true, true, true, null);
        subscriptionApi.addBlockingState(blockingState2, new LocalDate(2019, 5, 17), ImmutableList.<PluginProperty>of(), callContext);

        // 2019-05-17
        busHandler.pushExpectedEvents(NextEvent.BLOCK);
        clock.addDays(20);
        assertListenerStatus();

        //
        // Simulate some bad data on disk
        //
        // Invoice 1: RECURRING 2019-4-27 -> 2019-5-27
        final InvoiceModelDao firstInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 4, 27), new LocalDate(2019, 4, 27), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        final UUID initialRecuringItemId = UUID.randomUUID();
        firstInvoice.addInvoiceItem(new InvoiceItemModelDao(initialRecuringItemId, clock.getUTCNow(), InvoiceItemType.RECURRING, firstInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null, null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27),
                                                            new BigDecimal("29.95"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        insertInvoiceItems(firstInvoice);

        // Invoice 2: REPAIR_ADJ 2019-5-3 -> 2019-5-27 => Simulate the BLOCK billing on 2019-5-3
        final InvoiceModelDao secondInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 3), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        secondInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, secondInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                             null, null, null, null, null,
                                                             new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 27),
                                                             new BigDecimal("-23.96"), null, account.getCurrency(), initialRecuringItemId, null, null));
        insertInvoiceItems(secondInvoice);

        // Invoice 3: REPAIR_ADJ 2019-4-27 -> 2019-5-03 => Simulate the UNBLOCK billing on 2019-5-3 and RECURRING 2019-4-27 -> 2019-5-27
        //            Original initialRecuringItemId is now fully repaired and we have re-invoiced for the full period
        //
        final InvoiceModelDao thirdInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 3), new LocalDate(2019, 5, 3), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        final UUID secondRecurringItemId = UUID.randomUUID();
        thirdInvoice.addInvoiceItem(new InvoiceItemModelDao(secondRecurringItemId, clock.getUTCNow(), InvoiceItemType.RECURRING, thirdInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null, null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 27),
                                                            new BigDecimal("29.95"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        thirdInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, thirdInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            null, null, null, null, null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 03),
                                                            new BigDecimal("-5.99"), null, account.getCurrency(), initialRecuringItemId, null, null));
        insertInvoiceItems(thirdInvoice);

        // Invoice 4: REPAIR_ADJ 2019-5-17 -> 2019-5-27 => Simulate the BLOCK billing on 2019-5-17
        final InvoiceModelDao fourthInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);

        fourthInvoice.addInvoiceItem(new InvoiceItemModelDao(UUID.randomUUID(), clock.getUTCNow(), InvoiceItemType.REPAIR_ADJ, fourthInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                             null, null, null, null, null,
                                                             new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 27),
                                                             new BigDecimal("-9.98"), null, account.getCurrency(), secondRecurringItemId, null, null));

        insertInvoiceItems(fourthInvoice);

        // Invoice 5: RECURRING 2019-4-17 -> 2019-5-17
        final InvoiceModelDao fifthInvoice = new InvoiceModelDao(UUID.randomUUID(), clock.getUTCNow(), account.getId(), null, new LocalDate(2019, 5, 17), new LocalDate(2019, 5, 17), account.getCurrency(), false, InvoiceStatus.COMMITTED, false);
        final UUID thirdRecurringItemId = UUID.randomUUID();
        fifthInvoice.addInvoiceItem(new InvoiceItemModelDao(thirdRecurringItemId, clock.getUTCNow(), InvoiceItemType.RECURRING, fifthInvoice.getId(), account.getId(), null, null, entitlementId, "",
                                                            "Blowdart", "blowdart-monthly-notrial", "blowdart-monthly-notrial-evergreen", null, null,
                                                            new LocalDate(2019, 4, 27), new LocalDate(2019, 5, 17),
                                                            new BigDecimal("5.99"), new BigDecimal("29.95"), account.getCurrency(), null, null, null));

        insertInvoiceItems(fifthInvoice);


        // Trigger IllegalStateException: Double billing detected
        busHandler.pushExpectedEvents(NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.INVOICE, NextEvent.TAG);
        tagUserApi.removeTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), callContext);
        busHandler.waitAndIgnoreEvents(3000);

        // <! end of setup>
        //
        // We need to first VOID invoices that have REPAIR items otherwsie VOID operation on invoices being repaired would fail
        // However, in order to VOID such REPAIR invoices for which (system) credit was generated, we need to ensure there is enough
        // credit available on the account, hence the step of manually reclaiming credit before each operation.
        //

        deleteUsedCredit(account.getId(), new BigDecimal("-23.96"));

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.voidInvoice(secondInvoice.getId(), callContext);
        assertListenerStatus();

        deleteUsedCredit(account.getId(), new BigDecimal("-5.99"));
        deleteUsedCredit(account.getId(), new BigDecimal("-3.99"));

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.voidInvoice(fourthInvoice.getId(), callContext);
        assertListenerStatus();

        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.voidInvoice(thirdInvoice.getId(), callContext);
        assertListenerStatus();


        busHandler.pushExpectedEvents(NextEvent.INVOICE_ADJUSTMENT);
        invoiceUserApi.voidInvoice(firstInvoice.getId(), callContext);
        assertListenerStatus();


        // This remove the __PARK__ tag and fixes the state !
        busHandler.pushExpectedEvents(NextEvent.TAG, NextEvent.NULL_INVOICE);
        try {
            invoiceUserApi.triggerInvoiceGeneration(account.getId(), new LocalDate(2019, 5, 17), callContext);
            fail();
        } catch (final InvoiceApiException e) {
            assertEquals(e.getCode(), INVOICE_NOTHING_TO_DO.getCode());
        } finally {
            assertListenerStatus();
        }
    }


    private void insertInvoiceItems(final InvoiceModelDao invoice) {
        final FutureAccountNotifications callbackDateTimePerSubscriptions = new FutureAccountNotifications();
        invoiceDao.createInvoice(invoice, null, ImmutableSet.<InvoiceTrackingModelDao>of(), callbackDateTimePerSubscriptions, null, internalCallContext);
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

