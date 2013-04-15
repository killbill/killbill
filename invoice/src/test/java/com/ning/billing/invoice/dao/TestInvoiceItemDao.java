/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.ExternalChargeInvoiceItem;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.invoice.model.InvoiceItemFactory;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.util.entity.EntityPersistenceException;

import static com.ning.billing.invoice.TestInvoiceHelper.TEN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestInvoiceItemDao extends InvoiceTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testInvoiceItemCreation() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 10, 1);
        final LocalDate endDate = new LocalDate(2011, 11, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, "test plan", "test phase", startDate, endDate,
                                                                   rate, rate, Currency.USD);
        invoiceUtil.createInvoiceItem(item, internalCallContext);

        final InvoiceItemModelDao thisItem = invoiceUtil.getInvoiceItemById(item.getId(), internalCallContext);
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
        final UUID accountId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 3; i++) {
            final UUID invoiceId = UUID.randomUUID();

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test plan", "test phase", startDate.plusMonths(i), startDate.plusMonths(i + 1),
                                                                       rate, rate, Currency.USD);
            invoiceUtil.createInvoiceItem(item, internalCallContext);
        }

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemBySubscriptionId(subscriptionId, internalCallContext);
        assertEquals(items.size(), 3);
    }

    @Test(groups = "slow")
    public void testGetInvoiceItemsByInvoiceId() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        for (int i = 0; i < 5; i++) {
            final UUID subscriptionId = UUID.randomUUID();
            final BigDecimal amount = rate.multiply(new BigDecimal(i + 1));

            final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                       "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                       amount, amount, Currency.USD);
            invoiceUtil.createInvoiceItem(item, internalCallContext);
        }

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemByInvoiceId(invoiceId, internalCallContext);
        assertEquals(items.size(), 5);
    }

    @Test(groups = "slow")
    public void testGetInvoiceItemsByAccountId() throws EntityPersistenceException {
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final LocalDate targetDate = new LocalDate(2011, 5, 23);
        final DefaultInvoice invoice = new DefaultInvoice(accountId, clock.getUTCToday(), targetDate, Currency.USD);

        invoiceUtil.createInvoice(invoice, true, internalCallContext);

        final UUID invoiceId = invoice.getId();
        final LocalDate startDate = new LocalDate(2011, 3, 1);
        final BigDecimal rate = new BigDecimal("20.00");

        final UUID subscriptionId = UUID.randomUUID();

        final RecurringInvoiceItem item = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId,
                                                                   "test plan", "test phase", startDate, startDate.plusMonths(1),
                                                                   rate, rate, Currency.USD);
        invoiceUtil.createInvoiceItem(item, internalCallContext);

        final List<InvoiceItemModelDao> items = invoiceUtil.getInvoiceItemByAccountId(accountId, internalCallContext);
        assertEquals(items.size(), 1);
    }

    @Test(groups = "slow")
    public void testCreditBalanceInvoiceSqlDao() throws EntityPersistenceException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final LocalDate creditDate = new LocalDate(2012, 4, 1);

        final InvoiceItem creditInvoiceItem = new CreditBalanceAdjInvoiceItem(invoiceId, accountId, creditDate, TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(creditInvoiceItem, internalCallContext);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(creditInvoiceItem.getId(), internalCallContext);
        assertSameInvoiceItem(creditInvoiceItem, savedItem);
    }

    @Test(groups = "slow")
    public void testFixedPriceInvoiceSqlDao() throws EntityPersistenceException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final LocalDate startDate = new LocalDate(2012, 4, 1);

        final InvoiceItem fixedPriceInvoiceItem = new FixedPriceInvoiceItem(invoiceId, accountId, UUID.randomUUID(),
                                                                            UUID.randomUUID(), "test plan", "test phase", startDate, TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(fixedPriceInvoiceItem, internalCallContext);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(fixedPriceInvoiceItem.getId(), internalCallContext);
        assertSameInvoiceItem(fixedPriceInvoiceItem, savedItem);
    }

    @Test(groups = "slow")
    public void testExternalChargeInvoiceSqlDao() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String description = UUID.randomUUID().toString();
        final LocalDate startDate = new LocalDate(2012, 4, 1);
        final InvoiceItem externalChargeInvoiceItem = new ExternalChargeInvoiceItem(invoiceId, accountId, bundleId, description,
                                                                                    startDate, TEN, Currency.USD);
        invoiceUtil.createInvoiceItem(externalChargeInvoiceItem, internalCallContext);

        final InvoiceItemModelDao savedItem = invoiceUtil.getInvoiceItemById(externalChargeInvoiceItem.getId(), internalCallContext);
        assertSameInvoiceItem(externalChargeInvoiceItem, savedItem);
    }


    private void assertSameInvoiceItem(final InvoiceItem initialItem, final InvoiceItemModelDao fromDao) {
        final InvoiceItem newItem = InvoiceItemFactory.fromModelDao(fromDao);
        Assert.assertEquals(newItem.getId(), initialItem.getId());
        Assert.assertTrue(newItem.matches(initialItem));

    }
}
