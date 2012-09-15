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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.util.callcontext.CallContext;

public class TestInvoiceDaoForItemAdjustment extends InvoiceDaoTestBase {

    private static final BigDecimal INVOICE_ITEM_AMOUNT = new BigDecimal("21.00");

    @Test(groups = "slow")
    public void testAddInvoiceItemAdjustmentForNonExistingInvoiceItemId() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID invoiceItemId = UUID.randomUUID();
        final LocalDate effectiveDate = new LocalDate();
        final CallContext context = Mockito.mock(CallContext.class);

        try {
            invoiceDao.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, null, null, context);
            Assert.fail("Should not have been able to adjust a non existing invoice item");
        } catch (Exception e) {
            Assert.assertEquals(((InvoiceApiException) e.getCause()).getCode(), ErrorCode.INVOICE_ITEM_NOT_FOUND.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAddInvoiceItemAdjustmentForWrongInvoice() throws Exception {
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoice.getId(), invoice.getAccountId(), UUID.randomUUID(),
                                                                 UUID.randomUUID(), "test plan", "test phase",
                                                                 new LocalDate(2010, 1, 1), new LocalDate(2010, 4, 1),
                                                                 INVOICE_ITEM_AMOUNT, new BigDecimal("7.00"), Currency.USD);
        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, 1, true, context);

        try {
            invoiceDao.insertInvoiceItemAdjustment(invoice.getAccountId(), UUID.randomUUID(), invoiceItem.getId(), new LocalDate(2010, 1, 1), null, null, context);
            Assert.fail("Should not have been able to adjust an item on a non existing invoice");
        } catch (Exception e) {
            Assert.assertEquals(((InvoiceApiException) e.getCause()).getCode(), ErrorCode.INVOICE_INVALID_FOR_INVOICE_ITEM_ADJUSTMENT.getCode());
        }
    }

    @Test(groups = "slow")
    public void testAddInvoiceItemAdjustmentForFullAmount() throws Exception {
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoice.getId(), invoice.getAccountId(), UUID.randomUUID(),
                                                                 UUID.randomUUID(), "test plan", "test phase",
                                                                 new LocalDate(2010, 1, 1), new LocalDate(2010, 4, 1),
                                                                 INVOICE_ITEM_AMOUNT, new BigDecimal("7.00"), Currency.USD);
        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, 1, true, context);

        final InvoiceItem adjustedInvoiceItem = createAndCheckAdjustment(invoice, invoiceItem, null);
        Assert.assertEquals(adjustedInvoiceItem.getAmount().compareTo(invoiceItem.getAmount().negate()), 0);
    }

    @Test(groups = "slow")
    public void testAddInvoiceItemAdjustmentForPartialAmount() throws Exception {
        final Invoice invoice = new DefaultInvoice(UUID.randomUUID(), clock.getUTCToday(), clock.getUTCToday(), Currency.USD);
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoice.getId(), invoice.getAccountId(), UUID.randomUUID(),
                                                                 UUID.randomUUID(), "test plan", "test phase",
                                                                 new LocalDate(2010, 1, 1), new LocalDate(2010, 4, 1),
                                                                 INVOICE_ITEM_AMOUNT, new BigDecimal("7.00"), Currency.USD);
        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice, 1, true, context);

        final InvoiceItem adjustedInvoiceItem = createAndCheckAdjustment(invoice, invoiceItem, BigDecimal.TEN);
        Assert.assertEquals(adjustedInvoiceItem.getAmount().compareTo(BigDecimal.TEN.negate()), 0);
    }

    private InvoiceItem createAndCheckAdjustment(final Invoice invoice, final InvoiceItem invoiceItem, final BigDecimal amount) throws InvoiceApiException {
        final LocalDate effectiveDate = new LocalDate(2010, 1, 1);
        final InvoiceItem adjustedInvoiceItem = invoiceDao.insertInvoiceItemAdjustment(invoice.getAccountId(), invoice.getId(), invoiceItem.getId(),
                                                                                       effectiveDate, amount, null, context);
        Assert.assertEquals(adjustedInvoiceItem.getAccountId(), invoiceItem.getAccountId());
        Assert.assertNull(adjustedInvoiceItem.getBundleId());
        Assert.assertEquals(adjustedInvoiceItem.getCurrency(), invoiceItem.getCurrency());
        Assert.assertEquals(adjustedInvoiceItem.getDescription(), "item-adj");
        Assert.assertEquals(adjustedInvoiceItem.getEndDate(), effectiveDate);
        Assert.assertEquals(adjustedInvoiceItem.getInvoiceId(), invoiceItem.getInvoiceId());
        Assert.assertEquals(adjustedInvoiceItem.getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
        Assert.assertEquals(adjustedInvoiceItem.getLinkedItemId(), invoiceItem.getId());
        Assert.assertNull(adjustedInvoiceItem.getPhaseName());
        Assert.assertNull(adjustedInvoiceItem.getPlanName());
        Assert.assertNull(adjustedInvoiceItem.getRate());
        Assert.assertEquals(adjustedInvoiceItem.getStartDate(), effectiveDate);
        Assert.assertNull(adjustedInvoiceItem.getSubscriptionId());

        // Retrieve the item by id
        final InvoiceItem retrievedInvoiceItem = invoiceItemSqlDao.getById(adjustedInvoiceItem.getId().toString());
        Assert.assertEquals(retrievedInvoiceItem, adjustedInvoiceItem);

        // Retrieve the item by invoice id
        final Invoice retrievedInvoice = invoiceDao.getById(adjustedInvoiceItem.getInvoiceId());
        final List<InvoiceItem> invoiceItems = retrievedInvoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 2);
        final InvoiceItem retrievedByInvoiceInvoiceItem;
        if (invoiceItems.get(0).getId().equals(adjustedInvoiceItem.getId())) {
            retrievedByInvoiceInvoiceItem = invoiceItems.get(0);
        } else {
            retrievedByInvoiceInvoiceItem = invoiceItems.get(1);
        }
        Assert.assertEquals(retrievedByInvoiceInvoiceItem, adjustedInvoiceItem);

        // Verify the invoice balance
        if (amount == null) {
            Assert.assertEquals(retrievedInvoice.getBalance().compareTo(BigDecimal.ZERO), 0);
        } else {
            Assert.assertEquals(retrievedInvoice.getBalance().compareTo(INVOICE_ITEM_AMOUNT.add(amount.negate())), 0);
        }

        return adjustedInvoiceItem;
    }
}
