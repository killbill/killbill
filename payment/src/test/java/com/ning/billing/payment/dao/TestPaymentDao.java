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

import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.IDBI;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TestCallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

import static junit.framework.Assert.assertNull;
import static org.testng.Assert.assertEquals;

public class TestPaymentDao {

    private static final CallContext context = new TestCallContext("PaymentTests");

    private PaymentDao paymentDao;
    private MysqlTestingHelper helper;
    private IDBI dbi;
    private Clock clock;

    @BeforeSuite(groups = {"slow"})
    public void startMysql() throws IOException {
        final String paymentddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/payment/ddl.sql"));
        final String utilddl = IOUtils.toString(MysqlTestingHelper.class.getResourceAsStream("/com/ning/billing/util/ddl.sql"));


        clock = new DefaultClock();

        setupDb();

        helper.startMysql();
        helper.initDb(paymentddl);
        helper.initDb(utilddl);

        paymentDao = new AuditedPaymentDao(dbi, null);
    }

    private void setupDb() {
        helper = new MysqlTestingHelper();
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            final DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
    }

    @AfterSuite(groups = {"slow"})
    public void stopMysql() {
        helper.stopMysql();
    }

    @BeforeTest(groups = {"slow"})
    public void cleanupDb() {
        helper.cleanupAllTables();
    }


    @Test(groups = {"slow"})
    public void testUpdateStatus() {

        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, true, context);

        final PaymentStatus paymentStatus = PaymentStatus.SUCCESS;
        final String paymentError = "No error";

        paymentDao.updateStatusForPaymentWithAttempt(payment.getId(), paymentStatus, paymentError, attempt.getId(), context);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), null);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.SUCCESS);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId());
        assertEquals(attempts.size(), 1);
        final PaymentAttemptModelDao savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.SUCCESS);
        assertEquals(savedAttempt.getPaymentError(), paymentError);
        assertEquals(savedAttempt.getRequestedAmount().compareTo(amount), 0);
    }

    @Test(groups = {"slow"})
    public void testPaymentWithAttempt() {

        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao attempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);

        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, attempt, true, context);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), null);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);


        PaymentAttemptModelDao savedAttempt = paymentDao.getPaymentAttempt(attempt.getId());
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), null);
        assertEquals(savedPayment.getAmount().compareTo(amount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId());
        assertEquals(attempts.size(), 1);
        savedAttempt = attempts.get(0);
        assertEquals(savedAttempt.getId(), attempt.getId());
        assertEquals(savedAttempt.getPaymentId(), payment.getId());
        assertEquals(savedAttempt.getAccountId(), accountId);
        assertEquals(savedAttempt.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt.getPaymentStatus(), PaymentStatus.UNKNOWN);

    }

    @Test(groups = {"slow"})
    public void testNewAttempt() {
        final UUID accountId = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final BigDecimal amount = new BigDecimal(13);
        final Currency currency = Currency.USD;
        final DateTime effectiveDate = clock.getUTCNow();

        final PaymentModelDao payment = new PaymentModelDao(accountId, invoiceId, amount, currency, effectiveDate);
        final PaymentAttemptModelDao firstAttempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), amount);
        PaymentModelDao savedPayment = paymentDao.insertPaymentWithAttempt(payment, firstAttempt, true, context);

        final BigDecimal newAmount = new BigDecimal(15.23).setScale(2, RoundingMode.HALF_EVEN);
        final PaymentAttemptModelDao secondAttempt = new PaymentAttemptModelDao(accountId, invoiceId, payment.getId(), clock.getUTCNow(), newAmount);
        paymentDao.insertNewAttemptForPayment(payment.getId(), secondAttempt, true, context);

        final List<PaymentModelDao> payments = paymentDao.getPaymentsForInvoice(invoiceId);
        assertEquals(payments.size(), 1);
        savedPayment = payments.get(0);
        assertEquals(savedPayment.getId(), payment.getId());
        assertEquals(savedPayment.getAccountId(), accountId);
        assertEquals(savedPayment.getInvoiceId(), invoiceId);
        assertEquals(savedPayment.getPaymentMethodId(), null);
        assertEquals(savedPayment.getAmount().compareTo(newAmount), 0);
        assertEquals(savedPayment.getCurrency(), currency);
        assertEquals(savedPayment.getEffectiveDate().compareTo(effectiveDate), 0);
        assertEquals(savedPayment.getPaymentStatus(), PaymentStatus.UNKNOWN);

        final List<PaymentAttemptModelDao> attempts = paymentDao.getAttemptsForPayment(payment.getId());
        assertEquals(attempts.size(), 2);
        final PaymentAttemptModelDao savedAttempt1 = attempts.get(0);
        assertEquals(savedAttempt1.getPaymentId(), payment.getId());
        assertEquals(savedAttempt1.getAccountId(), accountId);
        assertEquals(savedAttempt1.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt1.getPaymentStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedAttempt1.getPaymentError(), null);
        assertEquals(savedAttempt1.getRequestedAmount().compareTo(amount), 0);


        final PaymentAttemptModelDao savedAttempt2 = attempts.get(1);
        assertEquals(savedAttempt2.getPaymentId(), payment.getId());
        assertEquals(savedAttempt2.getAccountId(), accountId);
        assertEquals(savedAttempt2.getInvoiceId(), invoiceId);
        assertEquals(savedAttempt2.getPaymentStatus(), PaymentStatus.UNKNOWN);
        assertEquals(savedAttempt2.getPaymentError(), null);
        assertEquals(savedAttempt2.getRequestedAmount().compareTo(newAmount), 0);
    }

    @Test(groups = {"slow"})
    public void testPaymentMethod() {

        final UUID paymentMethodId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final String pluginName = "nobody";
        final Boolean isActive = Boolean.TRUE;
        final String externalPaymentId = UUID.randomUUID().toString();

        final PaymentMethodModelDao method = new PaymentMethodModelDao(paymentMethodId, accountId, pluginName, isActive, externalPaymentId);

        PaymentMethodModelDao savedMethod = paymentDao.insertPaymentMethod(method, context);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);

        final List<PaymentMethodModelDao> result = paymentDao.getPaymentMethods(accountId);
        assertEquals(result.size(), 1);
        savedMethod = result.get(0);
        assertEquals(savedMethod.getId(), paymentMethodId);
        assertEquals(savedMethod.getAccountId(), accountId);
        assertEquals(savedMethod.getPluginName(), pluginName);
        assertEquals(savedMethod.isActive(), isActive);
        assertEquals(savedMethod.getExternalId(), externalPaymentId);

        paymentDao.deletedPaymentMethod(paymentMethodId);

        final PaymentMethodModelDao deletedPaymentMethod = paymentDao.getPaymentMethod(paymentMethodId);
        assertNull(deletedPaymentMethod);


    }
}
