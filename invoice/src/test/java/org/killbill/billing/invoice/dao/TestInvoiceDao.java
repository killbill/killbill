/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
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

package org.killbill.billing.invoice.dao;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.MockBillingEventSet;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentStatus;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.generator.InvoiceWithMetadata;
import org.killbill.billing.invoice.model.CreditAdjInvoiceItem;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.DefaultInvoicePayment;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.ItemAdjInvoiceItem;
import org.killbill.billing.invoice.model.ParentInvoiceItem;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;
import org.killbill.billing.invoice.model.RepairAdjInvoiceItem;
import org.killbill.billing.invoice.optimizer.InvoiceOptimizerBase.AccountInvoices;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.SqlOperator;
import org.killbill.clock.ClockMock;
import org.killbill.commons.utils.collect.Iterables;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.killbill.billing.invoice.TestInvoiceHelper.FIVE;
import static org.killbill.billing.invoice.TestInvoiceHelper.TEN;
import static org.killbill.billing.invoice.TestInvoiceHelper.TWENTY;
import static org.killbill.billing.invoice.TestInvoiceHelper.ZERO;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestInvoiceDao extends InvoiceTestSuiteWithEmbeddedDB {

    private Account account;
    private InternalCallContext context;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        if (hasFailed()) {
            return;
        }
        account = invoiceUtil.createAccount(callContext);
        context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
    }

    @Test(groups = "slow")
    public void testSimple() throws Exception {
        final UUID accountId = account.getId();
        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        final InvoiceModelDao retrievedInvoice = invoiceDao.getById(invoice.getId(), context);
        invoiceUtil.checkInvoicesEqual(retrievedInvoice, invoice);
        invoiceUtil.checkInvoicesEqual(invoiceDao.getByNumber(retrievedInvoice.getInvoiceNumber(), true, context), invoice);
    }

    @Test(groups = "slow")
    public void testSimpleInvoiceRun() throws Exception {
        final UUID accountId = account.getId();

        final InvoiceModelDao invoiceForExternalCharge = new InvoiceModelDao(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false);
        final InvoiceItemModelDao externalCharge1 = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(invoiceForExternalCharge.getId(), accountId, UUID.randomUUID(), UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("15.0"), Currency.USD, null));
        final InvoiceItemModelDao externalCharge2 = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(invoiceForExternalCharge.getId(), accountId, UUID.randomUUID(), UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("17.0"), Currency.USD, null));
        invoiceForExternalCharge.addInvoiceItem(externalCharge1);
        invoiceForExternalCharge.addInvoiceItem(externalCharge2);
        invoiceDao.createInvoices(Collections.singleton(invoiceForExternalCharge),
                                 null,
                                 Collections.emptySet(),
                                 new FutureAccountNotifications(),
                                 new ExistingInvoiceMetadata(Collections.emptyList()),
                                 false,
                                 context);

        final Invoice invoice = invoiceUserApi.getInvoice(invoiceForExternalCharge.getId(), callContext);
        invoiceUtil.checkInvoicesEqual(invoiceForExternalCharge, invoice);
    }

    @Test(groups = "slow")
    public void testWithInvoiceGroup() throws Exception {
        final UUID accountId = account.getId();

        final InvoiceModelDao invoice1 = new InvoiceModelDao(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false);
        final InvoiceItemModelDao item1 = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(invoice1.getId(), accountId, UUID.randomUUID(), UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("15.0"), Currency.USD, null));
        invoice1.addInvoiceItem(item1);


        final InvoiceModelDao invoice2 = new InvoiceModelDao(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false);
        final InvoiceItemModelDao item2 = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(invoice2.getId(), accountId, UUID.randomUUID(), UUID.randomUUID().toString(), clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("17.0"), Currency.USD, null));
        invoice2.addInvoiceItem(item2);

        final List<InvoiceModelDao> invoices = new ArrayList<>();
        invoices.add(invoice1);
        invoices.add(invoice2);

        invoiceDao.createInvoices(invoices,
                                  null,
                                  Collections.emptySet(),
                                  new FutureAccountNotifications(),
                                  new ExistingInvoiceMetadata(Collections.emptyList()),
                                  false,
                                  context);

        // We know that groupId will be the ID of the first invoice
        final UUID groupId = invoice1.getId();
        final List<Invoice> result = invoiceUserApi.getInvoicesByGroup(accountId, groupId, callContext);
        assertEquals(result.size(), 2);
    }

    // Return persisted invoice
    private Invoice createAndGetInvoice(final LocalDate invoiceDate, final LocalDate targetDate) throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final Invoice invoice = new DefaultInvoice(accountId, invoiceDate, targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        return invoice;
    }

    // Return persisted invoice
    private Invoice createAndGetInvoiceWithInvoiceItem(final LocalDate invoiceDate, final LocalDate targetDate, final BigDecimal amount) throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final Invoice invoice = new DefaultInvoice(accountId, invoiceDate, targetDate, Currency.USD);
        final RecurringInvoiceItem recurringItem1 = new RecurringInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, invoiceDate, invoiceDate,
                                                                             amount, BigDecimal.ONE, Currency.USD);
        invoice.addInvoiceItem(recurringItem1);
        invoiceUtil.createInvoice(invoice, context);
        return invoice;
    }

    @Test(groups = "slow")
    public void testRetrieveInvoicesByAccount() throws EntityPersistenceException {

        //with invoice components
        final BigDecimal amount = BigDecimal.TEN;
        final Invoice createdInvoice = createAndGetInvoiceWithInvoiceItem(clock.getUTCToday(), clock.getUTCToday(), amount);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(false, true, context);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        InvoiceModelDao thisInvoice = invoices.get(0);
        assertEquals(createdInvoice.getAccountId(), account.getId());
        assertEquals(thisInvoice.getInvoiceDate().compareTo(createdInvoice.getInvoiceDate()), 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getInvoiceItems().size(), 1);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(thisInvoice).compareTo(amount), 0);

        //without invoice components
        invoices = invoiceDao.getInvoicesByAccount(false, false, context);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        thisInvoice = invoices.get(0);
        assertEquals(createdInvoice.getAccountId(), account.getId());
        assertEquals(thisInvoice.getInvoiceDate().compareTo(createdInvoice.getInvoiceDate()), 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getInvoiceItems().size(), 0);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(thisInvoice).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testRetrieveAllInvoicesByAccount() throws EntityPersistenceException {
        //with invoice components
        final BigDecimal amount = BigDecimal.TEN;
        final Invoice createdInvoice = createAndGetInvoiceWithInvoiceItem(clock.getUTCToday(), clock.getUTCToday(), amount);

        List<InvoiceModelDao> invoices = invoiceDao.getAllInvoicesByAccount(false, true, context);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        InvoiceModelDao thisInvoice = invoices.get(0);
        assertEquals(createdInvoice.getAccountId(), account.getId());
        assertEquals(thisInvoice.getInvoiceDate().compareTo(createdInvoice.getInvoiceDate()), 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getInvoiceItems().size(), 1);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(thisInvoice).compareTo(amount), 0);

        //without invoice components
        invoices = invoiceDao.getAllInvoicesByAccount(false, false, context);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        thisInvoice = invoices.get(0);
        assertEquals(createdInvoice.getAccountId(), account.getId());
        assertEquals(thisInvoice.getInvoiceDate().compareTo(createdInvoice.getInvoiceDate()), 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getInvoiceItems().size(), 0);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(thisInvoice).compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testGetInvoicesByAccountSorted() throws EntityPersistenceException {
        final Invoice invoice1 = createAndGetInvoice(clock.getUTCToday(), clock.getUTCToday());
        final Invoice invoice2 = createAndGetInvoice(clock.getUTCToday().minusDays(1), clock.getUTCToday().minusDays(1));
        final Invoice invoice3 = createAndGetInvoice(clock.getUTCToday().minusDays(2), clock.getUTCToday().minusDays(2));
        final Invoice invoice4 = createAndGetInvoice(clock.getUTCToday().minusDays(4), clock.getUTCToday().minusDays(4));
        // although labeled "invoice5", the clock use "minusDays(3)", so in assertion, this should be in 2nd element.
        final Invoice invoice5 = createAndGetInvoice(clock.getUTCToday().minusDays(3), clock.getUTCToday().minusDays(3));

        final List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(false, true, context);

        assertNotNull(invoices);
        assertEquals(invoices.size(), 5);

        assertEquals(invoice4.getId(), invoices.get(0).getId());
        assertEquals(invoice4.getTargetDate().compareTo(invoices.get(0).getTargetDate()), 0);

        assertEquals(invoice5.getId(), invoices.get(1).getId());
        assertEquals(invoice5.getTargetDate().compareTo(invoices.get(1).getTargetDate()), 0);

        assertEquals(invoice3.getId(), invoices.get(2).getId());
        assertEquals(invoice3.getTargetDate().compareTo(invoices.get(2).getTargetDate()), 0);

        assertEquals(invoice2.getId(), invoices.get(3).getId());
        assertEquals(invoice2.getTargetDate().compareTo(invoices.get(3).getTargetDate()), 0);

        assertEquals(invoice1.getId(), invoices.get(4).getId());
        assertEquals(invoice1.getTargetDate().compareTo(invoices.get(4).getTargetDate()), 0);
    }

    @Test(groups = "slow")
    public void testInvoicePayment() throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final UUID invoiceId = invoice.getId();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2010, 1, 1);
        final LocalDate endDate = new LocalDate(2010, 4, 1);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test product", "test plan", "test phase", null, startDate, endDate,
                                                                 new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);

        invoice.addInvoiceItem(invoiceItem);
        invoiceUtil.createInvoice(invoice, context);

        final InvoiceModelDao savedInvoice = invoiceDao.getById(invoiceId, context);
        assertNotNull(savedInvoice);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(savedInvoice).compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getInvoiceItems().size(), 1);

        final BigDecimal paymentAmount = new BigDecimal("11.00");
        final UUID paymentId = UUID.randomUUID();

        final DefaultInvoicePayment defaultInvoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, clock.getUTCNow().plusDays(12), paymentAmount, Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.SUCCESS);
        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment), UUID.randomUUID(), context);

        final InvoiceModelDao retrievedInvoice = invoiceDao.getById(invoiceId, context);
        assertNotNull(retrievedInvoice);
        assertEquals(retrievedInvoice.getInvoiceItems().size(), 1);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(retrievedInvoice).compareTo(new BigDecimal("10.00")), 0);
    }

    @Test(groups = "slow")
    public void testRetrievalForNonExistentInvoiceOrInvoiceItem() throws InvoiceApiException {
        try {
            invoiceDao.getById(UUID.randomUUID(), context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_NOT_FOUND.getCode());
        }

        try {
            invoiceDao.getByNumber(null, true, context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_INVALID_NUMBER.getCode());
        }

        try {
            invoiceDao.getByNumber(Integer.MIN_VALUE, true, context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_NUMBER_NOT_FOUND.getCode());
        }

        try {
            invoiceDao.getChargebackById(UUID.randomUUID(), context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CHARGE_BACK_DOES_NOT_EXIST.getCode());
        }

        try {
            invoiceDao.getExternalChargeById(UUID.randomUUID(), context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ITEM_NOT_FOUND.getCode());
        }

        try {
            invoiceDao.getCreditById(UUID.randomUUID(), context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_ITEM_NOT_FOUND.getCode());
        }
    }

    @Test(groups = "slow")
    public void testCreateRefundOnNonExistingPayment() throws Exception {
        try {
            invoiceDao.createRefund(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, false, Collections.emptyMap(), null, InvoicePaymentStatus.SUCCESS, context);
            Assert.fail();
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_PAYMENT_BY_ATTEMPT_NOT_FOUND.getCode());
        }
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForRecurringItems() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test product", "test plan", "test A", null, startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test product", "test plan", "test B", null, startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        final RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test product", "test plan", "test C", null, startDate, endDate,
                                                                    rate3, rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(item3, context);

        final RecurringInvoiceItem item4 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test product", "test plan", "test D", null, startDate, endDate,
                                                                    rate4, rate4, Currency.USD);
        invoiceUtil.createInvoiceItem(item4, context);

        // Create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem item5 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test product", "test plan", "test phase A", null, startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item5, context);

        final RecurringInvoiceItem item6 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test product", "test plan", "test phase B", null, startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item6, context);

        final RecurringInvoiceItem item7 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test product", "test plan", "test phase C", null, startDate, endDate,
                                                                    rate3, rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(item7, context);

        // Check that each subscription returns the correct number of invoices
        final List<InvoiceModelDao> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, context);
        assertEquals(items1.size(), 2);

        final List<InvoiceModelDao> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, context);
        assertEquals(items2.size(), 2);

        final List<InvoiceModelDao> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, context);
        assertEquals(items3.size(), 2);

        final List<InvoiceModelDao> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, context);
        assertEquals(items4.size(), 1);
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForFixedItems() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test product", "test plan", "test A", null, startDate,
                                                                      rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final FixedPriceInvoiceItem item2 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test product", "test plan", "test B", null, startDate,
                                                                      rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        final FixedPriceInvoiceItem item3 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test product", "test plan", "test C", null, startDate,
                                                                      rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(item3, context);

        final FixedPriceInvoiceItem item4 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test product", "test plan", "test D", null, startDate,
                                                                      rate4, Currency.USD);
        invoiceUtil.createInvoiceItem(item4, context);

        // create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;

        final FixedPriceInvoiceItem item5 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test product", "test plan", "test phase A", null, startDate,
                                                                      rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item5, context);

        final FixedPriceInvoiceItem item6 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test product", "test plan", "test phase B", null, startDate,
                                                                      rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item6, context);

        final FixedPriceInvoiceItem item7 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test product", "test plan", "test phase C", null, startDate,
                                                                      rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(item7, context);

        // check that each subscription returns the correct number of invoices
        final List<InvoiceModelDao> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, context);
        assertEquals(items1.size(), 2);

        final List<InvoiceModelDao> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, context);
        assertEquals(items2.size(), 2);

        final List<InvoiceModelDao> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, context);
        assertEquals(items3.size(), 2);

        final List<InvoiceModelDao> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, context);
        assertEquals(items4.size(), 1);
    }

    @Test(groups = "slow")
    public void testGetInvoicesBySubscriptionForRecurringAndFixedItems() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        final UUID subscriptionId1 = UUID.randomUUID();
        final BigDecimal rate1 = new BigDecimal("17.0");
        final UUID subscriptionId2 = UUID.randomUUID();
        final BigDecimal rate2 = new BigDecimal("42.0");
        final UUID subscriptionId3 = UUID.randomUUID();
        final BigDecimal rate3 = new BigDecimal("3.0");
        final UUID subscriptionId4 = UUID.randomUUID();
        final BigDecimal rate4 = new BigDecimal("12.0");

        final LocalDate targetDate = new LocalDate(2011, 5, 23);

        // Create invoice 1 (subscriptions 1-4)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem recurringItem1 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test product", "test plan", "test A", null, startDate, endDate,
                                                                             rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem1, context);

        final RecurringInvoiceItem recurringItem2 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test product", "test plan", "test B", null, startDate, endDate,
                                                                             rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem2, context);

        final RecurringInvoiceItem recurringItem3 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test product", "test plan", "test C", null, startDate, endDate,
                                                                             rate3, rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem3, context);

        final RecurringInvoiceItem recurringItem4 = new RecurringInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test product", "test plan", "test D", null, startDate, endDate,
                                                                             rate4, rate4, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem4, context);

        final FixedPriceInvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId1, "test product", "test plan", "test A", null, startDate,
                                                                           rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem1, context);

        final FixedPriceInvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId2, "test product", "test plan", "test B", null, startDate,
                                                                           rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem2, context);

        final FixedPriceInvoiceItem fixedItem3 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId3, "test product", "test plan", "test C", null, startDate,
                                                                           rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem3, context);

        final FixedPriceInvoiceItem fixedItem4 = new FixedPriceInvoiceItem(invoiceId1, accountId, bundleId, subscriptionId4, "test product", "test plan", "test D", null, startDate,
                                                                           rate4, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem4, context);

        // create invoice 2 (subscriptions 1-3)
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        final UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem recurringItem5 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test product", "test plan", "test phase A", null, startDate, endDate,
                                                                             rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem5, context);

        final RecurringInvoiceItem recurringItem6 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test product", "test plan", "test phase B", null, startDate, endDate,
                                                                             rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem6, context);

        final RecurringInvoiceItem recurringItem7 = new RecurringInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test product", "test plan", "test phase C", null, startDate, endDate,
                                                                             rate3, rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem7, context);
        final FixedPriceInvoiceItem fixedItem5 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId1, "test product", "test plan", "test phase A", null, startDate,
                                                                           rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem5, context);

        final FixedPriceInvoiceItem fixedItem6 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId2, "test product", "test plan", "test phase B", null, startDate,
                                                                           rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem6, context);

        final FixedPriceInvoiceItem fixedItem7 = new FixedPriceInvoiceItem(invoiceId2, accountId, bundleId, subscriptionId3, "test product", "test plan", "test phase C", null, startDate,
                                                                           rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedItem7, context);

        // check that each subscription returns the correct number of invoices
        final List<InvoiceModelDao> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1, context);
        assertEquals(items1.size(), 4);

        final List<InvoiceModelDao> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2, context);
        assertEquals(items2.size(), 4);

        final List<InvoiceModelDao> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3, context);
        assertEquals(items3.size(), 4);

        final List<InvoiceModelDao> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4, context);
        assertEquals(items4.size(), 2);
    }

    @Test(groups = "slow")
    public void testGetInvoicesForAccountAfterDate() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate targetDate2 = new LocalDate(2011, 12, 6);
        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate2, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        List<InvoiceModelDao> invoices;
        invoices = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 1, 1), null, true, context);
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 10, 6), null, true, context);
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 10, 11), null, true, context);
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 12, 6), null, true, context);
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(false, new LocalDate(2012, 1, 1), null, true, context);
        assertEquals(invoices.size(), 0);
    }

    @Test(groups = "slow")
    public void testGetInvoicesForAccountBeforeDate() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate targetDate2 = new LocalDate(2011, 12, 6);
        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate2, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        List<InvoiceModelDao> invoices;
        invoices = invoiceDao.getInvoicesByAccount(false, null, new LocalDate(2011, 12, 7), true, context);
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(false, null, new LocalDate(2011, 12, 5), true, context);
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(false, null, new LocalDate(2011, 10, 5), true, context);
        assertEquals(invoices.size(), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalance() throws EntityPersistenceException, IOException {
        Pagination<InvoiceModelDao> searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 0L);
        searchResult.close();

        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.GT, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 0L);
        searchResult.close();

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                    endDate, rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.GT, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("59.0"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final BigDecimal payment1 = new BigDecimal("48.0");
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);

        searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("59.0"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 0L);
        searchResult.close();

        searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("59.0"), SqlOperator.LT, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("11.0"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(rate1.add(rate2).subtract(payment1)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithCredit() throws EntityPersistenceException, IOException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        Pagination<InvoiceModelDao> searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("17.0"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final CreditAdjInvoiceItem creditItem = new CreditAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), null, rate1.negate(), Currency.USD, null);
        invoiceUtil.createInvoiceItem(creditItem, context);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithNoPayments() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(rate1.add(rate2)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithNoInvoiceItems() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final BigDecimal payment1 = new BigDecimal("48.0");
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);

        final BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(BigDecimal.ZERO.subtract(payment1)), 0);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithRefundNoAdj() throws InvoiceApiException, EntityPersistenceException, IOException {
        testAccountBalanceWithRefundInternal(false);
    }

    private void testAccountBalanceWithRefundInternal(final boolean withAdjustment) throws InvoiceApiException, EntityPersistenceException, IOException {

        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal refund1 = new BigDecimal("7.00");

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);
        BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("20.00")), 0);

        Pagination<InvoiceModelDao> searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("20.00"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = rate1;
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        invoiceDao.createRefund(paymentId, UUID.randomUUID(), refund1, withAdjustment, Collections.emptyMap(), UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        if (withAdjustment) {
            assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
            searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
            assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
            searchResult.close();
        } else {
            assertEquals(balance.compareTo(new BigDecimal("7.00")), 0);
            searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("7.00"), SqlOperator.EQ, 0L, 5L, context);
            assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
            searchResult.close();
        }
    }

    @Test(groups = "slow")
    public void testFullRefundWithRepairAndInvoiceItemAdjustment() throws InvoiceApiException, EntityPersistenceException, IOException {
        final BigDecimal refundAmount = new BigDecimal("20.00");
        testRefundWithRepairAndInvoiceItemAdjustmentInternal(refundAmount);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithRepairAndInvoiceItemAdjustment() throws InvoiceApiException, EntityPersistenceException, IOException {
        final BigDecimal refundAmount = new BigDecimal("7.00");
        testRefundWithRepairAndInvoiceItemAdjustmentInternal(refundAmount);
    }

    private void testRefundWithRepairAndInvoiceItemAdjustmentInternal(final BigDecimal refundAmount) throws InvoiceApiException, EntityPersistenceException, IOException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount = new BigDecimal("20.0");

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                    endDate, amount, amount, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);
        BigDecimal accountBalance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(accountBalance.compareTo(new BigDecimal("20.00")), 0);

        Pagination<InvoiceModelDao> searchResult = invoiceDao.searchInvoicesByBalance(new BigDecimal("20.00"), SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = amount;
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);
        accountBalance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(accountBalance.compareTo(new BigDecimal("0.00")), 0);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        // Repair the item (And add CBA item that should be generated)
        final InvoiceItem repairItem = new RepairAdjInvoiceItem(invoice.getId(), accountId, startDate, endDate, amount.negate(), Currency.USD, item2.getId());
        invoiceUtil.createInvoiceItem(repairItem, context);

        final InvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice.getId(), accountId, startDate, amount, Currency.USD);
        invoiceUtil.createInvoiceItem(cbaItem, context);

        // Here, the balance is paid and the account has a credit (account balance: -20)
        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final Map<UUID, BigDecimal> itemAdjustment = new HashMap<UUID, BigDecimal>();
        // PAss a null value to let invoice calculate the amount to adjust
        itemAdjustment.put(item2.getId(), null);

        invoiceDao.createRefund(paymentId, UUID.randomUUID(), refundAmount, true, itemAdjustment, UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);
        accountBalance = invoiceDao.getAccountBalance(accountId, context);

        searchResult = invoiceDao.searchInvoicesByBalance(BigDecimal.ZERO, SqlOperator.EQ, 0L, 5L, context);
        assertEquals(searchResult.getTotalNbRecords(), (Long) 1L);
        searchResult.close();

        final boolean partialRefund = refundAmount.compareTo(amount) < 0;
        final BigDecimal cba = invoiceDao.getAccountCBA(accountId, context);
        final InvoiceModelDao savedInvoice = invoiceDao.getById(invoice.getId(), context);

        final BigDecimal expectedCba = accountBalance.compareTo(BigDecimal.ZERO) < 0 ? accountBalance.negate() : BigDecimal.ZERO;
        assertEquals(cba.compareTo(expectedCba), 0);

        // Let's re-calculate them from invoice
        final BigDecimal balanceAfterRefund = invoiceDao.getAccountBalance(accountId, context);
        final BigDecimal cbaAfterRefund = invoiceDao.getAccountCBA(accountId, context);

        if (partialRefund) {
            // IB = 20 (rec) - 20 (repair) + 20 (cba) - (20 -7) = 7;  AB = IB - CBA = 7 - 20 = -13
            assertEquals(accountBalance.compareTo(new BigDecimal("-13.0")), 0);
            assertEquals(savedInvoice.getInvoiceItems().size(), 4);
            assertEquals(balanceAfterRefund.compareTo(new BigDecimal("-13.0")), 0);
            assertEquals(cbaAfterRefund.compareTo(expectedCba), 0);
        } else {
            assertEquals(accountBalance.compareTo(new BigDecimal("0.0")), 0);
            assertEquals(savedInvoice.getInvoiceItems().size(), 4);
            assertEquals(balanceAfterRefund.compareTo(BigDecimal.ZERO), 0);
            assertEquals(cbaAfterRefund.compareTo(expectedCba), 0);
        }

    }

    @Test(groups = "slow")
    public void testAccountBalanceWithSmallRefundAndCBANoAdj() throws InvoiceApiException, EntityPersistenceException {
        final BigDecimal refundAmount = new BigDecimal("7.00");
        final BigDecimal expectedBalance = new BigDecimal("-3.00");
        testAccountBalanceWithRefundAndCBAInternal(false, refundAmount, expectedBalance);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithSmallRefundAndCBAWithAdj() throws InvoiceApiException, EntityPersistenceException {
        final BigDecimal refundAmount = new BigDecimal("7.00");
        final BigDecimal expectedBalance = new BigDecimal("-10.00");
        testAccountBalanceWithRefundAndCBAInternal(true, refundAmount, expectedBalance);
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithLargeRefundAndCBANoAdj() throws InvoiceApiException, EntityPersistenceException {
        final BigDecimal refundAmount = new BigDecimal("20.00");
        final BigDecimal expectedBalance = new BigDecimal("10.00");
        testAccountBalanceWithRefundAndCBAInternal(false, refundAmount, expectedBalance);
    }

    private void testAccountBalanceWithRefundAndCBAInternal(final boolean withAdjustment, final BigDecimal refundAmount, final BigDecimal expectedFinalBalance) throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount1 = new BigDecimal("5.0");
        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal rate2 = new BigDecimal("10.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate,
                                                                      amount1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("5.00")), 0);

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("25.00")), 0);

        // Pay the whole thing
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal payment1 = amount1.add(rate1);
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        // Repair previous item with rate 2
        final RepairAdjInvoiceItem item2Repair = new RepairAdjInvoiceItem(invoice1.getId(), accountId, startDate, endDate, rate1.negate(), Currency.USD, item2.getId());
        final RecurringInvoiceItem item2Replace = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                           endDate, rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2Repair, context);
        invoiceUtil.createInvoiceItem(item2Replace, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);

        // CBA
        final CreditBalanceAdjInvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), balance.negate(), Currency.USD);
        invoiceUtil.createInvoiceItem(cbaItem, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);
        BigDecimal cba = invoiceDao.getAccountCBA(accountId, context);
        assertEquals(cba.compareTo(new BigDecimal("10.00")), 0);

        // PARTIAL REFUND on the payment
        final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts = new HashMap<UUID, BigDecimal>();
        if (withAdjustment) {
            invoiceItemIdsWithAmounts.put(item2Replace.getId(), refundAmount);
        }
        invoiceDao.createRefund(paymentId, UUID.randomUUID(), refundAmount, withAdjustment, invoiceItemIdsWithAmounts, UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);

        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(expectedFinalBalance), 0);
        cba = invoiceDao.getAccountCBA(accountId, context);
        final BigDecimal expectedCba = balance.compareTo(BigDecimal.ZERO) < 0 ? balance.negate() : BigDecimal.ZERO;
        assertEquals(cba.compareTo(expectedCba), 0);
    }

    @Test(groups = "slow")
    public void testExternalChargeWithCBA() throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        final InvoiceItemModelDao credit = createCredit(accountId, clock.getUTCToday(), new BigDecimal("20.0"), true);

        final String description = UUID.randomUUID().toString();
        final InvoiceModelDao invoiceForExternalCharge = new InvoiceModelDao(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false);
        final InvoiceItemModelDao externalCharge = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(invoiceForExternalCharge.getId(), accountId, bundleId, description, clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("15.0"), Currency.USD, null));
        invoiceForExternalCharge.addInvoiceItem(externalCharge);
        final InvoiceItemModelDao charge = invoiceDao.createInvoices(List.of(invoiceForExternalCharge), null, Collections.emptySet(), null, null, true, context).get(0);

        InvoiceModelDao newInvoice = invoiceDao.getById(charge.getInvoiceId(), context);
        List<InvoiceItemModelDao> items = newInvoice.getInvoiceItems();
        // No CBA consumed yet since the credit was created on a DRAFT invoice
        assertEquals(items.size(), 1);
        assertEquals(items.get(0).getType(), InvoiceItemType.EXTERNAL_CHARGE);
        assertEquals(items.get(0).getDescription(), description);

        invoiceDao.changeInvoiceStatus(credit.getInvoiceId(), InvoiceStatus.COMMITTED, context);

        // CBA should have been consumed
        newInvoice = invoiceDao.getById(charge.getInvoiceId(), context);
        items = newInvoice.getInvoiceItems();
        assertEquals(items.size(), 2);
        for (final InvoiceItemModelDao cur : items) {
            if (cur.getId().equals(charge.getId())) {
                assertEquals(cur.getType(), InvoiceItemType.EXTERNAL_CHARGE);
                assertEquals(cur.getDescription(), description);
            } else {
                assertEquals(cur.getType(), InvoiceItemType.CBA_ADJ);
                assertTrue(cur.getAmount().compareTo(new BigDecimal("-15.00")) == 0);
            }
        }
    }

    @Test(groups = "slow")
    public void testExternalChargeOnDRAFTInvoiceWithCBA() throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        final InvoiceItemModelDao credit = createCredit(accountId, clock.getUTCToday(), new BigDecimal("20.0"), false);

        final String description = UUID.randomUUID().toString();
        final InvoiceModelDao draftInvoiceForExternalCharge = new InvoiceModelDao(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.DRAFT);
        final InvoiceItemModelDao externalCharge = new InvoiceItemModelDao(new ExternalChargeInvoiceItem(draftInvoiceForExternalCharge.getId(), accountId, bundleId, description, clock.getUTCToday(), clock.getUTCToday(), new BigDecimal("15.0"), Currency.USD, null));
        draftInvoiceForExternalCharge.addInvoiceItem(externalCharge);
        final InvoiceItemModelDao charge = invoiceDao.createInvoices(List.of(draftInvoiceForExternalCharge), null, Collections.emptySet(), null, null, true, context).get(0);

        InvoiceModelDao newInvoice = invoiceDao.getById(charge.getInvoiceId(), context);
        List<InvoiceItemModelDao> items = newInvoice.getInvoiceItems();
        // No CBA consumed yet since the charge was created on a DRAFT invoice
        assertEquals(items.size(), 1);
        assertEquals(items.get(0).getType(), InvoiceItemType.EXTERNAL_CHARGE);
        assertEquals(items.get(0).getDescription(), description);

        invoiceDao.changeInvoiceStatus(charge.getInvoiceId(), InvoiceStatus.COMMITTED, context);

        // CBA should have been consumed
        newInvoice = invoiceDao.getById(charge.getInvoiceId(), context);
        items = newInvoice.getInvoiceItems();
        assertEquals(items.size(), 2);
        for (final InvoiceItemModelDao cur : items) {
            if (cur.getId().equals(charge.getId())) {
                assertEquals(cur.getType(), InvoiceItemType.EXTERNAL_CHARGE);
                assertEquals(cur.getDescription(), description);
            } else {
                assertEquals(cur.getType(), InvoiceItemType.CBA_ADJ);
                assertTrue(cur.getAmount().compareTo(new BigDecimal("-15.00")) == 0);
            }
        }
    }

    @Test(groups = "slow")
    public void testAccountBalanceWithAllSortsOfThings() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal amount1 = new BigDecimal("5.0");
        final BigDecimal rate1 = new BigDecimal("20.0");
        final BigDecimal rate2 = new BigDecimal("10.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate,
                                                                      amount1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("5.00")), 0);

        // Recurring item
        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                    endDate, rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("25.00")), 0);

        // Pay the whole thing
        final BigDecimal payment1 = amount1.add(rate1);
        final InvoicePayment payment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), payment1, Currency.USD, Currency.USD, null, InvoicePaymentStatus.SUCCESS);
        invoiceUtil.createPayment(payment, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);

        // Repair previous item with rate 2
        final RepairAdjInvoiceItem item2Repair = new RepairAdjInvoiceItem(invoice1.getId(), accountId, startDate, endDate, rate1.negate(), Currency.USD, item2.getId());
        final RecurringInvoiceItem item2Replace = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate,
                                                                           endDate, rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2Repair, context);
        invoiceUtil.createInvoiceItem(item2Replace, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);

        // CBA
        final CreditBalanceAdjInvoiceItem cbaItem = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), balance.negate(), Currency.USD);
        invoiceUtil.createInvoiceItem(cbaItem, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("-10.00")), 0);
        BigDecimal cba = invoiceDao.getAccountCBA(accountId, context);
        assertEquals(cba.compareTo(new BigDecimal("10.00")), 0);

        // partial REFUND on the payment (along with CBA generated by the system)
        final InvoicePayment refund = new DefaultInvoicePayment(UUID.randomUUID(), InvoicePaymentType.ATTEMPT, UUID.randomUUID(), invoice1.getId(), new DateTime(), rate2.negate(), Currency.USD,
                                                                Currency.USD, null, payment.getId());
        invoiceUtil.createPayment(refund, context);
        final CreditBalanceAdjInvoiceItem cbaItem2 = new CreditBalanceAdjInvoiceItem(invoice1.getId(), accountId, new LocalDate(), rate2.negate(), Currency.USD);
        invoiceUtil.createInvoiceItem(cbaItem2, context);

        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(BigDecimal.ZERO), 0);
        cba = invoiceDao.getAccountCBA(accountId, context);
        assertEquals(cba.compareTo(BigDecimal.ZERO), 0);

        // NEXT RECURRING on invoice 2

        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1.plusMonths(1), Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        final RecurringInvoiceItem nextItem = new RecurringInvoiceItem(invoice2.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test bla", null, startDate.plusMonths(1),
                                                                       endDate.plusMonths(1), rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(nextItem, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("10.00")), 0);
        cba = invoiceDao.getAccountCBA(accountId, context);
        assertEquals(cba.compareTo(new BigDecimal("0.00")), 0);

        // FINALLY ISSUE A CREDIT ADJ
        final CreditAdjInvoiceItem creditItem = new CreditAdjInvoiceItem(invoice2.getId(), accountId, new LocalDate(), null, rate2.negate(), Currency.USD, null);
        invoiceUtil.createInvoiceItem(creditItem, context);
        balance = invoiceDao.getAccountBalance(accountId, context);
        assertEquals(balance.compareTo(new BigDecimal("0.00")), 0);
        cba = invoiceDao.getAccountCBA(accountId, context);
        assertEquals(cba.compareTo(new BigDecimal("0.00")), 0);
    }

    @Test(groups = "slow")
    public void testAccountCredit() throws InvoiceApiException {
        final UUID accountId = account.getId();
        final LocalDate effectiveDate = new LocalDate(2011, 3, 1);

        final BigDecimal creditAmount = new BigDecimal("5.0");

        createCredit(accountId, effectiveDate, creditAmount, true);

        final List<InvoiceModelDao> invoices = invoiceDao.getAllInvoicesByAccount(false, true, context);
        assertEquals(invoices.size(), 1);

        final InvoiceModelDao invoice = invoices.get(0);
        assertTrue(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).compareTo(BigDecimal.ZERO) == 0);
        final List<InvoiceItemModelDao> invoiceItems = invoice.getInvoiceItems();
        assertEquals(invoiceItems.size(), 2);
        boolean foundCredit = false;
        boolean foundCBA = false;
        for (final InvoiceItemModelDao cur : invoiceItems) {
            if (cur.getType() == InvoiceItemType.CREDIT_ADJ) {
                foundCredit = true;
                assertTrue(cur.getAmount().compareTo(creditAmount.negate()) == 0);
            } else if (cur.getType() == InvoiceItemType.CBA_ADJ) {
                foundCBA = true;
                assertTrue(cur.getAmount().compareTo(creditAmount) == 0);
            }
        }
        assertTrue(foundCredit);
        assertTrue(foundCBA);

        // No account CBA yet since the invoice is in DRAFT mode
        assertEquals(invoiceDao.getAccountCBA(accountId, context).compareTo(BigDecimal.ZERO), 0);

        invoiceDao.changeInvoiceStatus(invoice.getId(), InvoiceStatus.COMMITTED, context);
        assertEquals(invoiceDao.getAccountCBA(accountId, context).compareTo(creditAmount), 0);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalancePositive() throws EntityPersistenceException, InvoiceApiException {
        final BigDecimal creditAmount = new BigDecimal("2.0");
        final BigDecimal expectedBalance = new BigDecimal("3.0");
        final boolean expectCBA = false;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalanceNegative() throws EntityPersistenceException, InvoiceApiException {
        final BigDecimal creditAmount = new BigDecimal("7.0");
        final BigDecimal expectedBalance = new BigDecimal("0.0");
        final boolean expectCBA = true;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    @Test(groups = "slow")
    public void testInvoiceCreditWithBalanceZero() throws EntityPersistenceException, InvoiceApiException {
        final BigDecimal creditAmount = new BigDecimal("5.0");
        final BigDecimal expectedBalance = new BigDecimal("0.0");
        final boolean expectCBA = false;
        testInvoiceCreditInternal(creditAmount, expectedBalance, expectCBA);
    }

    private void testInvoiceCreditInternal(final BigDecimal creditAmount, final BigDecimal expectedBalance, final boolean expectCBA) throws EntityPersistenceException, InvoiceApiException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();

        // Create one invoice with a fixed invoice item
        final LocalDate targetDate = new LocalDate(2011, 2, 15);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);

        final BigDecimal amount1 = new BigDecimal("5.0");

        // Fixed Item
        final FixedPriceInvoiceItem item1 = new FixedPriceInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate,
                                                                      amount1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        // Create the credit item
        final LocalDate effectiveDate = new LocalDate(2011, 3, 1);

        createCredit(accountId, invoice1.getId(), effectiveDate, creditAmount, false);

        final List<InvoiceModelDao> invoices = invoiceDao.getAllInvoicesByAccount(false, true, context);
        assertEquals(invoices.size(), 1);

        final InvoiceModelDao invoice = invoices.get(0);
        assertTrue(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).compareTo(expectedBalance) == 0);
        final List<InvoiceItemModelDao> invoiceItems = invoice.getInvoiceItems();
        assertEquals(invoiceItems.size(), expectCBA ? 3 : 2);
        boolean foundCredit = false;
        boolean foundCBA = false;
        for (final InvoiceItemModelDao cur : invoiceItems) {
            if (cur.getType() == InvoiceItemType.CREDIT_ADJ) {
                foundCredit = true;
                assertTrue(cur.getAmount().compareTo(creditAmount.negate()) == 0);
            } else if (cur.getType() == InvoiceItemType.CBA_ADJ) {
                foundCBA = true;
            }
        }
        assertEquals(foundCBA, expectCBA);
        assertTrue(foundCredit);
    }

    @Test(groups = "slow")
    public void testGetUnpaidInvoicesByAccountId() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        LocalDate upToDate;
        Collection<InvoiceModelDao> invoices;

        upToDate = new LocalDate(2011, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 0);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 1);

        final LocalDate targetDate2 = new LocalDate(2011, 7, 1);
        final Invoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate2, Currency.USD);
        invoiceUtil.createInvoice(invoice2, context);

        final LocalDate startDate2 = new LocalDate(2011, 6, 1);
        final LocalDate endDate2 = startDate2.plusMonths(3);

        final BigDecimal rate3 = new BigDecimal("21.0");

        final RecurringInvoiceItem item3 = new RecurringInvoiceItem(invoice2.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase C", null, startDate2, endDate2,
                                                                    rate3, rate3, Currency.USD);
        invoiceUtil.createInvoiceItem(item3, context);

        upToDate = new LocalDate(2011, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 0);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 2);
    }

    @Test(groups = "slow")
    public void testGetUnpaidInvoicesByAccountIdWithDraftInvoice() throws EntityPersistenceException, InvoiceApiException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate1 = new LocalDate(2011, 10, 6);
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate1, Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final LocalDate endDate = startDate.plusMonths(1);

        final BigDecimal rate1 = new BigDecimal("17.0");
        final BigDecimal rate2 = new BigDecimal("42.0");

        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase A", null, startDate, endDate,
                                                                    rate1, rate1, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final RecurringInvoiceItem item2 = new RecurringInvoiceItem(invoice1.getId(), accountId, bundleId, UUID.randomUUID(), "test product", "test plan", "test phase B", null, startDate, endDate,
                                                                    rate2, rate2, Currency.USD);
        invoiceUtil.createInvoiceItem(item2, context);

        LocalDate upToDate;
        Collection<InvoiceModelDao> invoices;

        upToDate = new LocalDate(2011, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 0);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 1);

        List<InvoiceModelDao> allInvoicesByAccount = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 1, 1), null, true, context);
        assertEquals(allInvoicesByAccount.size(), 1);

        // insert DRAFT invoice
        createCredit(accountId, new LocalDate(2011, 12, 31), BigDecimal.TEN, true);

        allInvoicesByAccount = invoiceDao.getInvoicesByAccount(false, new LocalDate(2011, 1, 1), null, true, context);
        assertEquals(allInvoicesByAccount.size(), 2);
        assertEquals(allInvoicesByAccount.get(0).getStatus(), InvoiceStatus.COMMITTED);
        assertEquals(allInvoicesByAccount.get(1).getStatus(), InvoiceStatus.DRAFT);

        upToDate = new LocalDate(2012, 1, 1);
        invoices = invoiceDao.getUnpaidInvoicesByAccountId(accountId, null, upToDate, context);
        assertEquals(invoices.size(), 1);
    }

    /*
     *
     * this test verifies that immediate changes give the correct results
     *
     */
    @Test(groups = "slow")
    public void testInvoiceGenerationForImmediateChanges() throws InvoiceApiException, CatalogApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        final LocalDate targetDate = new LocalDate(2011, 2, 16);
        final Currency currency = Currency.USD;

        // generate first invoice
        final DefaultPrice price1 = new DefaultPrice(TEN, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(price1);
        final MockPlanPhase phase1 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        final MockPlan plan1 = new MockPlan(phase1);

        final SubscriptionBase subscription = getZombieSubscription();

        final DateTime effectiveDate1 = new DateTime(2011, 2, 1, 0, 0, 0);
        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate1, plan1, phase1, null,
                                                                       recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingMode.IN_ADVANCE,
                                                                       "testEvent1", 1L, SubscriptionBaseTransitionType.CREATE);

        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoiceList), null, targetDate, Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertEquals(invoice1.getBalance(), KillBillMoney.of(TEN, invoice1.getCurrency()));
        invoiceList.add(invoice1);

        // generate second invoice
        final DefaultPrice price2 = new DefaultPrice(TWENTY, Currency.USD);
        final MockInternationalPrice recurringPrice2 = new MockInternationalPrice(price2);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null, BillingPeriod.MONTHLY, PhaseType.TRIAL);
        final MockPlan plan2 = new MockPlan(phase2);

        final DateTime effectiveDate2 = new DateTime(2011, 2, 15, 0, 0, 0);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate2, plan2, phase2, null,
                                                                       recurringPrice2.getPrice(currency), currency, BillingPeriod.MONTHLY, 1, BillingMode.IN_ADVANCE,
                                                                       "testEvent2", 2L, SubscriptionBaseTransitionType.CREATE);
        events.add(event2);

        // second invoice should be for one half (14/28 days) the difference between the rate plans
        // this is a temporary state, since it actually contains an adjusting item that properly belong to invoice 1
        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoiceList), null, targetDate, Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();

        assertEquals(invoice2.getBalance(), KillBillMoney.of(FIVE, invoice2.getCurrency()));
        invoiceList.add(invoice2);

        invoiceUtil.createInvoice(invoice1, context);
        invoiceUtil.createInvoice(invoice2, context);

        final InvoiceModelDao savedInvoice1 = invoiceDao.getById(invoice1.getId(), context);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(savedInvoice1), KillBillMoney.of(TEN, savedInvoice1.getCurrency()));

        final InvoiceModelDao savedInvoice2 = invoiceDao.getById(invoice2.getId(), context);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(savedInvoice2), KillBillMoney.of(FIVE, savedInvoice2.getCurrency()));
    }

    @Test(groups = "slow")
    public void testInvoiceForFreeTrial() throws InvoiceApiException, CatalogApiException {
        final Currency currency = Currency.USD;
        final DefaultPrice price = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(price);
        final MockPlanPhase phase = new MockPlanPhase(null, fixedPrice);
        final MockPlan plan = new MockPlan(phase);

        final SubscriptionBase subscription = getZombieSubscription();
        final DateTime effectiveDate = invoiceUtil.buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate, plan, phase,
                                                                      fixedPrice.getPrice(currency), null, currency, BillingPeriod.MONTHLY, 15, BillingMode.IN_ADVANCE,
                                                                      "testEvent", 1L, SubscriptionBaseTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event);

        final LocalDate targetDate = invoiceUtil.buildDate(2011, 1, 15);
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, new AccountInvoices(), null, targetDate, Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);

    }

    private SubscriptionBase getZombieSubscription(UUID subscriptionId) {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(UUID.randomUUID());
        return subscription;
    }

    private SubscriptionBase getZombieSubscription() {
        return getZombieSubscription(UUID.randomUUID());
    }

    @Test(groups = "slow")
    public void testInvoiceForFreeTrialWithRecurringDiscount() throws InvoiceApiException, CatalogApiException {
        final Currency currency = Currency.USD;

        final DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        final MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        final BigDecimal cheapAmount = new BigDecimal("24.95");
        final DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        final MockPlan plan = new MockPlan();

        final SubscriptionBase subscription = getZombieSubscription();
        final DateTime effectiveDate1 = invoiceUtil.buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate1, plan, phase1, fixedPrice.getPrice(currency),
                                                                       null, currency, BillingPeriod.MONTHLY, 1, BillingMode.IN_ADVANCE,
                                                                       "testEvent1", 1L, SubscriptionBaseTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final UUID accountId = account.getId();
        final InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, new AccountInvoices(), null, new LocalDate(effectiveDate1), Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        assertNotNull(invoice1);
        assertEquals(invoice1.getNumberOfItems(), 1);
        assertEquals(invoice1.getBalance().compareTo(ZERO), 0);

        final List<Invoice> invoiceList = new ArrayList<Invoice>();
        invoiceList.add(invoice1);

        //invoiceUtil.createInvoice(invoice1, invoice1.getTargetDate().getDayOfMonth(), callcontext);

        final DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                                                                       recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingMode.IN_ADVANCE,
                                                                       "testEvent2", 2L, SubscriptionBaseTransitionType.PHASE);
        events.add(event2);

        final InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoiceList), null, new LocalDate(effectiveDate2), Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        assertNotNull(invoice2);
        assertEquals(invoice2.getNumberOfItems(), 1);
        assertEquals(invoice2.getBalance().compareTo(cheapAmount), 0);

        invoiceList.add(invoice2);

        //invoiceUtil.createInvoice(invoice2, invoice2.getTargetDate().getDayOfMonth(), callcontext);

        final DateTime effectiveDate3 = effectiveDate2.plusMonths(1);
        final InvoiceWithMetadata invoiceWithMetadata3 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoiceList), null, new LocalDate(effectiveDate3), Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice3 = invoiceWithMetadata3.getInvoice();
        assertNotNull(invoice3);
        assertEquals(invoice3.getNumberOfItems(), 1);
        assertEquals(invoice3.getBalance().compareTo(cheapAmount), 0);

        //invoiceUtil.createInvoice(invoice3, invoice3.getTargetDate().getDayOfMonth(), callcontext);
    }

    @Test(groups = "slow")
    public void testInvoiceForEmptyEventSet() throws InvoiceApiException {
        final BillingEventSet events = new MockBillingEventSet();
        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, new AccountInvoices(), null, new LocalDate(), Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNull(invoice);
    }

    @Test(groups = "slow")
    public void testMixedModeInvoicePersistence() throws InvoiceApiException, CatalogApiException, EntityPersistenceException {
        final Currency currency = Currency.USD;
        final DefaultPrice zeroPrice = new DefaultPrice(BigDecimal.ZERO, Currency.USD);
        final MockInternationalPrice fixedPrice = new MockInternationalPrice(zeroPrice);
        final MockPlanPhase phase1 = new MockPlanPhase(null, fixedPrice);

        final BigDecimal cheapAmount = new BigDecimal("24.95");
        final DefaultPrice cheapPrice = new DefaultPrice(cheapAmount, Currency.USD);
        final MockInternationalPrice recurringPrice = new MockInternationalPrice(cheapPrice);
        final MockPlanPhase phase2 = new MockPlanPhase(recurringPrice, null);

        final MockPlan plan = new MockPlan();

        final SubscriptionBase subscription = getZombieSubscription();
        final DateTime effectiveDate1 = invoiceUtil.buildDate(2011, 1, 1).toDateTimeAtStartOfDay();

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate1, plan, phase1,
                                                                       fixedPrice.getPrice(currency), null, currency,
                                                                       BillingPeriod.MONTHLY, 1, BillingMode.IN_ADVANCE,
                                                                       "testEvent1", 1L, SubscriptionBaseTransitionType.CREATE);
        final BillingEventSet events = new MockBillingEventSet();
        events.add(event1);

        final DateTime effectiveDate2 = effectiveDate1.plusDays(30);
        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, subscription, effectiveDate2, plan, phase2, null,
                                                                       recurringPrice.getPrice(currency), currency, BillingPeriod.MONTHLY, 31, BillingMode.IN_ADVANCE,
                                                                       "testEvent2", 2L, SubscriptionBaseTransitionType.CHANGE);
        events.add(event2);

        final InvoiceWithMetadata invoiceWithMetadata = generator.generateInvoice(account, events, new AccountInvoices(), null, new LocalDate(effectiveDate2), Currency.USD, null, Collections.emptyList(), context);
        final Invoice invoice = invoiceWithMetadata.getInvoice();
        assertNotNull(invoice);
        assertEquals(invoice.getNumberOfItems(), 2);
        assertEquals(invoice.getBalance().compareTo(cheapAmount), 0);

        invoiceUtil.createInvoice(invoice, context);
        final InvoiceModelDao savedInvoice = invoiceDao.getById(invoice.getId(), context);

        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getInvoiceItems().size(), 2);
        assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(savedInvoice).compareTo(cheapAmount), 0);
    }

    @Test(groups = "slow")
    public void testRefundedInvoiceWithInvoiceItemAdjustmentWithRepair() throws InvoiceApiException, EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2010, 1, 1);

        ((ClockMock) clock).setDay(startDate);

        final LocalDate recuringStartDate = clock.getUTCNow().plusDays(30).toLocalDate();
        final LocalDate recuringEndDate = recuringStartDate.plusMonths(1);
        final LocalDate targetDate = recuringStartDate.plusDays(1);

        // FIRST CREATE INITIAL INVOICE WITH ONE RECURRING ITEM
        final Invoice invoice = new DefaultInvoice(accountId, targetDate, targetDate, Currency.USD);
        final UUID invoiceId = invoice.getId();

        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test product", "test-plan", "test-phase-rec",
                                                                 null, recuringStartDate, recuringEndDate, new BigDecimal("239.00"), new BigDecimal("239.00"), Currency.USD);

        invoice.addInvoiceItem(invoiceItem);
        invoiceUtil.createInvoice(invoice, context);

        ((ClockMock) clock).addDays(1);

        // SECOND CREATE THE PAYMENT
        final BigDecimal paymentAmount = new BigDecimal("239.00");
        final UUID paymentId = UUID.randomUUID();
        final DefaultInvoicePayment defaultInvoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoiceId, clock.getUTCNow(), paymentAmount, Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.SUCCESS);
        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment), UUID.randomUUID(), context);

        // AND THEN THIRD THE REFUND
        final Map<UUID, BigDecimal> invoiceItemMap = new HashMap<UUID, BigDecimal>();
        invoiceItemMap.put(invoiceItem.getId(), new BigDecimal("239.00"));
        invoiceDao.createRefund(paymentId, UUID.randomUUID(), paymentAmount, true, invoiceItemMap, UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);

        final InvoiceModelDao savedInvoice = invoiceDao.getById(invoiceId, context);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getInvoiceItems().size(), 2);

        final List<Invoice> invoices = new ArrayList<Invoice>();
        invoices.add(new DefaultInvoice(savedInvoice));

        // NOW COMPUTE A DIFFERENT ITEM TO TRIGGER REPAIR
        final BillingEventSet events = new MockBillingEventSet();
        final SubscriptionBase subscription = getZombieSubscription(subscriptionId);

        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn("product");

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("plan");
        Mockito.when(plan.getProduct()).thenReturn(product);
        Mockito.when(plan.getRecurringBillingMode()).thenReturn(BillingMode.IN_ADVANCE);

        final PlanPhase phase1 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase1.getName()).thenReturn("plan-phase1");

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, recuringStartDate.toDateTimeAtStartOfDay(), plan, phase1, null,
                                                                       TEN, Currency.USD,
                                                                       BillingPeriod.MONTHLY, 31, BillingMode.IN_ADVANCE,
                                                                       "new-event", 1L, SubscriptionBaseTransitionType.CREATE);
        events.add(event1);
        final InvoiceWithMetadata newInvoiceWithMetadata = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoices), null, targetDate, Currency.USD, null, Collections.emptyList(), context);
        final Invoice newInvoice = newInvoiceWithMetadata.getInvoice();
        invoiceUtil.createInvoice(newInvoice, context);

        // VERIFY THAT WE STILL HAVE ONLY 2 ITEMS, MEANING THERE WERE NO REPAIR AND NO CBA GENERATED
        final Invoice firstInvoice = new DefaultInvoice(invoiceDao.getById(invoiceId, context));
        assertNotNull(firstInvoice);
        assertEquals(firstInvoice.getInvoiceItems().size(), 2);
    }

    @Test(groups = "slow")
    public void testInvoiceNumber() throws InvoiceApiException, EntityPersistenceException {
        final Currency currency = Currency.USD;
        final DateTime targetDate1 = clock.getUTCNow().plusMonths(1);
        final DateTime targetDate2 = clock.getUTCNow().plusMonths(2);

        final SubscriptionBase subscription = getZombieSubscription();

        final Product product = Mockito.mock(Product.class);
        Mockito.when(product.getName()).thenReturn("product");

        final Plan plan = Mockito.mock(Plan.class);
        Mockito.when(plan.getName()).thenReturn("plan");
        Mockito.when(plan.getProduct()).thenReturn(product);

        Mockito.when(plan.getRecurringBillingMode()).thenReturn(BillingMode.IN_ADVANCE);

        final PlanPhase phase1 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase1.getName()).thenReturn("plan-phase1");

        final PlanPhase phase2 = Mockito.mock(PlanPhase.class);
        Mockito.when(phase2.getName()).thenReturn("plan-phase2");

        final BillingEventSet events = new MockBillingEventSet();
        final List<Invoice> invoices = new ArrayList<Invoice>();

        final BillingEvent event1 = invoiceUtil.createMockBillingEvent(null, subscription, targetDate1, plan, phase1, null,
                                                                       TEN, currency,
                                                                       BillingPeriod.MONTHLY, 31, BillingMode.IN_ADVANCE,
                                                                       "testEvent1", 1L, SubscriptionBaseTransitionType.CHANGE);
        events.add(event1);

        InvoiceWithMetadata invoiceWithMetadata1 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoices), null, new LocalDate(targetDate1), Currency.USD, null, Collections.emptyList(), context);
        Invoice invoice1 = invoiceWithMetadata1.getInvoice();
        invoices.add(invoice1);
        invoiceUtil.createInvoice(invoice1, context);
        invoice1 = new DefaultInvoice(invoiceDao.getById(invoice1.getId(), context));
        assertNotNull(invoice1.getInvoiceNumber());

        final BillingEvent event2 = invoiceUtil.createMockBillingEvent(null, subscription, targetDate1, plan, phase2, null,
                                                                       TWENTY, currency,
                                                                       BillingPeriod.MONTHLY, 31, BillingMode.IN_ADVANCE,
                                                                       "testEvent2", 2L, SubscriptionBaseTransitionType.CHANGE);
        events.add(event2);
        InvoiceWithMetadata invoiceWithMetadata2 = generator.generateInvoice(account, events, new AccountInvoices(null, null, invoices), null, new LocalDate(targetDate2), Currency.USD, null, Collections.emptyList(), context);
        Invoice invoice2 = invoiceWithMetadata2.getInvoice();
        invoiceUtil.createInvoice(invoice2, context);
        invoice2 = new DefaultInvoice(invoiceDao.getById(invoice2.getId(), context));
        assertNotNull(invoice2.getInvoiceNumber());
    }

    @Test(groups = "slow")
    public void testRefundWithCBAPartiallyConsumed() throws Exception {
        final UUID accountId = account.getId();

        // Create invoice 1
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoice1.getId(), invoice1.getAccountId(), null, null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), null, clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                   fixedItem1.getStartDate(), fixedItem1.getEndDate(),
                                                                                   fixedItem1.getAmount().negate(), fixedItem1.getCurrency(),
                                                                                   fixedItem1.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                                         fixedItem1.getStartDate(), fixedItem1.getAmount(),
                                                                                                         fixedItem1.getCurrency());

        invoiceUtil.createInvoice(invoice1, context);
        invoiceUtil.createInvoiceItem(fixedItem1, context);
        invoiceUtil.createInvoiceItem(repairAdjInvoiceItem, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem1, context);

        final UUID paymentId = UUID.randomUUID();
        final DefaultInvoicePayment defaultInvoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), clock.getUTCNow().plusDays(12), new BigDecimal("10.0"),
                                                                                      Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.SUCCESS);

        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment), UUID.randomUUID(), context);

        // Create invoice 2
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoice2.getId(), invoice1.getAccountId(), null, null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), null, clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem2.getInvoiceId(), fixedItem2.getAccountId(),
                                                                                                         fixedItem2.getStartDate(), fixedItem2.getAmount().negate(),
                                                                                                         fixedItem2.getCurrency());
        invoiceUtil.createInvoice(invoice2, context);
        invoiceUtil.createInvoiceItem(fixedItem2, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem2, context);

        // Verify scenario - half of the CBA should have been used
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 5.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 0.00, 10.00, context);
        invoiceUtil.verifyInvoice(invoice2.getId(), 0.00, -5.00, context);

        // Refund Payment before we can deleted CBA
        invoiceDao.createRefund(paymentId, UUID.randomUUID(), new BigDecimal("10.0"), false, Collections.emptyMap(), UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);

        // Verify all three invoices were affected
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 0.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 5.00, 5.00, context);
        invoiceUtil.verifyInvoice(invoice2.getId(), 0.00, -5.00, context);
    }

    @Test(groups = "slow")
    public void testRefundCBAFullyConsumedTwice() throws Exception {
        final UUID accountId = account.getId();

        // Create invoice 1
        // Scenario: single item with payment
        // * $10 item
        // Then, a repair occur:
        // * $-10 repair
        // * $10 generated CBA due to the repair (assume previous payment)
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem1 = new FixedPriceInvoiceItem(invoice1.getId(), invoice1.getAccountId(), null, null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), null, clock.getUTCToday(), BigDecimal.TEN, Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                   fixedItem1.getStartDate(), fixedItem1.getEndDate(),
                                                                                   fixedItem1.getAmount().negate(), fixedItem1.getCurrency(),
                                                                                   fixedItem1.getId());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(fixedItem1.getInvoiceId(), fixedItem1.getAccountId(),
                                                                                                         fixedItem1.getStartDate(), fixedItem1.getAmount(),
                                                                                                         fixedItem1.getCurrency());
        invoiceUtil.createInvoice(invoice1, context);
        invoiceUtil.createInvoiceItem(fixedItem1, context);
        invoiceUtil.createInvoiceItem(repairAdjInvoiceItem, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem1, context);

        final BigDecimal paymentAmount = new BigDecimal("10.00");
        final UUID paymentId = UUID.randomUUID();

        final DefaultInvoicePayment defaultInvoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice1.getId(), clock.getUTCNow().plusDays(12), paymentAmount,
                                                                                      Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.SUCCESS);
        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment), UUID.randomUUID(), context);

        // Create invoice 2
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice2 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem2 = new FixedPriceInvoiceItem(invoice2.getId(), invoice1.getAccountId(), null, null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), null, clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem2 = new CreditBalanceAdjInvoiceItem(fixedItem2.getInvoiceId(), fixedItem2.getAccountId(),
                                                                                                         fixedItem2.getStartDate(), fixedItem2.getAmount().negate(),
                                                                                                         fixedItem2.getCurrency());
        invoiceUtil.createInvoice(invoice2, context);
        invoiceUtil.createInvoiceItem(fixedItem2, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem2, context);

        // Create invoice 3
        // Scenario: single item
        // * $5 item
        // * $-5 CBA used
        final DefaultInvoice invoice3 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem fixedItem3 = new FixedPriceInvoiceItem(invoice3.getId(), invoice1.getAccountId(), null, null, null, UUID.randomUUID().toString(),
                                                                 UUID.randomUUID().toString(), null, clock.getUTCToday(), new BigDecimal("5"), Currency.USD);
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem3 = new CreditBalanceAdjInvoiceItem(fixedItem3.getInvoiceId(), fixedItem3.getAccountId(),
                                                                                                         fixedItem3.getStartDate(), fixedItem3.getAmount().negate(),
                                                                                                         fixedItem3.getCurrency());
        invoiceUtil.createInvoice(invoice3, context);
        invoiceUtil.createInvoiceItem(fixedItem3, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem3, context);

        // Verify scenario - all CBA should have been used
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 0.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 0.00, 10.00, context);
        invoiceUtil.verifyInvoice(invoice2.getId(), 0.00, -5.00, context);
        invoiceUtil.verifyInvoice(invoice3.getId(), 0.00, -5.00, context);

        invoiceDao.createRefund(paymentId, UUID.randomUUID(), paymentAmount, false, Collections.emptyMap(), UUID.randomUUID().toString(), InvoicePaymentStatus.SUCCESS, context);

        // Verify all three invoices were affected
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 0.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 10.00, 10.00, context);
        invoiceUtil.verifyInvoice(invoice2.getId(), 0.00, -5.00, context);
        invoiceUtil.verifyInvoice(invoice3.getId(), 0.00, -5.00, context);
    }

    @Test(groups = "slow")
    public void testCantDeleteSystemGeneratedCredit() throws Exception {
        final UUID accountId = account.getId();

        // Create invoice 1
        // Scenario:
        // * $-10 repair
        // * $10 generated CBA
        final Invoice invoice1 = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final RepairAdjInvoiceItem repairAdjInvoiceItem = new RepairAdjInvoiceItem(invoice1.getId(), invoice1.getAccountId(),
                                                                                   invoice1.getInvoiceDate(), invoice1.getInvoiceDate(),
                                                                                   BigDecimal.TEN.negate(), invoice1.getCurrency(),
                                                                                   UUID.randomUUID());
        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(invoice1.getId(), invoice1.getAccountId(),
                                                                                                         invoice1.getInvoiceDate(), repairAdjInvoiceItem.getAmount().negate(),
                                                                                                         invoice1.getCurrency());
        invoiceUtil.createInvoice(invoice1, context);
        invoiceUtil.createInvoiceItem(repairAdjInvoiceItem, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem1, context);

        // Verify scenario
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 10.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 0.00, 10.00, context);

        // Delete the CBA on invoice 1
        try {
            invoiceDao.deleteCBA(accountId, invoice1.getId(), creditBalanceAdjInvoiceItem1.getId(), context);
            Assert.fail();
        } catch (final InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.INVOICE_CBA_DELETED.getCode(), "Cannot delete system generated credit");
        }

        // Verify the result
        Assert.assertEquals(invoiceDao.getAccountCBA(accountId, context).doubleValue(), 10.00);
        invoiceUtil.verifyInvoice(invoice1.getId(), 0.00, 10.00, context);
    }



    @Test(groups = "slow")
    public void testDeleteConsumedCredit() throws Exception {
        final UUID accountId = account.getId();

        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);

        final RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoice.getId(), accountId, UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test ZOO", null,
                                                                    clock.getUTCNow().plusMonths(-1).toLocalDate(), clock.getUTCNow().toLocalDate(),
                                                                    BigDecimal.TEN, BigDecimal.TEN, Currency.USD);



        final CreditBalanceAdjInvoiceItem creditBalanceAdjInvoiceItem1 = new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(),
                                                                                                         invoice.getInvoiceDate(), BigDecimal.ONE.negate(),
                                                                                                         invoice.getCurrency());
        invoiceUtil.createInvoice(invoice, context);
        invoiceUtil.createInvoiceItem(recurringItem, context);
        invoiceUtil.createInvoiceItem(creditBalanceAdjInvoiceItem1, context);

        invoiceUtil.verifyInvoice(invoice.getId(), 9.00, -1.00, context);

        // Delete the CBA on invoice 1
        invoiceDao.deleteCBA(accountId, invoice.getId(), creditBalanceAdjInvoiceItem1.getId(), context);

        final InvoiceModelDao res = invoiceDao.getById(invoice.getId(), context);
        Assert.assertEquals(res.getInvoiceItems().size(), 2);
        Assert.assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(res).compareTo(BigDecimal.TEN), 0);
        Assert.assertEquals(InvoiceModelDaoHelper.getCBAAmount(res).compareTo(BigDecimal.ZERO), 0);
        final InvoiceItemModelDao cbaAdj = res.getInvoiceItems().stream()
                .filter(input -> input.getType() == InvoiceItemType.CBA_ADJ && input.getAmount().compareTo(BigDecimal.ZERO) == 0)
                .findFirst().orElse(null);
        Assert.assertNotNull(cbaAdj);

    }

    @Test(groups = "slow")
    public void testWithFailedPaymentAttempt() throws Exception {
        final UUID accountId = account.getId();
        final Invoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final RecurringInvoiceItem item1 = new RecurringInvoiceItem(invoice.getId(), accountId, bundleId, subscriptionId, "test product", "test plan", "test ZOO", null,
                                                                    clock.getUTCNow().plusMonths(-1).toLocalDate(), clock.getUTCNow().toLocalDate(),
                                                                    BigDecimal.TEN, BigDecimal.TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(item1, context);

        final InvoiceModelDao retrievedInvoice = invoiceDao.getById(invoice.getId(), context);
        assertEquals(retrievedInvoice.getInvoicePayments().size(), 0);

        final UUID paymentId = UUID.randomUUID();
        final DefaultInvoicePayment defaultInvoicePayment = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice.getId(), clock.getUTCNow().plusDays(12), BigDecimal.TEN, Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.INIT);
        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment), UUID.randomUUID(), context);

        final InvoiceModelDao retrievedInvoice1 = invoiceDao.getById(invoice.getId(), context);
        assertEquals(retrievedInvoice1.getInvoicePayments().size(), 1);
        assertEquals(retrievedInvoice1.getInvoicePayments().get(0).getStatus(), InvoicePaymentStatus.INIT);

        final DefaultInvoicePayment defaultInvoicePayment2 = new DefaultInvoicePayment(InvoicePaymentType.ATTEMPT, paymentId, invoice.getId(), clock.getUTCNow().plusDays(12), BigDecimal.TEN, Currency.USD, Currency.USD, "cookie", InvoicePaymentStatus.SUCCESS);
        invoiceDao.notifyOfPaymentCompletion(new InvoicePaymentModelDao(defaultInvoicePayment2), UUID.randomUUID(), context);

        final InvoiceModelDao retrievedInvoice2 = invoiceDao.getById(invoice.getId(), context);
        assertEquals(retrievedInvoice2.getInvoicePayments().size(), 1);
        assertEquals(retrievedInvoice2.getInvoicePayments().get(0).getStatus(), InvoicePaymentStatus.SUCCESS);
    }

    private InvoiceItemModelDao createCredit(final UUID accountId, final LocalDate effectiveDate, final BigDecimal creditAmount, final boolean draft) throws InvoiceApiException {
        return createCredit(accountId, null, effectiveDate, creditAmount, draft);
    }

    private InvoiceItemModelDao createCredit(final UUID accountId, @Nullable final UUID invoiceId, final LocalDate effectiveDate, final BigDecimal creditAmount, final boolean draft) throws InvoiceApiException {
        final InvoiceModelDao invoiceModelDao;
        if (invoiceId == null) {
            invoiceModelDao = new InvoiceModelDao(accountId, effectiveDate, effectiveDate, Currency.USD, false, draft ? InvoiceStatus.DRAFT : InvoiceStatus.COMMITTED);
        } else {
            invoiceModelDao = invoiceDao.getById(invoiceId, context);
        }
        final CreditAdjInvoiceItem invoiceItem = new CreditAdjInvoiceItem(UUID.randomUUID(),
                                                                          context.getCreatedDate(),
                                                                          invoiceModelDao.getId(),
                                                                          accountId,
                                                                          effectiveDate,
                                                                          null,
                                                                          // Note! The amount is negated here!
                                                                          creditAmount.negate(),
                                                                          invoiceModelDao.getCurrency(),
                                                                          null);
        invoiceModelDao.addInvoiceItem(new InvoiceItemModelDao(invoiceItem));
        return invoiceDao.createInvoices(List.of(invoiceModelDao), null, Collections.emptySet(), null, null, true, context).get(0);
    }

    @Test(groups = "slow")
    public void testCreateParentChildInvoiceRelation() throws InvoiceApiException {

        final UUID parentInvoiceId = UUID.randomUUID();
        final UUID childInvoiceId = UUID.randomUUID();
        final UUID childAccountId = UUID.randomUUID();
        InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(parentInvoiceId, childInvoiceId, childAccountId);
        invoiceDao.createParentChildInvoiceRelation(invoiceRelation, context);

        final List<InvoiceParentChildModelDao> relations = invoiceDao.getChildInvoicesByParentInvoiceId(parentInvoiceId, context);
        assertEquals(relations.size(), 1);
        final InvoiceParentChildModelDao parentChildRelation = relations.get(0);
        assertEquals(parentChildRelation.getChildAccountId(), childAccountId);
        assertEquals(parentChildRelation.getChildInvoiceId(), childInvoiceId);
        assertEquals(parentChildRelation.getParentInvoiceId(), parentInvoiceId);

    }

    @Test(groups = "slow")
    public void testCreateParentInvoice() throws InvoiceApiException {

        final UUID parentAccountId = UUID.randomUUID();
        final UUID childAccountId = UUID.randomUUID();
        final DateTime today = clock.getNow(account.getTimeZone());

        InvoiceModelDao parentInvoice = new InvoiceModelDao(parentAccountId, today.toLocalDate(), account.getCurrency(), InvoiceStatus.DRAFT, true);
        InvoiceItem parentInvoiceItem = new ParentInvoiceItem(UUID.randomUUID(), today, parentInvoice.getId(), parentAccountId, childAccountId, BigDecimal.TEN, account.getCurrency(), "");
        parentInvoice.addInvoiceItem(new InvoiceItemModelDao(parentInvoiceItem));

        invoiceDao.createInvoices(List.of(parentInvoice), null, Collections.emptySet(), null, null, true, context);

        final InvoiceModelDao parentDraftInvoice = invoiceDao.getParentDraftInvoice(parentAccountId, context);

        assertNotNull(parentDraftInvoice);
        assertEquals(parentDraftInvoice.getStatus(), InvoiceStatus.DRAFT);
        assertEquals(parentDraftInvoice.getInvoiceItems().size(), 1);

    }

    @Test(groups = "slow")
    public void testRetrieveInvoiceItemsByParentInvoice() throws InvoiceApiException, EntityPersistenceException {
        final UUID childAccountId = account.getId();
        final Invoice childInvoice = new DefaultInvoice(childAccountId, clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final UUID invoiceId = childInvoice.getId();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2010, 1, 1);
        final LocalDate endDate = new LocalDate(2010, 4, 1);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, childAccountId, bundleId, subscriptionId, "test product", "test plan", "test phase", null, startDate, endDate,
                                                                 new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);
        final InvoiceItem invoiceAdj = new ItemAdjInvoiceItem(invoiceItem, startDate, new BigDecimal("-5.00"), Currency.USD);

        childInvoice.addInvoiceItem(invoiceItem);
        childInvoice.addInvoiceItem(invoiceAdj);
        invoiceUtil.createInvoice(childInvoice, context);

        final UUID parentInvoiceId = UUID.randomUUID();

        InvoiceParentChildModelDao invoiceRelation = new InvoiceParentChildModelDao(parentInvoiceId, childInvoice.getId(), childAccountId);
        invoiceDao.createParentChildInvoiceRelation(invoiceRelation, context);

        final List<InvoiceItemModelDao> invoiceItems = invoiceDao.getInvoiceItemsByParentInvoice(parentInvoiceId, context);
        assertEquals(invoiceItems.size(), 2);
        assertEquals(invoiceItems.get(0).getType(), InvoiceItemType.RECURRING);
        assertEquals(invoiceItems.get(1).getType(), InvoiceItemType.ITEM_ADJ);

    }

    @Test(groups = "slow")
    public void testGetByInvoiceItemId() throws EntityPersistenceException, InvoiceApiException {
        final Invoice invoice1 = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice1, context);

        final UUID invoiceId1 = invoice1.getId();

        LocalDate startDate = new LocalDate(2011, 3, 1);
        LocalDate endDate = startDate.plusMonths(1);

        final RecurringInvoiceItem recurringItem1 = new RecurringInvoiceItem(invoiceId1, account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, startDate, endDate,
                                                                             BigDecimal.ONE, BigDecimal.ONE, Currency.USD);
        invoiceUtil.createInvoiceItem(recurringItem1, context);

        final InvoiceModelDao targetInvoice = invoiceDao.getByInvoiceItem(recurringItem1.getId(), internalCallContext);
        assertNotNull(targetInvoice);
        assertEquals(targetInvoice.getId(), invoiceId1);
        assertEquals(targetInvoice.getInvoiceItems().size(), 1);
        assertEquals(targetInvoice.getInvoiceItems().get(0).getId(), recurringItem1.getId());
    }

    @Test(groups = "slow")
    public void testSearch() throws EntityPersistenceException {
        Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.EUR);
        invoiceUtil.createInvoice(invoice, context);

        //search based on invoice id
        Pagination<InvoiceModelDao> page = invoiceDao.searchInvoices(invoice.getId().toString(), 0L, 10L, internalCallContext);
        List<InvoiceModelDao> all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on id

        //search based on account id with limit=2
        page = invoiceDao.searchInvoices(invoice.getAccountId().toString(), 0L, 2L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 2);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on account id

        // search based on currency
        page = invoiceDao.searchInvoices("USD", 0L, 10L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 2);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on currency

        // search based on currency with limit=1
        page = invoiceDao.searchInvoices("USD", 0L, 1L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on currency

        //search based on invoice number
        page = invoiceDao.searchInvoices(all.get(0).getInvoiceNumber().toString(), 0L, 10L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on invoice number

        //search based on query marker
        page = invoiceDao.searchInvoices("_q=1&account_id="+account.getId(), 0L, 1L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNull(all.get(0).getBalance()); //balance not returned as search is based on query marker

        //search based on balance
        page = invoiceDao.searchInvoices("_q=1&balance[eq]=0", 0L, 1L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNotNull(all.get(0).getBalance()); //balance is returned as search is based on balance
        Assert.assertEquals(all.get(0).getBalance().compareTo(BigDecimal.ZERO), 0);
    }

    @Test(groups = "slow")
    public void testSearchOnBalance() throws EntityPersistenceException {
        Invoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        invoiceUtil.createInvoice(invoice, context);

        //invoice  with 0 balance
        Pagination<InvoiceModelDao> page = invoiceDao.searchInvoices("_q=1&balance[eq]=0", 0L, 5L, internalCallContext);
        List<InvoiceModelDao> all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        assertNotNull(all.get(0).getBalance()); //balance is returned as search is based on balance
        Assert.assertEquals(all.get(0).getBalance().stripTrailingZeros().compareTo(BigDecimal.ZERO), 0);

        //DRAFT invoice
        final BigDecimal amount = BigDecimal.TEN;
        invoice = new DefaultInvoice(UUIDs.randomUUID(), account.getId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.DRAFT);
        RecurringInvoiceItem recurringItem = new RecurringInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, LocalDate.now(), LocalDate.now(),
                                                                      amount, BigDecimal.ONE, Currency.USD);
        invoice.addInvoiceItem(recurringItem);
        invoiceUtil.createInvoice(invoice, context);

        page = invoiceDao.searchInvoices("_q=1&balance[eq]=0", 0L, 5L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 2);

        //VOID invoice
        invoice = new DefaultInvoice(UUIDs.randomUUID(), account.getId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, false, InvoiceStatus.VOID);
        recurringItem = new RecurringInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, LocalDate.now(), LocalDate.now(),
                                                 amount, BigDecimal.ONE, Currency.USD);
        invoice.addInvoiceItem(recurringItem);
        invoiceUtil.createInvoice(invoice, context);

        page = invoiceDao.searchInvoices("_q=1&balance[eq]=0", 0L, 5L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 3);

        //migration invoice
        invoice = new DefaultInvoice(UUIDs.randomUUID(), account.getId(), null, clock.getUTCToday(), clock.getUTCToday(), Currency.USD, true, InvoiceStatus.COMMITTED);
        recurringItem = new RecurringInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, LocalDate.now(), LocalDate.now(),
                                                 amount, BigDecimal.ONE, Currency.USD);
        invoice.addInvoiceItem(recurringItem);
        invoiceUtil.createInvoice(invoice, context);

        page = invoiceDao.searchInvoices("_q=1&balance[eq]=0", 0L, 5L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 4);

        //invoice  with non-zero balance
        invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        recurringItem = new RecurringInvoiceItem(invoice.getId(), account.getId(), UUID.randomUUID(), UUID.randomUUID(), "test product", "test plan", "test A", null, LocalDate.now(), LocalDate.now(),
                                                 amount, BigDecimal.ONE, Currency.USD);
        invoice.addInvoiceItem(recurringItem);
        invoiceUtil.createInvoice(invoice, context);

        page = invoiceDao.searchInvoices("_q=1&balance[gt]=0", 0L, 5L, internalCallContext);
        all = Iterables.toUnmodifiableList(page);
        Assert.assertNotNull(all);
        Assert.assertEquals(all.size(), 1);
        Assert.assertNotNull(all.get(0).getBalance()); //balance is returned as search is based on balance
        Assert.assertEquals(all.get(0).getBalance().stripTrailingZeros().compareTo(amount), 0);
    }
}
