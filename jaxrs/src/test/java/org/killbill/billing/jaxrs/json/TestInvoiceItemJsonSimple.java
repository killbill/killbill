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
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;

import static org.killbill.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestInvoiceItemJsonSimple extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String invoiceItemId = UUID.randomUUID().toString();
        final String invoiceId = UUID.randomUUID().toString();
        final String linkedInvoiceItemId = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String childAccountId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String subscriptionId = UUID.randomUUID().toString();
        final String planName = UUID.randomUUID().toString();
        final String phaseName = UUID.randomUUID().toString();
        final String usageName = UUID.randomUUID().toString();
        final String type = "FIXED";
        final String description = UUID.randomUUID().toString();
        final LocalDate startDate = clock.getUTCToday();
        final LocalDate endDate = clock.getUTCToday();
        final BigDecimal amount = BigDecimal.TEN;
        final Currency currency = Currency.MXN;
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final InvoiceItemJson invoiceItemJson = new InvoiceItemJson(invoiceItemId, invoiceId, linkedInvoiceItemId, accountId, childAccountId,
                                                                                      bundleId, subscriptionId, planName, phaseName, usageName, type, description,
                                                                                      startDate, endDate, amount, currency.name(), null, auditLogs);
        Assert.assertEquals(invoiceItemJson.getInvoiceItemId(), invoiceItemId);
        Assert.assertEquals(invoiceItemJson.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceItemJson.getLinkedInvoiceItemId(), linkedInvoiceItemId);
        Assert.assertEquals(invoiceItemJson.getAccountId(), accountId);
        Assert.assertEquals(invoiceItemJson.getChildAccountId(), childAccountId);
        Assert.assertEquals(invoiceItemJson.getBundleId(), bundleId);
        Assert.assertEquals(invoiceItemJson.getSubscriptionId(), subscriptionId);
        Assert.assertEquals(invoiceItemJson.getPlanName(), planName);
        Assert.assertEquals(invoiceItemJson.getPhaseName(), phaseName);
        Assert.assertEquals(invoiceItemJson.getUsageName(), usageName);
        Assert.assertEquals(invoiceItemJson.getItemType(), type);
        Assert.assertEquals(invoiceItemJson.getDescription(), description);
        Assert.assertEquals(invoiceItemJson.getStartDate(), startDate);
        Assert.assertEquals(invoiceItemJson.getEndDate(), endDate);
        Assert.assertEquals(invoiceItemJson.getAmount(), amount);
        Assert.assertEquals(invoiceItemJson.getCurrency(), currency.name());
        Assert.assertEquals(invoiceItemJson.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(invoiceItemJson);
        final InvoiceItemJson fromJson = mapper.readValue(asJson, InvoiceItemJson.class);
        Assert.assertEquals(fromJson, invoiceItemJson);
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
        Mockito.when(invoiceItem.getUsageName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getStartDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoiceItem.getEndDate()).thenReturn(clock.getUTCToday());
        Mockito.when(invoiceItem.getAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.EUR);
        Mockito.when(invoiceItem.getInvoiceItemType()).thenReturn(InvoiceItemType.FIXED);

        final InvoiceItemJson invoiceItemJson = new InvoiceItemJson(invoiceItem);
        Assert.assertEquals(invoiceItemJson.getInvoiceItemId(), invoiceItem.getId().toString());
        Assert.assertEquals(invoiceItemJson.getInvoiceId(), invoiceItem.getInvoiceId().toString());
        Assert.assertEquals(invoiceItemJson.getLinkedInvoiceItemId(), invoiceItem.getLinkedItemId().toString());
        Assert.assertEquals(invoiceItemJson.getAccountId(), invoiceItem.getAccountId().toString());
        Assert.assertEquals(invoiceItemJson.getBundleId(), invoiceItem.getBundleId().toString());
        Assert.assertEquals(invoiceItemJson.getSubscriptionId(), invoiceItem.getSubscriptionId().toString());
        Assert.assertEquals(invoiceItemJson.getPlanName(), invoiceItem.getPlanName());
        Assert.assertEquals(invoiceItemJson.getPhaseName(), invoiceItem.getPhaseName());
        Assert.assertEquals(invoiceItemJson.getUsageName(), invoiceItem.getUsageName());
        Assert.assertEquals(invoiceItemJson.getDescription(), invoiceItem.getDescription());
        Assert.assertEquals(invoiceItemJson.getStartDate(), invoiceItem.getStartDate());
        Assert.assertEquals(invoiceItemJson.getEndDate(), invoiceItem.getEndDate());
        Assert.assertEquals(invoiceItemJson.getAmount(), invoiceItem.getAmount());
        Assert.assertEquals(invoiceItemJson.getCurrency(), invoiceItem.getCurrency().name());
    }
}
