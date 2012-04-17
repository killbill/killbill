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
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.DefaultPaymentInfo;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfoEvent;

public abstract class TestPaymentDao {
    protected PaymentDao paymentDao;
    protected CallContext context = new TestCallContext("PaymentTests");

    @Test
    public void testCreatePayment() {
        PaymentInfoEvent paymentInfo = new DefaultPaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                .setAmount(BigDecimal.TEN)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setEffectiveDate(new DefaultClock().getUTCNow())
                .build();

        paymentDao.savePaymentInfo(paymentInfo, context);
    }

    @Test
    public void testUpdatePaymentInfo() {
        PaymentInfoEvent paymentInfo = new DefaultPaymentInfo.Builder().setPaymentId(UUID.randomUUID().toString())
                .setAmount(BigDecimal.TEN)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setCreatedDate(new DefaultClock().getUTCNow())
                .setUpdatedDate(new DefaultClock().getUTCNow())
                .setEffectiveDate(new DefaultClock().getUTCNow())
                .build();

        CallContext context = new TestCallContext("PaymentTests");
        paymentDao.savePaymentInfo(paymentInfo, context);
        paymentDao.updatePaymentInfo("CreditCard", paymentInfo.getPaymentId(), "Visa", "US", context);
    }

    @Test
    public void testUpdatePaymentAttempt() {
        PaymentAttempt paymentAttempt = new PaymentAttempt.Builder().setPaymentAttemptId(UUID.randomUUID())
                .setPaymentId(UUID.randomUUID().toString())
                .setInvoiceId(UUID.randomUUID())
                .setAccountId(UUID.randomUUID())
                .setAmount(BigDecimal.TEN)
                .setCurrency(Currency.USD)
                .setInvoiceDate(context.getCreatedDate())
                .build();

        paymentDao.createPaymentAttempt(paymentAttempt, context);
    }

    @Test
    public void testGetPaymentForInvoice() throws AccountApiException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentAttemptId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String paymentId = UUID.randomUUID().toString();
        final BigDecimal invoiceAmount = BigDecimal.TEN;

        // Move the clock backwards to test the updated_date field (see below)
        ClockMock clock = new ClockMock();
        CallContext thisContext = new DefaultCallContext("Payment Tests", CallOrigin.TEST, UserType.TEST, clock);

        PaymentAttempt originalPaymentAttempt = new PaymentAttempt(paymentAttemptId, invoiceId, accountId, invoiceAmount, Currency.USD, clock.getUTCNow(), clock.getUTCNow(), paymentId, 0);
        PaymentAttempt attempt = paymentDao.createPaymentAttempt(originalPaymentAttempt, thisContext);

        List<PaymentAttempt> attemptsFromGet = paymentDao.getPaymentAttemptsForInvoiceId(invoiceId.toString());

        Assert.assertEquals(attempt, attemptsFromGet.get(0));

        PaymentAttempt attempt3 = paymentDao.getPaymentAttemptsForInvoiceIds(Arrays.asList(invoiceId.toString())).get(0);

        Assert.assertEquals(attempt, attempt3);

        PaymentAttempt attempt4 = paymentDao.getPaymentAttemptById(attempt3.getPaymentAttemptId());

        Assert.assertEquals(attempt3, attempt4);

        PaymentInfoEvent originalPaymentInfo = new DefaultPaymentInfo.Builder().setPaymentId(paymentId)
                .setAmount(invoiceAmount)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setCreatedDate(clock.getUTCNow())
                .setUpdatedDate(clock.getUTCNow())
                .setEffectiveDate(clock.getUTCNow())
                .build();

        paymentDao.savePaymentInfo(originalPaymentInfo, thisContext);
        PaymentInfoEvent paymentInfo = paymentDao.getPaymentInfo(Arrays.asList(invoiceId.toString())).get(0);
        Assert.assertEquals(paymentInfo, originalPaymentInfo);

        clock.setDeltaFromReality(60 * 60 * 1000); // move clock forward one hour
        paymentDao.updatePaymentInfo(originalPaymentInfo.getPaymentMethod(), originalPaymentInfo.getPaymentId(), originalPaymentInfo.getCardType(), originalPaymentInfo.getCardCountry(), thisContext);
        paymentInfo = paymentDao.getPaymentInfo(Arrays.asList(invoiceId.toString())).get(0);
        Assert.assertEquals(paymentInfo.getCreatedDate().compareTo(attempt.getCreatedDate()), 0);
        Assert.assertTrue(paymentInfo.getUpdatedDate().isAfter(originalPaymentInfo.getUpdatedDate()));
    }
}
