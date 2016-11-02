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

package org.killbill.billing.jaxrs.json;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

import static org.killbill.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

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
        final InvoiceJson invoiceJsonSimple = new InvoiceJson(amount, Currency.USD.toString(), InvoiceStatus.COMMITTED.toString(),
                                                              creditAdj, refundAdj, invoiceId, invoiceDate,
                                                              targetDate, invoiceNumber, balance, accountId, bundleKeys,
                                                              credits, null, false, auditLogs);
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
        Assert.assertEquals(invoiceJsonSimple.getStatus(), InvoiceStatus.COMMITTED.toString());
        Assert.assertFalse(invoiceJsonSimple.getIsParentInvoice());

        final String asJson = mapper.writeValueAsString(invoiceJsonSimple);
        final InvoiceJson fromJson = mapper.readValue(asJson, InvoiceJson.class);
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
        Mockito.when(invoice.getCurrency()).thenReturn(Currency.MXN);
        Mockito.when(invoice.getStatus()).thenReturn(InvoiceStatus.COMMITTED);


        final String bundleKeys = UUID.randomUUID().toString();
        final List<CreditJson> credits = ImmutableList.<CreditJson>of(createCreditJson());

        final InvoiceJson invoiceJson = new InvoiceJson(invoice, bundleKeys, credits, null);
        Assert.assertEquals(invoiceJson.getAmount(), invoice.getChargedAmount());
        Assert.assertEquals(invoiceJson.getCreditAdj(), invoice.getCreditedAmount());
        Assert.assertEquals(invoiceJson.getRefundAdj(), invoice.getRefundedAmount());
        Assert.assertEquals(invoiceJson.getInvoiceId(), invoice.getId().toString());
        Assert.assertEquals(invoiceJson.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoiceJson.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(invoiceJson.getInvoiceNumber(), String.valueOf(invoice.getInvoiceNumber()));
        Assert.assertEquals(invoiceJson.getBalance(), invoice.getBalance());
        Assert.assertEquals(invoiceJson.getAccountId(), invoice.getAccountId().toString());
        Assert.assertEquals(invoiceJson.getBundleKeys(), bundleKeys);
        Assert.assertEquals(invoiceJson.getCredits(), credits);
        Assert.assertNull(invoiceJson.getAuditLogs());
        Assert.assertEquals(invoiceJson.getStatus(), InvoiceStatus.COMMITTED.toString());
    }

    private CreditJson createCreditJson() {
        final BigDecimal creditAmount = BigDecimal.TEN;
        final Currency currency = Currency.USD;
        final String invoiceId = UUID.randomUUID().toString();
        final String invoiceNumber = UUID.randomUUID().toString();
        final LocalDate effectiveDate = clock.getUTCToday();
        final String accountId = UUID.randomUUID().toString();
        return new CreditJson(creditAmount, currency.name(), invoiceId, invoiceNumber, effectiveDate,  accountId, null, null);
    }
}
