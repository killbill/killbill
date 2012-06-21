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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;
import com.ning.billing.catalog.api.Currency;

public class TestBusinessInvoice extends AnalyticsTestSuite {
    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final BigDecimal amountCharged = BigDecimal.ZERO;
        final BigDecimal amountCredited = BigDecimal.ONE;
        final BigDecimal amountPaid = BigDecimal.TEN;
        final BigDecimal balance = BigDecimal.valueOf(123L);
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.MXN;
        final DateTime invoiceDate = new DateTime(DateTimeZone.UTC);
        final UUID invoiceId = UUID.randomUUID();
        final DateTime targetDate = new DateTime(DateTimeZone.UTC);
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);
        final BusinessInvoice invoice = new BusinessInvoice(accountKey, amountCharged, amountCredited, amountPaid, balance,
                                                            createdDate, currency, invoiceDate, invoiceId, targetDate, updatedDate);
        Assert.assertSame(invoice, invoice);
        Assert.assertEquals(invoice, invoice);
        Assert.assertTrue(invoice.equals(invoice));
        Assert.assertEquals(invoice.getAccountKey(), accountKey);
        Assert.assertEquals(invoice.getAmountCharged(), amountCharged);
        Assert.assertEquals(invoice.getAmountCredited(), amountCredited);
        Assert.assertEquals(invoice.getAmountPaid(), amountPaid);
        Assert.assertEquals(invoice.getBalance(), balance);
        Assert.assertEquals(invoice.getCreatedDate(), createdDate);
        Assert.assertEquals(invoice.getCurrency(), currency);
        Assert.assertEquals(invoice.getInvoiceDate(), invoiceDate);
        Assert.assertEquals(invoice.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoice.getTargetDate(), targetDate);
        Assert.assertEquals(invoice.getUpdatedDate(), updatedDate);

        final BusinessInvoice otherInvoice = new BusinessInvoice(null, null, null, null, null, createdDate, null,
                                                                 null, invoiceId, null, null);
        Assert.assertFalse(invoice.equals(otherInvoice));

        // Test setters
        otherInvoice.setAccountKey(accountKey);
        otherInvoice.setAmountCharged(amountCharged);
        otherInvoice.setAmountCredited(amountCredited);
        otherInvoice.setAmountPaid(amountPaid);
        otherInvoice.setBalance(balance);
        otherInvoice.setCurrency(currency);
        otherInvoice.setInvoiceDate(invoiceDate);
        otherInvoice.setTargetDate(targetDate);
        otherInvoice.setUpdatedDate(updatedDate);
        Assert.assertTrue(invoice.equals(otherInvoice));
    }
}
