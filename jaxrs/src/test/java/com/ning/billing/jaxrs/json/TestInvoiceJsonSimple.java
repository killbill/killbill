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
import com.ning.billing.invoice.api.Invoice;

public class TestInvoiceJsonSimple {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JodaModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final BigDecimal amount = BigDecimal.TEN;
        final BigDecimal credit = BigDecimal.ONE;
        final String invoiceId = UUID.randomUUID().toString();
        final DateTime invoiceDate = new DateTime(DateTimeZone.UTC);
        final DateTime targetDate = new DateTime(DateTimeZone.UTC);
        final String invoiceNumber = UUID.randomUUID().toString();
        final BigDecimal balance = BigDecimal.ZERO;
        final String accountId = UUID.randomUUID().toString();
        final InvoiceJsonSimple invoiceJsonSimple = new InvoiceJsonSimple(amount, credit, invoiceId, invoiceDate,
                                                                          targetDate, invoiceNumber, balance, accountId);
        Assert.assertEquals(invoiceJsonSimple.getAmount(), amount);
        Assert.assertEquals(invoiceJsonSimple.getCredit(), credit);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceDate(), invoiceDate);
        Assert.assertEquals(invoiceJsonSimple.getTargetDate(), targetDate);
        Assert.assertEquals(invoiceJsonSimple.getInvoiceNumber(), invoiceNumber);
        Assert.assertEquals(invoiceJsonSimple.getBalance(), balance);
        Assert.assertEquals(invoiceJsonSimple.getAccountId(), accountId);

        final String asJson = mapper.writeValueAsString(invoiceJsonSimple);
        Assert.assertEquals(asJson, "{\"amount\":" + invoiceJsonSimple.getAmount().toString() + "," +
                "\"credit\":" + invoiceJsonSimple.getCredit().toString() + "," +
                "\"invoiceId\":\"" + invoiceJsonSimple.getInvoiceId() + "\"," +
                "\"invoiceDate\":\"" + invoiceJsonSimple.getInvoiceDate().toDateTimeISO().toString() + "\"," +
                "\"targetDate\":\"" + invoiceJsonSimple.getTargetDate().toDateTimeISO().toString() + "\"," +
                "\"invoiceNumber\":\"" + invoiceJsonSimple.getInvoiceNumber() + "\"," +
                "\"balance\":" + invoiceJsonSimple.getBalance().toString() + "," +
                "\"accountId\":\"" + invoiceJsonSimple.getAccountId() + "\"}");

        final InvoiceJsonSimple fromJson = mapper.readValue(asJson, InvoiceJsonSimple.class);
        Assert.assertEquals(fromJson, invoiceJsonSimple);
    }

    @Test(groups = "fast")
    public void testFromInvoice() throws Exception {
        final Invoice invoice = Mockito.mock(Invoice.class);
        Mockito.when(invoice.getAmountCharged()).thenReturn(BigDecimal.TEN);
        Mockito.when(invoice.getAmountCredited()).thenReturn(BigDecimal.ONE);
        Mockito.when(invoice.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(invoice.getInvoiceDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(invoice.getTargetDate()).thenReturn(new DateTime(DateTimeZone.UTC));
        Mockito.when(invoice.getInvoiceNumber()).thenReturn(Integer.MAX_VALUE);
        Mockito.when(invoice.getBalance()).thenReturn(BigDecimal.ZERO);
        Mockito.when(invoice.getAccountId()).thenReturn(UUID.randomUUID());

        final InvoiceJsonSimple invoiceJsonSimple = new InvoiceJsonSimple(invoice);
        Assert.assertEquals(invoiceJsonSimple.getAmount(), invoice.getAmountCharged());
        Assert.assertEquals(invoiceJsonSimple.getCredit(), invoice.getAmountCredited());
        Assert.assertEquals(invoiceJsonSimple.getInvoiceId(), invoice.getId().toString());
        Assert.assertEquals(invoiceJsonSimple.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoiceJsonSimple.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(invoiceJsonSimple.getInvoiceNumber(), String.valueOf(invoice.getInvoiceNumber()));
        Assert.assertEquals(invoiceJsonSimple.getBalance(), invoice.getBalance());
        Assert.assertEquals(invoiceJsonSimple.getAccountId(), invoice.getAccountId().toString());
    }
}
