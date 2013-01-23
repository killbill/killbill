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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DBTestingHelper;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;
import com.ning.billing.util.dao.DefaultNonEntityDao;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestPaymentDao extends PaymentTestSuiteWithEmbeddedDB {

    private PaymentDao paymentDao;
    private DBTestingHelper helper;
    private IDBI dbi;
    private Clock clock;
    private CacheControllerDispatcher controllerDispatcher;


    @BeforeSuite(groups = "slow")
    public void setup() throws IOException {
        clock = new DefaultClock();
        controllerDispatcher = new CacheControllerDispatcher();
        setupDb();
        paymentDao = new DefaultPaymentDao(dbi, clock, controllerDispatcher, new DefaultNonEntityDao(dbi));
    }

    private void setupDb() {
        helper = KillbillTestSuiteWithEmbeddedDB.getDBTestingHelper();
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            final DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
    }

    @Test(groups = "slow")
    public void testRefund() {
        final UUID accountId = UUID.randomUUID();
        final UUID paymentId1 = UUID.randomUUID();
        final BigDecimal amount1 = new BigDecimal(13);
        final Currency currency = Currency.USD;

        final RefundModelDao refund1 = new RefundModelDao(accountId, paymentId1, amount1, currency, true);

        paymentDao.insertRefund(refund1, internalCallContext);
        final RefundModelDao refundCheck = paymentDao.getRefund(refund1.getId(), internalCallContext);
        assertNotNull(refundCheck);
        assertEquals(refundCheck.getAccountId(), accountId);
        assertEquals(refundCheck.getPaymentId(), paymentId1);
        assertEquals(refundCheck.getAmount().compareTo(amount1), 0);
        assertEquals(refundCheck.getCurrency(), currency);
        assertEquals(refundCheck.isAdjusted(), true);
        assertEquals(refundCheck.getRefundStatus(), RefundStatus.CREATED);

        final BigDecimal amount2 = new BigDecimal(7.00);
        final UUID paymentId2 = UUID.randomUUID();

        RefundModelDao refund2 = new RefundModelDao(accountId, paymentId2, amount2, currency, true);
        paymentDao.insertRefund(refund2, internalCallContext);
        paymentDao.updateRefundStatus(refund2.getId(), RefundStatus.COMPLETED, internalCallContext);

        List<RefundModelDao> refundChecks = paymentDao.getRefundsForPayment(paymentId1, internalCallContext);
        assertEquals(refundChecks.size(), 1);

        refundChecks = paymentDao.getRefundsForPayment(paymentId2, internalCallContext);
        assertEquals(refundChecks.size(), 1);

        refundChecks = paymentDao.getRefundsForAccount(accountId, internalCallContext);
        assertEquals(refundChecks.size(), 2);
        for (RefundModelDao cur : refundChecks) {
            if (cur.getPaymentId().equals(paymentId1)) {
                assertEquals(cur.getAmount().compareTo(amount1), 0);
                assertEquals(cur.getRefundStatus(), RefundStatus.CREATED);
            } else if (cur.getPaymentId().equals(paymentId2)) {
                assertEquals(cur.getAmount().compareTo(amount2), 0);
                assertEquals(cur.getRefundStatus(), RefundStatus.COMPLETED);
            } else {
                fail("Unexpected refund");
            }
        }
    }

    @Test(groups = "slow")
    public void testUpdateStatus() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, internalCallContext);

        final PaymentStatus paymentStatus = PaymentStatus.SUCCESS;
        final String gatewayErrorCode = "OK";

        paymentDao.updateStatusForPaymentWithAttempt(payment.getId(), paymentStatus, gatewayErrorCode, null, null, null, attempt.getId(), internalCallContext);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.SUCCESS);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 1);
        final PaymentAttemptModelDao savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedAttempt.getGatewayErrorCode(), gatewayErrorCode);
        assertEquals(savedAttempt.getRequestedAmount().compareTo(amount), 0);
    }

    @Test(groups = "slow")
    public void testPaymentWithAttempt() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);

        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, internalCallContext);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        PaymentAttemptModelDao savedAttempt = paymentDao.getPaymentAttempt(attempt.getId(), internalCallContext);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 1);
        savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getProcessingStatus(), PaymentStatus.UNKNOWN);

    }

    @Test(groups = "slow")
    public void testNewAttempt() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID paymentMethodId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, paymentMethodId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao firstAttempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, firstAttempt, internalCallContext);

        final PaymentModelDao lastPayment = paymentDao.getLastPaymentForPaymentMethod(accountId, paymentMethodId, internalCallContext);
        assertNotNull(lastPayment);
        assertEquals(lastPayment.getId(), payment.getId());
        assertEquals(lastPayment.getAccountId(), accountId);
        assertEquals(lastPayment.getInvoiceId(), invoiceId);
        assertEquals(lastPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(lastPayment.getAmount().compareTo(amount), 0);
        assertEquals(lastPayment.getCurrency(), currency);
        assertEquals(lastPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(lastPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final BigDecimal newAmount = new BigDecimal(15.23).setScale(2, RoundingMode.HALF_EVEN);
        final PaymentAttemptModelDao secondAttempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), newAmount);
        paymentDao.insertNewAttemptForPayment(payment.getId(), secondAttempt, internalCallContext);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId, internalCallContext);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), paymentMethodId);
        assertEquals(savedPayment.getAmount().compareTo(newAmount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId(), internalCallContext);
        assertEquals(attempts.size(), 2);
        final PaymentAttemptModelDao savedAttempt1 = attempts.get(0);
        assertEquals(savedAttempt1.getPaymentId(), payment.getId());
        assertEquals(savedAttempt1.getAccountId(), accountId);
        assertEquals(savedAttempt1.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt1.getProcessingStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedAttempt1.getGatewayErrorCode(), null);
        assertEquals(savedAttempt1.getGatewayErrorMsg(), null);
        assertEquals(savedAttempt1.getRequestedAmount().compareTo(amount), 0);

        final PaymentAttemptModelDao savedAttempt2 = attempts.get(1);
        assertEquals(savedAttempt2.getPaymentId(), payment.getId());
        assertEquals(savedAttempt2.getAccountId(), accountId);
        assertEquals(savedAttempt2.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt2.getProcessingStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedAttempt2.getGatewayErrorCode(), null);
        assertEquals(savedAttempt2.getGatewayErrorMsg(), null);
        assertEquals(savedAttempt2.getRequestedAmount().compareTo(newAmount), 0);
    }

    @Test(groups = "slow")
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;
        final String externalPaymentId = UUID.randomUUID().toString();

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, null, null,
                                                                       accountId, pluginName, isActive, externalPaymentId);

        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, internalCallContext);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        final List<PaymentMethodModelDao> result = paymentDao.getPaymentMethods(accountId, internalCallContext);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);
        assertEquals(savedMethod.getExternalId(), externalPaymentId);

        paymentDao.deletedPaymentMethod(paymentMethodId, internalCallContext);

        final PaymentMethodModelDao deletedPaymentMethod = paymentDao.getPaymentMethod(paymentMethodId, internalCallContext);
        assertNull(deletedPaymentMethod);
    }
}
