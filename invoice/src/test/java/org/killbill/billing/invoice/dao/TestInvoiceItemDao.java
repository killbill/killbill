/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.ExternalChargeInvoiceItem;
import org.killbill.billing.invoice.model.FixedPriceInvoiceItem;
import org.killbill.billing.invoice.model.InvoiceItemFactory;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;

import static org.killbill.billing.invoice.TestInvoiceHelper.TEN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestInvoiceItemDao extends InvoiceTestSuiteWithEmbeddedDB {

    private Account account;
    private InternalCallContext context;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        account = invoiceUtil.createAccount(callContext);
        context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);
    }

    @Test(groups = "slow")
    public void testInvoiceItemCreation() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 10, 1);
        final LocalDate endDate = new LocalDate(2011, 11, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test product", "test plan", "test phase", startDate, endDate,
                                                                   rate, rate, Currency.USD);
        invoiceUtil.createInvoiceItem(item, context);

        final InvoiceItemModelDao thisItem = invoiceUtil.getInvoiceItemById(item.getId(), context);
        assertNotNull(thisItem);
        assertEquals(thisItem.getId(), item.getId());
        assertEquals(thisItem.getInvoiceId(), item.getInvoiceId());
        assertEquals(thisItem.getSubscriptionId(), item.getSubscriptionId());
        assertTrue(thisItem.getStartDate().compareTo(item.getStartDate()) == 0);
        assertTrue(thisItem.getEndDate().compareTo(item.getEndDate()) == 0);
        assertEquals(thisItem.getAmount().compareTo(item.getRate()), 0);
        assertEquals(thisItem.getRate().compareTo(item.getRate()), 0);
        assertEquals(thisItem.getCurrency(), item.getCurrency());
        // created date is no longer set before persistence layer call
        // assertEquals(thisItem.getCreatedDate().compareTo(item.getCreatedDate()), 0);
    }

    @Test(groups = "slow")
    public void testGetInvoiceItemsBySubscriptionId() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 3; i++) {
            final UUID invoiceId = UUID.randomUUID();

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test product", "test plan", "test phase", startDate.plusMonths(i), startDate.plusMonths(i + 1),
                                                                       rate, rate, Currency.USD);
            invoiceUtil.createInvoiceItem(item, context);
        }

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemBySubscriptionId(subscriptionId, context);
        assertEquals(items.size(), 3);
    }

    @Test(groups = "slow")
    public void testGetInvoiceItemsByInvoiceId() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 5; i++) {
            final UUID subscriptionId = UUID.randomUUID();
            final BigDecimal amount = rate.multiply(new BigDecimal(i + 1));

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test product", "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                       amount, amount, Currency.USD);
            invoiceUtil.createInvoiceItem(item, context);
        }

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemByInvoiceId(invoiceId, context);
        assertEquals(items.size(), 5);
    }

    @Test(groups = "slow")
    public void testGetInvoiceItemsByAccountId() throws EntityPersistenceException {
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate = new LocalDate(2011, 5, 23);
        final DefaultInvoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);

        invoiceUtil.createInvoice(invoice, context);

        final UUID invoiceId = invoice.getId();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        final UUID subscriptionId = UUID.randomUUID();

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                   "test product", "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                   rate, rate, Currency.USD);
        invoiceUtil.createInvoiceItem(item, context);

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemByAccountId(context);
        assertEquals(items.size(), 1);
    }

    @Test(groups = "slow")
    public void testCreditBalanceInvoiceSqlDao() throws EntityPersistenceException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = account.getId();
        final LocalDate creditDate = new LocalDate(2012, 4, 1);

        final InvoiceItem creditInvoiceItem = new CreditBalanceAdjInvoiceItem(invoiceId, accountId, creditDate, TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(creditInvoiceItem, context);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(creditInvoiceItem.getId(), context);
        assertSameInvoiceItem(creditInvoiceItem, savedItem);
    }

    @Test(groups = "slow")
    public void testFixedPriceInvoiceSqlDao() throws EntityPersistenceException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = account.getId();
        final LocalDate startDate = new LocalDate(2012, 4, 1);

        final InvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(),
                                                                            UUID.randomUUID(), "test product", "test plan", "test phase", startDate, TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedPriceInvoiceItem, context);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(fixedPriceInvoiceItem.getId(), context);
        assertSameInvoiceItem(fixedPriceInvoiceItem, savedItem);
    }

    @Test(groups = "slow")
    public void testExternalChargeInvoiceSqlDao() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = account.getId();
        final UUID bundleId = UUID.randomUUID();
        final String description = UUID.randomUUID().toString();
        final LocalDate startDate = new LocalDate(2012, 4, 1);
        final LocalDate endDate = new LocalDate(2012, 5, 1);
        final InvoiceItem externalChargeInvoiceItem = new ExternalChargeInvoiceItem(invoiceId, accountId, bundleId, description,
                                                                                    startDate, endDate, TEN, Currency.USD, null);
        invoiceUtil.createInvoiceItem(externalChargeInvoiceItem, context);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(externalChargeInvoiceItem.getId(), context);
        assertSameInvoiceItem(externalChargeInvoiceItem, savedItem);
    }

    @Test(groups = "slow")
    public void testExternalChargeForVariousCurrenciesInvoiceSqlDao() throws Exception {
        // 0 decimal place
        createAndVerifyExternalCharge(new BigDecimal("10"), Currency.VND);
        // 2 decimal places
        createAndVerifyExternalCharge(new BigDecimal("10"), Currency.USD);
        createAndVerifyExternalCharge(new BigDecimal("10.1"), Currency.USD);
        createAndVerifyExternalCharge(new BigDecimal("10.01"), Currency.USD);
        // 3 decimal places
        createAndVerifyExternalCharge(new BigDecimal("10"), Currency.BHD);
        createAndVerifyExternalCharge(new BigDecimal("10.1"), Currency.BHD);
        createAndVerifyExternalCharge(new BigDecimal("10.01"), Currency.BHD);
        createAndVerifyExternalCharge(new BigDecimal("10.001"), Currency.BHD);
        // 8 decimal places
        createAndVerifyExternalCharge(new BigDecimal("10"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.1"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.01"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.001"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.0001"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.00001"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.000001"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.0000001"), Currency.BTC);
        createAndVerifyExternalCharge(new BigDecimal("10.00000001"), Currency.BTC);
        // Malagasy ariary is subdivided into 5 iraimbilanja
        createAndVerifyExternalCharge(new BigDecimal("10.2"), Currency.MGA);
    }

    private void createAndVerifyExternalCharge(final BigDecimal amount, final Currency currency) throws EntityPersistenceException {
        final InvoiceItem externalChargeInvoiceItem = new ExternalChargeInvoiceItem(UUID.randomUUID(), account.getId(), UUID.randomUUID(),
                                                                                    UUID.randomUUID().toString(), new LocalDate(2012, 4, 1), new LocalDate(2012, 5, 1), amount, currency, null);
        invoiceUtil.createInvoiceItem(externalChargeInvoiceItem, context);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(externalChargeInvoiceItem.getId(), context);
        assertSameInvoiceItem(externalChargeInvoiceItem, savedItem);
        Assert.assertEquals(externalChargeInvoiceItem.getAmount().compareTo(amount), 0);
    }

    private void assertSameInvoiceItem(final InvoiceItem initialItem, final InvoiceItemModelDao fromDao) {
        final InvoiceItem newItem = InvoiceItemFactory.fromModelDao(fromDao);
        Assert.assertEquals(newItem.getId(), initialItem.getId());
        Assert.assertTrue(newItem.matches(initialItem));

    }
}
