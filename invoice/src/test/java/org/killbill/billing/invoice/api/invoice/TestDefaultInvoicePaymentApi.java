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

package org.killbill.billing.invoice.api.invoice;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentType;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.killbill.billing.invoice.proRations.InvoiceTestUtils.createAndPersistInvoice;
import static org.killbill.billing.invoice.proRations.InvoiceTestUtils.createAndPersistPayment;

public class TestDefaultInvoicePaymentApi extends InvoiceTestSuiteWithEmbeddedDB {

    private static final BigDecimal THIRTY = new BigDecimal("30.00");
    private static final Currency CURRENCY = Currency.EUR;

    @Test(groups = "slow")
    public void testFullRefundWithNoAdjustment() throws Exception {
        verifyRefund(THIRTY, THIRTY, THIRTY, false, ImmutableMap.<UUID, BigDecimal>of());
    }

    @Test(groups = "slow")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        verifyRefund(THIRTY, BigDecimal.TEN, BigDecimal.TEN, false, ImmutableMap.<UUID, BigDecimal>of());
    }


    @Test(groups = "slow")
    public void testFullRefundWithBothInvoiceItemAdjustments() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceUtil, invoiceDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, internalCallContext);

        // Fully adjust both items
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        adjustments.put(invoice.getInvoiceItems().get(0).getId(), null);
        adjustments.put(invoice.getInvoiceItems().get(1).getId(), null);

        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("40"), BigDecimal.ZERO, true, adjustments);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithSingleInvoiceItemAdjustment() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceUtil, invoiceDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, internalCallContext);

        // Fully adjust both items
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        adjustments.put(invoice.getInvoiceItems().get(0).getId(), null);

        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("30"), BigDecimal.ZERO, true, adjustments);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithTwoInvoiceItemAdjustment() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceUtil, invoiceDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, internalCallContext);
        // Adjust partially both items: the invoice posted was 40 \u20ac, but we should really just have charged you 2 \u20ac
        final ImmutableMap<UUID, BigDecimal> adjustments = ImmutableMap.<UUID, BigDecimal>of(invoice.getInvoiceItems().get(0).getId(), new BigDecimal("29"),
                                                                                             invoice.getInvoiceItems().get(1).getId(), new BigDecimal("9"));
        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("38"), BigDecimal.ZERO, true, adjustments);
    }

    private void verifyRefund(final BigDecimal invoiceAmount, final BigDecimal refundAmount, final BigDecimal finalInvoiceAmount,
                              final boolean adjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceUtil, invoiceDao, clock, invoiceAmount, CURRENCY, internalCallContext);
        verifyRefund(invoice, invoiceAmount, refundAmount, finalInvoiceAmount, adjusted, invoiceItemIdsWithAmounts);
    }

    private void verifyRefund(final Invoice invoice, final BigDecimal invoiceAmount, final BigDecimal refundAmount, final BigDecimal finalInvoiceAmount,
                              final boolean adjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final InvoicePayment payment = createAndPersistPayment(invoiceInternalApi, clock, invoice.getId(), invoiceAmount, CURRENCY, internalCallContext);

        // Verify the initial invoice balance
        final BigDecimal initialInvoiceBalance = invoiceInternalApi.getInvoiceById(invoice.getId(), internalCallContext).getBalance();
        Assert.assertEquals(initialInvoiceBalance.compareTo(BigDecimal.ZERO), 0);

        // Create a full refund with no adjustment
        final InvoicePayment refund = invoiceInternalApi.recordRefund(payment.getPaymentId(), UUID.randomUUID(), refundAmount, adjusted, invoiceItemIdsWithAmounts,
                                                                      UUID.randomUUID().toString(), true, internalCallContext);
        Assert.assertEquals(refund.getAmount().compareTo(refundAmount.negate()), 0);
        Assert.assertEquals(refund.getCurrency(), CURRENCY);
        Assert.assertEquals(refund.getInvoiceId(), invoice.getId());
        Assert.assertEquals(refund.getPaymentId(), payment.getPaymentId());
        Assert.assertEquals(refund.getType(), InvoicePaymentType.REFUND);

        // Verify the current invoice balance
        final BigDecimal newInvoiceBalance = invoiceInternalApi.getInvoiceById(invoice.getId(), internalCallContext).getBalance();
        Assert.assertEquals(newInvoiceBalance.compareTo(finalInvoiceAmount), 0);
    }
}
