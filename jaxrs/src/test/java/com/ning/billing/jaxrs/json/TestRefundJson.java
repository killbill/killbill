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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.jaxrs.JaxrsTestSuite;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

import com.google.common.collect.ImmutableList;

public class TestRefundJson extends JaxrsTestSuite {

    private final Clock clock = new ClockMock();

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String refundId = UUID.randomUUID().toString();
        final String paymentId = UUID.randomUUID().toString();
        final BigDecimal refundAmount = BigDecimal.TEN;
        final String currency = "USD";
        final boolean isAdjusted = true;
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final List<InvoiceItemJsonSimple> adjustments = ImmutableList.<InvoiceItemJsonSimple>of(createInvoiceItemJson());
        final List<AuditLogJson> auditLogs = createAuditLogsJson();
        final RefundJson refundJson = new RefundJson(refundId, paymentId, refundAmount, currency, isAdjusted, requestedDate,
                                                     effectiveDate, adjustments, auditLogs);
        Assert.assertEquals(refundJson.getRefundId(), refundId);
        Assert.assertEquals(refundJson.getPaymentId(), paymentId);
        Assert.assertEquals(refundJson.getRefundAmount(), refundAmount);
        Assert.assertEquals(refundJson.getCurrency(), currency);
        Assert.assertEquals(refundJson.isAdjusted(), isAdjusted);
        Assert.assertEquals(refundJson.getRequestedDate(), requestedDate);
        Assert.assertEquals(refundJson.getEffectiveDate(), effectiveDate);
        Assert.assertEquals(refundJson.getAdjustments(), adjustments);
        Assert.assertEquals(refundJson.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(refundJson);
        final RefundJson fromJson = mapper.readValue(asJson, RefundJson.class);
        Assert.assertEquals(fromJson, refundJson);
    }

    private InvoiceItemJsonSimple createInvoiceItemJson() {
        final String invoiceItemId = UUID.randomUUID().toString();
        final String invoiceId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String subscriptionId = UUID.randomUUID().toString();
        final String planName = UUID.randomUUID().toString();
        final String phaseName = UUID.randomUUID().toString();
        final String description = UUID.randomUUID().toString();
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = clock.getUTCToday();
        final BigDecimal amount = BigDecimal.TEN;
        final Currency currency = Currency.MXN;
        final List<AuditLogJson> auditLogs = createAuditLogsJson();
        return new InvoiceItemJsonSimple(invoiceItemId, invoiceId, accountId, bundleId, subscriptionId,
                                         planName, phaseName, description, startDate, endDate,
                                         amount, currency, auditLogs);
    }
}
