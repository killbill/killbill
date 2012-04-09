/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;

public abstract class TestPaymentDao {

    protected PaymentDao paymentDao;

    @Test
    public void testCreatePayment() {
        PaymentInfo paymentInfo = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                .setAmount(BigDecimal.TEN)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setCreatedDate(new DateTime(DateTimeZone.UTC))
                .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                .build();

        paymentDao.savePaymentInfo(paymentInfo);
    }

    @Test
    public void testUpdatePaymenInfo() {
        PaymentInfo paymentInfo = new PaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                .setAmount(BigDecimal.TEN)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setCreatedDate(new DateTime(DateTimeZone.UTC))
                .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                .setEffectiveDate(new DateTime(DateTimeZone.UTC))
                .build();

        paymentDao.savePaymentInfo(paymentInfo);

        paymentDao.updatePaymentInfo("CreditCard", paymentInfo.getPaymentId(), "Visa", "US");
    }

    @Test
    public void testUpdatePaymentAttempt() {
        PaymentAttempt paymentAttempt = new PaymentAttempt.Builder().setPaymentAttemptId(UUID.randomUUID())
                .setPaymentId(UUID.randomUUID().toString())
                .setInvoiceId(UUID.randomUUID())
                .setAccountId(UUID.randomUUID())
                .setAmount(BigDecimal.TEN)
                .setCurrency(Currency.USD)
                .setInvoiceDate(new DateTime(DateTimeZone.UTC))
                .setCreatedDate(new DateTime(DateTimeZone.UTC))
                .setUpdatedDate(new DateTime(DateTimeZone.UTC))
                .build();

        paymentDao.createPaymentAttempt(paymentAttempt);
    }

    @Test
    public void testGetPaymentForInvoice() throws AccountApiException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentAttemptId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String paymentId = UUID.randomUUID().toString();
        final BigDecimal invoiceAmount = BigDecimal.TEN;

        // Move the clock backwards to test the updated_date field (see below)
        final DateTime now = new DateTime(DateTimeZone.UTC).minusDays(1);

        PaymentAttempt originalPaymenAttempt = new PaymentAttempt(paymentAttemptId, invoiceId, accountId, invoiceAmount, Currency.USD, now, now, paymentId, 0);

        PaymentAttempt attempt = paymentDao.createPaymentAttempt(originalPaymenAttempt);

        PaymentAttempt attempt2 = paymentDao.getPaymentAttemptForInvoiceId(invoiceId.toString());

        Assert.assertEquals(attempt, attempt2);

        PaymentAttempt attempt3 = paymentDao.getPaymentAttemptsForInvoiceIds(Arrays.asList(invoiceId.toString())).get(0);

        Assert.assertEquals(attempt, attempt3);

        PaymentAttempt attempt4 = paymentDao.getPaymentAttemptById(attempt3.getPaymentAttemptId());

        Assert.assertEquals(attempt3, attempt4);

        PaymentInfo originalPaymentInfo = new PaymentInfo.Builder().setPaymentId(paymentId)
                .setAmount(invoiceAmount)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setCreatedDate(now)
                .setUpdatedDate(now)
                .setEffectiveDate(now)
                .build();

        paymentDao.savePaymentInfo(originalPaymentInfo);
        PaymentInfo paymentInfo = paymentDao.getPaymentInfo(Arrays.asList(invoiceId.toString())).get(0);
        Assert.assertEquals(paymentInfo, originalPaymentInfo);

        paymentDao.updatePaymentInfo(originalPaymentInfo.getPaymentMethod(), originalPaymentInfo.getPaymentId(), originalPaymentInfo.getCardType(), originalPaymentInfo.getCardCountry());
        paymentInfo = paymentDao.getPaymentInfo(Arrays.asList(invoiceId.toString())).get(0);
        Assert.assertEquals(paymentInfo.getCreatedDate().getMillis() / 1000, originalPaymentInfo.getCreatedDate().getMillis() / 1000);
        Assert.assertTrue(paymentInfo.getUpdatedDate().isAfter(originalPaymentInfo.getUpdatedDate()));
    }
}
