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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.DefaultInvoice;
import org.killbill.billing.invoice.model.RecurringInvoiceItem;

public class TestInvoiceDaoForItemAdjustment extends InvoiceTestSuiteWithEmbeddedDB {

    private static final BigDecimal INVOICE_ITEM_AMOUNT = new BigDecimal("21.00");

    @Test(groups = "slow")
    public void testAddInvoiceItemAdjustmentForNonExistingInvoiceItemId() throws Exception {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID invoiceItemId = UUID.randomUUID();
        final LocalDate effectiveDate = new LocalDate();

        try {
            invoiceDao.insertInvoiceItemAdjustment(accountId, invoiceId, invoiceItemId, effectiveDate, null, null, internalCallContext);
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
        invoiceUtil.createInvoice(invoice, true, internalCallContext);

        try {
            invoiceDao.insertInvoiceItemAdjustment(invoice.getAccountId(), UUID.randomUUID(), invoiceItem.getId(), new LocalDate(2010, 1, 1), null, null, internalCallContext);
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
        invoiceUtil.createInvoice(invoice, true, internalCallContext);

        final InvoiceItemModelDao adjustedInvoiceItem = createAndCheckAdjustment(invoice, invoiceItem, null);
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
        invoiceUtil.createInvoice(invoice, true, internalCallContext);

        final InvoiceItemModelDao adjustedInvoiceItem = createAndCheckAdjustment(invoice, invoiceItem, BigDecimal.TEN);
        Assert.assertEquals(adjustedInvoiceItem.getAmount().compareTo(BigDecimal.TEN.negate()), 0);
    }

    private InvoiceItemModelDao createAndCheckAdjustment(final Invoice invoice, final InvoiceItem invoiceItem, final BigDecimal amount) throws InvoiceApiException {
        final LocalDate effectiveDate = new LocalDate(2010, 1, 1);
        final InvoiceItemModelDao adjustedInvoiceItem = invoiceDao.insertInvoiceItemAdjustment(invoice.getAccountId(), invoice.getId(), invoiceItem.getId(),
                                                                                               effectiveDate, amount, null, internalCallContext);
        Assert.assertEquals(adjustedInvoiceItem.getAccountId(), invoiceItem.getAccountId());
        Assert.assertNull(adjustedInvoiceItem.getBundleId());
        Assert.assertEquals(adjustedInvoiceItem.getCurrency(), invoiceItem.getCurrency());
        Assert.assertEquals(adjustedInvoiceItem.getEndDate(), effectiveDate);
        Assert.assertEquals(adjustedInvoiceItem.getInvoiceId(), invoiceItem.getInvoiceId());
        Assert.assertEquals(adjustedInvoiceItem.getType(), InvoiceItemType.ITEM_ADJ);
        Assert.assertEquals(adjustedInvoiceItem.getLinkedItemId(), invoiceItem.getId());
        Assert.assertNull(adjustedInvoiceItem.getPhaseName());
        Assert.assertNull(adjustedInvoiceItem.getPlanName());
        Assert.assertNull(adjustedInvoiceItem.getRate());
        Assert.assertEquals(adjustedInvoiceItem.getStartDate(), effectiveDate);
        Assert.assertNull(adjustedInvoiceItem.getSubscriptionId());

        // Retrieve the item by id
        final InvoiceItemModelDao retrievedInvoiceItem = invoiceUtil.getInvoiceItemById(adjustedInvoiceItem.getId(), internalCallContext);
        // TODO We can't use equals() due to the createdDate field
        Assert.assertEquals(retrievedInvoiceItem.getAccountId(), adjustedInvoiceItem.getAccountId());
        Assert.assertNull(retrievedInvoiceItem.getBundleId());
        Assert.assertEquals(retrievedInvoiceItem.getCurrency(), adjustedInvoiceItem.getCurrency());
        Assert.assertEquals(retrievedInvoiceItem.getEndDate(), adjustedInvoiceItem.getEndDate());
        Assert.assertEquals(retrievedInvoiceItem.getInvoiceId(), adjustedInvoiceItem.getInvoiceId());
        Assert.assertEquals(retrievedInvoiceItem.getType(), adjustedInvoiceItem.getType());
        Assert.assertEquals(retrievedInvoiceItem.getLinkedItemId(), adjustedInvoiceItem.getLinkedItemId());
        Assert.assertNull(retrievedInvoiceItem.getPhaseName());
        Assert.assertNull(retrievedInvoiceItem.getPlanName());
        Assert.assertNull(retrievedInvoiceItem.getRate());
        Assert.assertEquals(retrievedInvoiceItem.getStartDate(), adjustedInvoiceItem.getStartDate());
        Assert.assertNull(retrievedInvoiceItem.getSubscriptionId());

        // Retrieve the item by invoice id
        final InvoiceModelDao retrievedInvoice = invoiceDao.getById(adjustedInvoiceItem.getInvoiceId(), internalCallContext);
        final List<InvoiceItemModelDao> invoiceItems = retrievedInvoice.getInvoiceItems();
        Assert.assertEquals(invoiceItems.size(), 2);
        final InvoiceItemModelDao retrievedByInvoiceInvoiceItem;
        if (invoiceItems.get(0).getId().equals(adjustedInvoiceItem.getId())) {
            retrievedByInvoiceInvoiceItem = invoiceItems.get(0);
        } else {
            retrievedByInvoiceInvoiceItem = invoiceItems.get(1);
        }
        // TODO We can't use equals() due to the createdDate field
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getAccountId(), adjustedInvoiceItem.getAccountId());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getBundleId(), adjustedInvoiceItem.getBundleId());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getCurrency(), adjustedInvoiceItem.getCurrency());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getEndDate(), adjustedInvoiceItem.getEndDate());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getInvoiceId(), adjustedInvoiceItem.getInvoiceId());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getType(), adjustedInvoiceItem.getType());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getLinkedItemId(), adjustedInvoiceItem.getLinkedItemId());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getPhaseName(), adjustedInvoiceItem.getPhaseName());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getPlanName(), adjustedInvoiceItem.getPlanName());
        Assert.assertEquals(retrievedByInvoiceInvoiceItem.getRate(), adjustedInvoiceItem.getRate());
        Assert.assertEquals(retrievedInvoiceItem.getStartDate(), adjustedInvoiceItem.getStartDate());
        Assert.assertEquals(retrievedInvoiceItem.getSubscriptionId(), adjustedInvoiceItem.getSubscriptionId());

        // Verify the invoice balance
        if (amount == null) {
            Assert.assertEquals(InvoiceModelDaoHelper.getBalance(retrievedInvoice).compareTo(BigDecimal.ZERO), 0);
        } else {
            Assert.assertEquals(InvoiceModelDaoHelper.getBalance(retrievedInvoice).compareTo(INVOICE_ITEM_AMOUNT.add(amount.negate())), 0);
        }

        return adjustedInvoiceItem;
    }
}
