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
import java.util.Map;
import java.util.UUID;

import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ErrorCode;
import com.ning.billing.invoice.InvoiceTestSuite;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.tag.dao.MockTagDao;
import com.ning.billing.util.tag.dao.MockTagDefinitionDao;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.common.collect.ImmutableMap;

public class TestDefaultInvoiceDao extends InvoiceTestSuite {

    private InvoiceSqlDao invoiceSqlDao;
    private AuditedInvoiceDao dao;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final IDBI idbi = Mockito.mock(IDBI.class);
        invoiceSqlDao = Mockito.mock(InvoiceSqlDao.class);
        Mockito.when(idbi.onDemand(InvoiceSqlDao.class)).thenReturn(invoiceSqlDao);
        Mockito.when(invoiceSqlDao.getById(Mockito.anyString(), Mockito.<InternalTenantContext>any())).thenReturn(Mockito.mock(Invoice.class));
        Mockito.when(invoiceSqlDao.inTransaction(Mockito.<Transaction<Void, InvoiceSqlDao>>any())).thenAnswer(new Answer() {
            @Override
            public Object answer(final InvocationOnMock invocation) {
                final Object[] args = invocation.getArguments();
                try {
                    ((Transaction<Void, InvoiceSqlDao>) args[0]).inTransaction(invoiceSqlDao, null);
                } catch (Exception e) {
                    Assert.fail(e.toString());
                }
                return null;
            }
        });

        final NextBillingDatePoster poster = Mockito.mock(NextBillingDatePoster.class);
        final TagDefinitionDao tagDefinitionDao = new MockTagDefinitionDao();
        final TagDao tagDao = new MockTagDao();
        dao = new AuditedInvoiceDao(idbi, poster, Mockito.mock(Clock.class), Mockito.mock(InternalBus.class));
    }

    @Test(groups = "fast")
    public void testComputePositiveRefundAmount() throws Exception {
        // Verify the cases with no adjustment first
        final Map<UUID, BigDecimal> noItemAdjustment = ImmutableMap.<UUID, BigDecimal>of();
        verifyComputedRefundAmount(null, null, noItemAdjustment, BigDecimal.ZERO);
        verifyComputedRefundAmount(null, BigDecimal.ZERO, noItemAdjustment, BigDecimal.ZERO);
        verifyComputedRefundAmount(BigDecimal.TEN, null, noItemAdjustment, BigDecimal.TEN);
        verifyComputedRefundAmount(BigDecimal.TEN, BigDecimal.ONE, noItemAdjustment, BigDecimal.ONE);
        try {
            verifyComputedRefundAmount(BigDecimal.ONE, BigDecimal.TEN, noItemAdjustment, BigDecimal.TEN);
            Assert.fail("Shouldn't have been able to compute a refund amount");
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.REFUND_AMOUNT_TOO_HIGH.getCode());
        }

        // Try with adjustments now
        final Map<UUID, BigDecimal> itemAdjustments = ImmutableMap.<UUID, BigDecimal>of(UUID.randomUUID(), BigDecimal.ONE,
                                                                                        UUID.randomUUID(), BigDecimal.TEN,
                                                                                        UUID.randomUUID(), BigDecimal.ZERO);
        verifyComputedRefundAmount(new BigDecimal("100"), new BigDecimal("11"), itemAdjustments, new BigDecimal("11"));
        try {
            verifyComputedRefundAmount(new BigDecimal("100"), BigDecimal.TEN, itemAdjustments, BigDecimal.TEN);
            Assert.fail("Shouldn't have been able to compute a refund amount");
        } catch (InvoiceApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.REFUND_AMOUNT_DONT_MATCH_ITEMS_TO_ADJUST.getCode());
        }
    }

    private void verifyComputedRefundAmount(final BigDecimal paymentAmount, final BigDecimal requestedAmount,
                                            final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final BigDecimal expectedRefundAmount) throws InvoiceApiException {
        final InvoicePayment invoicePayment = Mockito.mock(InvoicePayment.class);
        Mockito.when(invoicePayment.getAmount()).thenReturn(paymentAmount);

        final BigDecimal actualRefundAmount = dao.computePositiveRefundAmount(invoicePayment, requestedAmount, invoiceItemIdsWithAmounts);
        Assert.assertEquals(actualRefundAmount, expectedRefundAmount);
    }

    @Test(groups = "fast")
    public void testFindByNumber() throws Exception {
        final Integer number = Integer.MAX_VALUE;
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoiceSqlDao.getByRecordId(number.longValue(), internalCallContext)).thenReturn(invoice);

        Assert.assertEquals(dao.getByNumber(number, internalCallContext), invoice);
        try {
            dao.getByNumber(Integer.MIN_VALUE, internalCallContext);
            Assert.fail();
        } catch (InvoiceApiException e) {
        }
    }
}
