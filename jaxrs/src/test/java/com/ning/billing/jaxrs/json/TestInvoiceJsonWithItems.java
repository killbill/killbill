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

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestInvoiceJsonWithItems extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final BigDecimal amount = BigDecimal.TEN;
        final BigDecimal cba = BigDecimal.ONE;
        final BigDecimal creditAdj = BigDecimal.ONE;
        final BigDecimal refundAdj = BigDecimal.ONE;
        final String invoiceId = UUID.randomUUID().toString();
        final LocalDate invoiceDate = clock.getUTCToday();
        final LocalDate targetDate = clock.getUTCToday();
        final String invoiceNumber = UUID.randomUUID().toString();
        final BigDecimal balance = BigDecimal.ZERO;
        final String accountId = UUID.randomUUID().toString();
        final InvoiceItemJsonSimple invoiceItemJsonSimple = createInvoiceItemJson();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final InvoiceJsonWithItems invoiceJsonWithItems = new InvoiceJsonWithItems(amount, cba, creditAdj, refundAdj, invoiceId, invoiceDate,
                                                                                   targetDate, invoiceNumber, balance, accountId,
                                                                                   ImmutableList.<InvoiceItemJsonSimple>of(invoiceItemJsonSimple), auditLogs);
        Assert.assertEquals(invoiceJsonWithItems.getAmount(), amount);
        Assert.assertEquals(invoiceJsonWithItems.getCBA(), cba);
        Assert.assertEquals(invoiceJsonWithItems.getCreditAdj(), creditAdj);
        Assert.assertEquals(invoiceJsonWithItems.getRefundAdj(), refundAdj);
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceDate(), invoiceDate);
        Assert.assertEquals(invoiceJsonWithItems.getTargetDate(), targetDate);
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceNumber(), invoiceNumber);
        Assert.assertEquals(invoiceJsonWithItems.getBalance(), balance);
        Assert.assertEquals(invoiceJsonWithItems.getAccountId(), accountId);
        Assert.assertEquals(invoiceJsonWithItems.getItems().size(), 1);
        Assert.assertEquals(invoiceJsonWithItems.getItems().get(0), invoiceItemJsonSimple);
        Assert.assertEquals(invoiceJsonWithItems.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(invoiceJsonWithItems);
        final InvoiceJsonWithItems fromJson = mapper.readValue(asJson, InvoiceJsonWithItems.class);
        Assert.assertEquals(fromJson, invoiceJsonWithItems);
    }

    @Test(groups = "fast")
    public void testFromInvoice() throws Exception {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getChargedAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getInvoiceDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getTargetDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoice.getInvoiceNumber()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.ZERO);
        Mockito.when(invoice.getAccountId()).thenReturn(UUID.randomUUID());
        final InvoiceItem invoiceItem = createInvoiceItem();
        Mockito.when(invoice.getInvoiceItems()).thenReturn(ImmutableList.<InvoiceItem>of(invoiceItem));

        final InvoiceJsonWithItems invoiceJsonWithItems = new InvoiceJsonWithItems(invoice);
        Assert.assertEquals(invoiceJsonWithItems.getAmount(), invoice.getChargedAmount());
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceId(), invoice.getId().toString());
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoiceJsonWithItems.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(invoiceJsonWithItems.getInvoiceNumber(), String.valueOf(invoice.getInvoiceNumber()));
        Assert.assertEquals(invoiceJsonWithItems.getBalance(), invoice.getBalance());
        Assert.assertEquals(invoiceJsonWithItems.getAccountId(), invoice.getAccountId().toString());
        Assert.assertEquals(invoiceJsonWithItems.getItems().size(), 1);
        Assert.assertNull(invoiceJsonWithItems.getAuditLogs());

        final InvoiceItemJsonSimple invoiceItemJsonSimple = invoiceJsonWithItems.getItems().get(0);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceId(), invoiceItem.getInvoiceId().toString());
        Assert.assertEquals(invoiceItemJsonSimple.getLinkedInvoiceItemId(), invoiceItem.getLinkedItemId().toString());
        Assert.assertEquals(invoiceItemJsonSimple.getAccountId(), invoiceItem.getAccountId().toString());
        Assert.assertEquals(invoiceItemJsonSimple.getBundleId(), invoiceItem.getBundleId().toString());
        Assert.assertEquals(invoiceItemJsonSimple.getSubscriptionId(), invoiceItem.getSubscriptionId().toString());
        Assert.assertEquals(invoiceItemJsonSimple.getPlanName(), invoiceItem.getPlanName());
        Assert.assertEquals(invoiceItemJsonSimple.getPhaseName(), invoiceItem.getPhaseName());
        Assert.assertEquals(invoiceItemJsonSimple.getDescription(), invoiceItem.getDescription());
        Assert.assertEquals(invoiceItemJsonSimple.getStartDate(), invoiceItem.getStartDate());
        Assert.assertEquals(invoiceItemJsonSimple.getEndDate(), invoiceItem.getEndDate());
        Assert.assertEquals(invoiceItemJsonSimple.getAmount(), invoiceItem.getAmount());
        Assert.assertEquals(invoiceItemJsonSimple.getCurrency(), invoiceItem.getCurrency());
    }

    private InvoiceItemJsonSimple createInvoiceItemJson() {
        final String invoiceItemId = UUID.randomUUID().toString();
        final String invoiceId = UUID.randomUUID().toString();
        final String linkedInvoiceItemId = UUID.randomUUID().toString();
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
        return new InvoiceItemJsonSimple(invoiceItemId, invoiceId, linkedInvoiceItemId, accountId, bundleId, subscriptionId,
                                         planName, phaseName, description, startDate, endDate,
                                         amount, currency, null);
    }

    private InvoiceItem createInvoiceItem() {
        final InvoiceItem invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getInvoiceId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getLinkedItemId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getAccountId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getBundleId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getPlanName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getPhaseName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getStartDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoiceItem.getEndDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoiceItem.getAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.EUR);

        return invoiceItem;
    }
}
