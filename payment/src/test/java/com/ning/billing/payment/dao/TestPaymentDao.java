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

import com.ning.billing.payment.api.DefaultPaymentAttempt;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentAttempt.PaymentAttemptStatus;
import com.ning.billing.payment.api.PaymentInfoEvent;

public abstract class TestPaymentDao {

    protected PaymentDao paymentDao;
    protected CallContext context = new TestCallContext("PaymentTests");

    @Test(groups={"slow"})
    public void testCreatePayment() {
        PaymentInfoEvent paymentInfo = new DefaultPaymentInfoEvent.Builder().setId(UUID.randomUUID())
                .setExternalPaymentId("40863fe3f6dca54")
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

    @Test(groups={"slow"})
    public void testUpdatePaymentInfo() {
        PaymentInfoEvent paymentInfo = new DefaultPaymentInfoEvent.Builder().setId(UUID.randomUUID())
                .setExternalPaymentId("40863fe3f6dca54")
                .setAmount(BigDecimal.TEN)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setEffectiveDate(new DefaultClock().getUTCNow())
                .build();

        CallContext context = new TestCallContext("PaymentTests");
        paymentDao.savePaymentInfo(paymentInfo, context);
        paymentDao.updatePaymentInfo("CreditCard", paymentInfo.getId(), "Visa", "US", context);
    }

    @Test(groups={"slow"})
    public void testUpdatePaymentAttempt() {
        PaymentAttempt paymentAttempt = new DefaultPaymentAttempt.Builder().setPaymentAttemptId(UUID.randomUUID())
                .setPaymentId(UUID.randomUUID())
                .setInvoiceId(UUID.randomUUID())
                .setAccountId(UUID.randomUUID())
                .setAmount(BigDecimal.TEN)
                .setCurrency(Currency.USD)
                .setInvoiceDate(context.getCreatedDate())
                .build();

        paymentDao.createPaymentAttempt(paymentAttempt, PaymentAttemptStatus.IN_PROCESSING, context);
    }

    @Test(groups={"slow"})
    public void testGetPaymentForInvoice() throws AccountApiException {
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentAttemptId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID paymentId = UUID.randomUUID();
        final BigDecimal invoiceAmount = BigDecimal.TEN;

        // Move the clock backwards to test the updated_date field (see below)
        ClockMock clock = new ClockMock();
        CallContext thisContext = new DefaultCallContext("Payment Tests", CallOrigin.TEST, UserType.TEST, clock);


        PaymentAttempt originalPaymentAttempt = new DefaultPaymentAttempt(paymentAttemptId, invoiceId, accountId, invoiceAmount, Currency.USD, clock.getUTCNow(), clock.getUTCNow(), paymentId, 0, null, null, PaymentAttemptStatus.IN_PROCESSING);
        PaymentAttempt attempt = paymentDao.createPaymentAttempt(originalPaymentAttempt, PaymentAttemptStatus.IN_PROCESSING, thisContext);


        List<PaymentAttempt> attemptsFromGet = paymentDao.getPaymentAttemptsForInvoiceId(invoiceId);

        Assert.assertEquals(attempt, attemptsFromGet.get(0));

        PaymentAttempt attempt3 = paymentDao.getPaymentAttemptsForInvoiceIds(Arrays.asList(invoiceId)).get(0);

        Assert.assertEquals(attempt, attempt3);

        PaymentAttempt attempt4 = paymentDao.getPaymentAttemptById(attempt3.getId());

        Assert.assertEquals(attempt3, attempt4);

        PaymentInfoEvent originalPaymentInfo = new DefaultPaymentInfoEvent.Builder().setId(paymentId)
                .setExternalPaymentId("test test")
                .setAmount(invoiceAmount)
                .setStatus("Processed")
                .setBankIdentificationNumber("1234")
                .setPaymentNumber("12345")
                .setPaymentMethodId("12345")
                .setReferenceId("12345")
                .setType("Electronic")
                .setEffectiveDate(clock.getUTCNow())
                .build();

        paymentDao.savePaymentInfo(originalPaymentInfo, thisContext);
        paymentDao.updatePaymentAttemptWithPaymentId(originalPaymentAttempt.getId(), originalPaymentInfo.getId(), thisContext);
        PaymentInfoEvent paymentInfo = paymentDao.getPaymentInfoList(Arrays.asList(invoiceId)).get(0);
        Assert.assertEquals(paymentInfo, originalPaymentInfo);

        clock.setDeltaFromReality(60 * 60 * 1000); // move clock forward one hour
        paymentDao.updatePaymentInfo(originalPaymentInfo.getPaymentMethod(), originalPaymentInfo.getId(), originalPaymentInfo.getCardType(), originalPaymentInfo.getCardCountry(), thisContext);
        paymentInfo = paymentDao.getPaymentInfoList(Arrays.asList(invoiceId)).get(0);
        // TODO: replace these asserts
//        Assert.assertEquals(paymentInfo.getCreatedDate().compareTo(attempt.getCreatedDate()), 0);
//        Assert.assertTrue(paymentInfo.getUpdatedDate().isAfter(originalPaymentInfo.getUpdatedDate()));
    }
}
