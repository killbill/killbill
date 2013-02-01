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

import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestInvoiceItemJsonSimple extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
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
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final InvoiceItemJsonSimple invoiceItemJsonSimple = new InvoiceItemJsonSimple(invoiceItemId, invoiceId, linkedInvoiceItemId, accountId,
                                                                                      bundleId, subscriptionId, planName, phaseName, description,
                                                                                      startDate, endDate, amount, currency, auditLogs);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceItemId(), invoiceItemId);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceItemJsonSimple.getLinkedInvoiceItemId(), linkedInvoiceItemId);
        Assert.assertEquals(invoiceItemJsonSimple.getAccountId(), accountId);
        Assert.assertEquals(invoiceItemJsonSimple.getBundleId(), bundleId);
        Assert.assertEquals(invoiceItemJsonSimple.getSubscriptionId(), subscriptionId);
        Assert.assertEquals(invoiceItemJsonSimple.getPlanName(), planName);
        Assert.assertEquals(invoiceItemJsonSimple.getPhaseName(), phaseName);
        Assert.assertEquals(invoiceItemJsonSimple.getDescription(), description);
        Assert.assertEquals(invoiceItemJsonSimple.getStartDate(), startDate);
        Assert.assertEquals(invoiceItemJsonSimple.getEndDate(), endDate);
        Assert.assertEquals(invoiceItemJsonSimple.getAmount(), amount);
        Assert.assertEquals(invoiceItemJsonSimple.getCurrency(), currency);
        Assert.assertEquals(invoiceItemJsonSimple.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(invoiceItemJsonSimple);
        final InvoiceItemJsonSimple fromJson = mapper.readValue(asJson, InvoiceItemJsonSimple.class);
        Assert.assertEquals(fromJson, invoiceItemJsonSimple);
    }

    @Test(groups = "fast")
    public void testFromInvoiceItem() throws Exception {
        final InvoiceItem invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getId()).thenReturn(UUID.randomUUID());
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

        final InvoiceItemJsonSimple invoiceItemJsonSimple = new InvoiceItemJsonSimple(invoiceItem);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceItemId(), invoiceItem.getId().toString());
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
}
