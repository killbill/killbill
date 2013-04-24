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

package com.ning.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestInvoiceJsonWithBundleKeys extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final BigDecimal amount = BigDecimal.TEN;
        final BigDecimal creditAdj = BigDecimal.ONE;
        final BigDecimal refundAdj = BigDecimal.ONE;
        final String invoiceId = UUID.randomUUID().toString();
        final LocalDate invoiceDate = clock.getUTCToday();
        final LocalDate targetDate = clock.getUTCToday();
        final String invoiceNumber = UUID.randomUUID().toString();
        final BigDecimal balance = BigDecimal.ZERO;
        final String accountId = UUID.randomUUID().toString();
        final String bundleKeys = UUID.randomUUID().toString();
        final CreditJson creditJson = createCreditJson();
        final List<CreditJson> credits = ImmutableList.<CreditJson>of(creditJson);
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final InvoiceJsonWithBundleKeys invoiceJsonSimple = new InvoiceJsonWithBundleKeys(amount, creditAdj, refundAdj, invoiceId, invoiceDate,
                                                                                          targetDate, invoiceNumber, balance, accountId, bundleKeys,
                                                                                          credits, auditLogs);
        Assert.assertEquals(invoiceJsonSimple.getAmount(), amount);
        Assert.assertEquals(invoiceJsonSimple.getCreditAdj(), creditAdj);
        Assert.assertEquals(invoiceJsonSimple.getRefundAdj(), refundAdj);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceDate(), invoiceDate);
        Assert.assertEquals(invoiceJsonSimple.getTargetDate(), targetDate);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceNumber(), invoiceNumber);
        Assert.assertEquals(invoiceJsonSimple.getBalance(), balance);
        Assert.assertEquals(invoiceJsonSimple.getAccountId(), accountId);
        Assert.assertEquals(invoiceJsonSimple.getBundleKeys(), bundleKeys);
        Assert.assertEquals(invoiceJsonSimple.getCredits(), credits);
        Assert.assertEquals(invoiceJsonSimple.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(invoiceJsonSimple);
        final InvoiceJsonWithBundleKeys fromJson = mapper.readValue(asJson, InvoiceJsonWithBundleKeys.class);
        Assert.assertEquals(fromJson, invoiceJsonSimple);
    }

    @Test(groups = "fast")
    public void testFromInvoice() throws Exception {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getChargedAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getCreditedAmount()).thenReturn(BigDecimal.ONE);
        Mockito.when(invoice.getRefundedAmount()).thenReturn(BigDecimal.ONE);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getInvoiceDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getTargetDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getInvoiceNumber()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.ZERO);
        Mockito.when(invoice.getAccountId()).thenReturn(UUID.randomUUID());

        final String bundleKeys = UUID.randomUUID().toString();
        final List<CreditJson> credits = ImmutableList.<CreditJson>of(createCreditJson());

        final InvoiceJsonWithBundleKeys invoiceJsonWithBundleKeys = new InvoiceJsonWithBundleKeys(invoice, bundleKeys, credits, null);
        Assert.assertEquals(invoiceJsonWithBundleKeys.getAmount(), invoice.getChargedAmount());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getCreditAdj(), invoice.getCreditedAmount());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getRefundAdj(), invoice.getRefundedAmount());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getInvoiceId(), invoice.getId().toString());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getInvoiceNumber(), String.valueOf(invoice.getInvoiceNumber()));
        Assert.assertEquals(invoiceJsonWithBundleKeys.getBalance(), invoice.getBalance());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getAccountId(), invoice.getAccountId().toString());
        Assert.assertEquals(invoiceJsonWithBundleKeys.getBundleKeys(), bundleKeys);
        Assert.assertEquals(invoiceJsonWithBundleKeys.getCredits(), credits);
        Assert.assertNull(invoiceJsonWithBundleKeys.getAuditLogs());
    }

    private CreditJson createCreditJson() {
        final BigDecimal creditAmount = BigDecimal.TEN;
        final String invoiceId = UUID.randomUUID().toString();
        final String invoiceNumber = UUID.randomUUID().toString();
        final DateTime requestedDate = clock.getUTCNow();
        final DateTime effectiveDate = clock.getUTCNow();
        final String reason = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        return new CreditJson(creditAmount, invoiceId, invoiceNumber, requestedDate, effectiveDate, reason, accountId, null);
    }
}
