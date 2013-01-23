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

package com.ning.billing.invoice.api.invoice;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteWithEmbeddedDB;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePayment.InvoicePaymentType;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.svcs.DefaultInvoiceInternalApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.InvoiceItemSqlDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.invoice.notification.MockNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.DefaultNonEntityDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;
import com.ning.billing.util.svcsapi.bus.InternalBus;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static com.ning.billing.invoice.tests.InvoiceTestUtils.createAndPersistInvoice;
import static com.ning.billing.invoice.tests.InvoiceTestUtils.createAndPersistPayment;

public class TestDefaultInvoicePaymentApi extends InvoiceTestSuiteWithEmbeddedDB {

    private static final BigDecimal THIRTY = new BigDecimal("30.00");
    private static final Currency CURRENCY = Currency.EUR;

    private final Clock clock = new ClockMock();
    private final CacheControllerDispatcher controllerDispatcher = new CacheControllerDispatcher();

    private InvoiceSqlDao invoiceSqlDao;
    private InvoiceItemSqlDao invoiceItemSqlDao;
    private InvoicePaymentApi invoicePaymentApi;
    private InvoiceInternalApi invoiceInternalApi;
    private InternalCallContextFactory internalCallContextFactory;

    @BeforeSuite(groups = "slow")
    public void setup() throws IOException {
        final IDBI dbi = getDBI();

        invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        invoiceSqlDao.test(internalCallContext);

        invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        invoiceItemSqlDao.test(internalCallContext);

        final NonEntityDao nonEntityDao = new DefaultNonEntityDao(dbi);
        final NextBillingDatePoster nextBillingDatePoster = new MockNextBillingDatePoster();
        internalCallContextFactory = new InternalCallContextFactory(clock, nonEntityDao, controllerDispatcher);
        final InvoiceDao invoiceDao = new DefaultInvoiceDao(dbi, nextBillingDatePoster, Mockito.mock(InternalBus.class), clock, controllerDispatcher, nonEntityDao);
        invoicePaymentApi = new DefaultInvoicePaymentApi(invoiceDao, internalCallContextFactory);
        invoiceInternalApi = new DefaultInvoiceInternalApi(invoiceDao);
    }

    @Test(groups = "slow")
    public void testFullRefundWithNoAdjustment() throws Exception {
        verifyRefund(THIRTY, THIRTY, THIRTY, false, ImmutableMap.<UUID, BigDecimal>of());
    }

    @Test(groups = "slow")
    public void testPartialRefundWithNoAdjustment() throws Exception {
        verifyRefund(THIRTY, BigDecimal.TEN, BigDecimal.TEN, false, ImmutableMap.<UUID, BigDecimal>of());
    }

    @Test(groups = "slow")
    public void testFullRefundWithInvoiceAdjustment() throws Exception {
        verifyRefund(THIRTY, THIRTY, BigDecimal.ZERO, true, ImmutableMap.<UUID, BigDecimal>of());
    }

    @Test(groups = "slow")
    public void testPartialRefundWithInvoiceAdjustment() throws Exception {
        verifyRefund(THIRTY, BigDecimal.TEN, BigDecimal.ZERO, true, ImmutableMap.<UUID, BigDecimal>of());
    }

    @Test(groups = "slow")
    public void testFullRefundWithBothInvoiceItemAdjustments() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, callContext, internalCallContextFactory);

        // Fully adjust both items
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        adjustments.put(invoice.getInvoiceItems().get(0).getId(), null);
        adjustments.put(invoice.getInvoiceItems().get(1).getId(), null);

        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("40"), BigDecimal.ZERO, true, adjustments);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithSingleInvoiceItemAdjustment() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, callContext, internalCallContextFactory);

        // Fully adjust both items
        final Map<UUID, BigDecimal> adjustments = new HashMap<UUID, BigDecimal>();
        adjustments.put(invoice.getInvoiceItems().get(0).getId(), null);

        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("30"), BigDecimal.ZERO, true, adjustments);
    }

    @Test(groups = "slow")
    public void testPartialRefundWithTwoInvoiceItemAdjustment() throws Exception {
        // Create an invoice with two items (30 \u20ac and 10 \u20ac)
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock,
                                                        ImmutableList.<BigDecimal>of(THIRTY, BigDecimal.TEN), CURRENCY, callContext, internalCallContextFactory);
        // Adjust partially both items: the invoice posted was 40 \u20ac, but we should really just have charged you 2 \u20ac
        final ImmutableMap<UUID, BigDecimal> adjustments = ImmutableMap.<UUID, BigDecimal>of(invoice.getInvoiceItems().get(0).getId(), new BigDecimal("29"),
                                                                                             invoice.getInvoiceItems().get(1).getId(), new BigDecimal("9"));
        verifyRefund(invoice, new BigDecimal("40"), new BigDecimal("38"), BigDecimal.ZERO, true, adjustments);
    }

    private void verifyRefund(final BigDecimal invoiceAmount, final BigDecimal refundAmount, final BigDecimal finalInvoiceAmount,
                              final boolean adjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final Invoice invoice = createAndPersistInvoice(invoiceSqlDao, invoiceItemSqlDao, clock, invoiceAmount, CURRENCY, callContext, internalCallContextFactory);
        verifyRefund(invoice, invoiceAmount, refundAmount, finalInvoiceAmount, adjusted, invoiceItemIdsWithAmounts);
    }

    private void verifyRefund(final Invoice invoice, final BigDecimal invoiceAmount, final BigDecimal refundAmount, final BigDecimal finalInvoiceAmount,
                              final boolean adjusted, final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts) throws InvoiceApiException {
        final InvoicePayment payment = createAndPersistPayment(invoiceInternalApi, clock, invoice.getId(), invoiceAmount, CURRENCY, internalCallContext);

        // Verify the initial invoice balance
        final BigDecimal initialInvoiceBalance = invoicePaymentApi.getInvoice(invoice.getId(), callContext).getBalance();
        Assert.assertEquals(initialInvoiceBalance.compareTo(BigDecimal.ZERO), 0);

        // Create a full refund with no adjustment
        final InvoicePayment refund = invoiceInternalApi.createRefund(payment.getPaymentId(), refundAmount, adjusted, invoiceItemIdsWithAmounts,
                                                                      UUID.randomUUID(), internalCallContext);
        Assert.assertEquals(refund.getAmount().compareTo(refundAmount.negate()), 0);
        Assert.assertEquals(refund.getCurrency(), CURRENCY);
        Assert.assertEquals(refund.getInvoiceId(), invoice.getId());
        Assert.assertEquals(refund.getPaymentId(), payment.getPaymentId());
        Assert.assertEquals(refund.getType(), InvoicePaymentType.REFUND);

        // Verify the current invoice balance
        final BigDecimal newInvoiceBalance = invoicePaymentApi.getInvoice(invoice.getId(), callContext).getBalance().setScale(2, RoundingMode.HALF_UP);
        Assert.assertEquals(newInvoiceBalance.compareTo(finalInvoiceAmount.setScale(2, RoundingMode.HALF_UP)), 0);
    }
}
