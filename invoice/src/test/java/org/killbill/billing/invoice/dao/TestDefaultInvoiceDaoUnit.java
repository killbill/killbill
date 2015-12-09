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
import java.util.Map;
import java.util.UUID;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceApiException;

import com.google.common.collect.ImmutableMap;

public class TestDefaultInvoiceDaoUnit extends InvoiceTestSuiteNoDB {

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
        final InvoicePaymentModelDao invoicePayment = Mockito.mock(InvoicePaymentModelDao.class);
        Mockito.when(invoicePayment.getAmount()).thenReturn(paymentAmount);

        final BigDecimal actualRefundAmount = invoiceDaoHelper.computePositiveRefundAmount(invoicePayment, requestedAmount, invoiceItemIdsWithAmounts);
        Assert.assertEquals(actualRefundAmount, expectedRefundAmount);
    }
}
