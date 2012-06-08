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
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;

public class TestInvoiceItemJsonSimple {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final String planName = UUID.randomUUID().toString();
        final String phaseName = UUID.randomUUID().toString();
        final String description = UUID.randomUUID().toString();
        final DateTime startDate = new DateTime(DateTimeZone.UTC);
        final DateTime endDate = new DateTime(DateTimeZone.UTC);
        final BigDecimal amount = BigDecimal.TEN;
        final Currency currency = Currency.MXN;
        final InvoiceItemJsonSimple invoiceItemJsonSimple = new InvoiceItemJsonSimple(invoiceId, accountId, bundleId, subscriptionId,
                                                                                      planName, phaseName, description, startDate, endDate,
                                                                                      amount, currency);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceId(), invoiceId);
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

        final String asJson = mapper.writeValueAsString(invoiceItemJsonSimple);
        Assert.assertEquals(asJson, "{\"invoiceId\":\"" + invoiceItemJsonSimple.getInvoiceId().toString() + "\"," +
                "\"accountId\":\"" + invoiceItemJsonSimple.getAccountId().toString() + "\"," +
                "\"bundleId\":\"" + invoiceItemJsonSimple.getBundleId().toString() + "\"," +
                "\"subscriptionId\":\"" + invoiceItemJsonSimple.getSubscriptionId().toString() + "\"," +
                "\"planName\":\"" + invoiceItemJsonSimple.getPlanName() + "\"," +
                "\"phaseName\":\"" + invoiceItemJsonSimple.getPhaseName() + "\"," +
                "\"description\":\"" + invoiceItemJsonSimple.getDescription() + "\"," +
                "\"startDate\":\"" + invoiceItemJsonSimple.getStartDate().toDateTimeISO().toString() + "\"," +
                "\"endDate\":\"" + invoiceItemJsonSimple.getEndDate().toDateTimeISO().toString() + "\"," +
                "\"amount\":" + invoiceItemJsonSimple.getAmount().toString() + "," +
                "\"currency\":\"" + invoiceItemJsonSimple.getCurrency().toString() + "\"}");

        final InvoiceItemJsonSimple fromJson = mapper.readValue(asJson, InvoiceItemJsonSimple.class);
        Assert.assertEquals(fromJson, invoiceItemJsonSimple);
    }

    @Test(groups = "fast")
    public void testFromInvoiceItem() throws Exception {
        final InvoiceItem invoiceItem = Mockito.mock(InvoiceItem.class);
        Mockito.when(invoiceItem.getInvoiceId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getAccountId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getBundleId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getSubscriptionId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoiceItem.getPlanName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getPhaseName()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getDescription()).thenReturn(UUID.randomUUID().toString());
        Mockito.when(invoiceItem.getStartDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(invoiceItem.getEndDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(invoiceItem.getAmount()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoiceItem.getCurrency()).thenReturn(Currency.EUR);

        final InvoiceItemJsonSimple invoiceItemJsonSimple = new InvoiceItemJsonSimple(invoiceItem);
        Assert.assertEquals(invoiceItemJsonSimple.getInvoiceId(), invoiceItem.getInvoiceId());
        Assert.assertEquals(invoiceItemJsonSimple.getAccountId(), invoiceItem.getAccountId());
        Assert.assertEquals(invoiceItemJsonSimple.getBundleId(), invoiceItem.getBundleId());
        Assert.assertEquals(invoiceItemJsonSimple.getSubscriptionId(), invoiceItem.getSubscriptionId());
        Assert.assertEquals(invoiceItemJsonSimple.getPlanName(), invoiceItem.getPlanName());
        Assert.assertEquals(invoiceItemJsonSimple.getPhaseName(), invoiceItem.getPhaseName());
        Assert.assertEquals(invoiceItemJsonSimple.getDescription(), invoiceItem.getDescription());
        Assert.assertEquals(invoiceItemJsonSimple.getStartDate(), invoiceItem.getStartDate());
        Assert.assertEquals(invoiceItemJsonSimple.getEndDate(), invoiceItem.getEndDate());
        Assert.assertEquals(invoiceItemJsonSimple.getAmount(), invoiceItem.getAmount());
        Assert.assertEquals(invoiceItemJsonSimple.getCurrency(), invoiceItem.getCurrency());
    }
}
