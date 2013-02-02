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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;
import com.ning.billing.catalog.api.Currency;

public class TestBusinessInvoicePayment extends AnalyticsTestSuite {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final String accountKey = UUID.randomUUID().toString();
        final BigDecimal amount = BigDecimal.ONE;
        final String extFirstPaymentRefId = UUID.randomUUID().toString();
        final String extSecondPaymentRefId = UUID.randomUUID().toString();
        final String cardCountry = UUID.randomUUID().toString();
        final String cardType = UUID.randomUUID().toString();
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.BRL;
        final DateTime effectiveDate = new DateTime(DateTimeZone.UTC);
        final UUID invoiceId = UUID.randomUUID();
        final String paymentError = UUID.randomUUID().toString();
        final UUID paymentId = UUID.randomUUID();
        final String paymentMethod = UUID.randomUUID().toString();
        final String paymentType = UUID.randomUUID().toString();
        final String pluginName = UUID.randomUUID().toString();
        final String processingStatus = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.ZERO;
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);
        final String invoicePaymentType = UUID.randomUUID().toString();
        final UUID linkedInvoicePaymentId = UUID.randomUUID();
        final BusinessInvoicePaymentModelDao invoicePayment = new BusinessInvoicePaymentModelDao(accountKey, amount, extFirstPaymentRefId, extSecondPaymentRefId,
                                                                                                 cardCountry, cardType, createdDate,
                                                                                                 currency, effectiveDate, invoiceId,
                                                                                                 paymentError, paymentId, paymentMethod,
                                                                                                 paymentType, pluginName, processingStatus,
                                                                                                 requestedAmount, updatedDate, invoicePaymentType,
                                                                                                 linkedInvoicePaymentId);
        Assert.assertSame(invoicePayment, invoicePayment);
        Assert.assertEquals(invoicePayment, invoicePayment);
        Assert.assertTrue(invoicePayment.equals(invoicePayment));
        Assert.assertEquals(invoicePayment.getAccountKey(), accountKey);
        Assert.assertEquals(invoicePayment.getAmount(), amount);
        Assert.assertEquals(invoicePayment.getExtFirstPaymentRefId(), extFirstPaymentRefId);
        Assert.assertEquals(invoicePayment.getExtSecondPaymentRefId(), extSecondPaymentRefId);
        Assert.assertEquals(invoicePayment.getCardCountry(), cardCountry);
        Assert.assertEquals(invoicePayment.getCardType(), cardType);
        Assert.assertEquals(invoicePayment.getCreatedDate(), createdDate);
        Assert.assertEquals(invoicePayment.getCurrency(), currency);
        Assert.assertEquals(invoicePayment.getEffectiveDate(), effectiveDate);
        Assert.assertEquals(invoicePayment.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoicePayment.getPaymentError(), paymentError);
        Assert.assertEquals(invoicePayment.getPaymentId(), paymentId);
        Assert.assertEquals(invoicePayment.getPaymentMethod(), paymentMethod);
        Assert.assertEquals(invoicePayment.getPaymentType(), paymentType);
        Assert.assertEquals(invoicePayment.getPluginName(), pluginName);
        Assert.assertEquals(invoicePayment.getProcessingStatus(), processingStatus);
        Assert.assertEquals(invoicePayment.getRequestedAmount(), requestedAmount);
        Assert.assertEquals(invoicePayment.getUpdatedDate(), updatedDate);
        Assert.assertEquals(invoicePayment.getInvoicePaymentType(), invoicePaymentType);
        Assert.assertEquals(invoicePayment.getLinkedInvoicePaymentId(), linkedInvoicePaymentId);

        final BusinessInvoicePaymentModelDao otherInvoicePayment = new BusinessInvoicePaymentModelDao(null, null, extFirstPaymentRefId, extSecondPaymentRefId, null, null, createdDate,
                                                                                                      null, null, null, null, paymentId, null,
                                                                                                      null, null, null, null, null, null, null);
        Assert.assertFalse(invoicePayment.equals(otherInvoicePayment));
    }
}
