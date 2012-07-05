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

package com.ning.billing.analytics.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.TestWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoicePayment;
import com.ning.billing.catalog.api.Currency;

public class TestBusinessInvoicePaymentSqlDao extends TestWithEmbeddedDB {
    private BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoicePaymentSqlDao = dbi.onDemand(BusinessInvoicePaymentSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final UUID attemptId = UUID.randomUUID();
        final String accountKey = UUID.randomUUID().toString();
        final BusinessInvoicePayment invoicePayment = createInvoicePayment(attemptId, accountKey);

        // Verify initial state
        Assert.assertNull(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment.getAttemptId().toString()));
        Assert.assertEquals(invoicePaymentSqlDao.deleteInvoicePaymentForPaymentAttempt(invoicePayment.getAttemptId().toString()), 0);

        // Add the invoice payment
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment), 1);

        // Retrieve it
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment.getAttemptId().toString()), invoicePayment);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey()).get(0), invoicePayment);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment.getPaymentId().toString()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment.getPaymentId().toString()).get(0), invoicePayment);

        // Delete it
        Assert.assertEquals(invoicePaymentSqlDao.deleteInvoicePaymentForPaymentAttempt(invoicePayment.getAttemptId().toString()), 1);
        Assert.assertNull(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment.getAttemptId().toString()));
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey()).size(), 0);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment.getPaymentId().toString()).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final UUID attemptId1 = UUID.randomUUID();
        final String accountKey1 = UUID.randomUUID().toString();
        final BusinessInvoicePayment invoicePayment1 = createInvoicePayment(attemptId1, accountKey1);
        final UUID attemptId2 = UUID.randomUUID();
        final String accountKey2 = UUID.randomUUID().toString();
        final BusinessInvoicePayment invoicePayment2 = createInvoicePayment(attemptId2, accountKey2);

        // Create both invoice payments
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment1), 1);
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment2), 1);

        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment1.getAttemptId().toString()), invoicePayment1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment2.getAttemptId().toString()), invoicePayment2);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment1.getAccountKey()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment2.getAccountKey()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment1.getPaymentId().toString()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment2.getPaymentId().toString()).size(), 1);

        // Remove the first invoice payment
        Assert.assertEquals(invoicePaymentSqlDao.deleteInvoicePaymentForPaymentAttempt(invoicePayment1.getAttemptId().toString()), 1);

        Assert.assertNull(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment1.getAttemptId().toString()));
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentForPaymentAttempt(invoicePayment2.getAttemptId().toString()), invoicePayment2);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment1.getAccountKey()).size(), 0);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment2.getAccountKey()).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment1.getPaymentId().toString()).size(), 0);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForPayment(invoicePayment2.getPaymentId().toString()).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoicePaymentSqlDao.test();
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessInvoicePayment createInvoicePayment(final UUID attemptId, final String accountKey) {
        final BigDecimal amount = BigDecimal.ONE;
        final String cardCountry = UUID.randomUUID().toString().substring(0, 20);
        final String cardType = UUID.randomUUID().toString().substring(0, 20);
        final DateTime createdDate = new DateTime(DateTimeZone.UTC);
        final Currency currency = Currency.BRL;
        final DateTime effectiveDate = new DateTime(DateTimeZone.UTC);
        final UUID invoiceId = UUID.randomUUID();
        final String paymentError = UUID.randomUUID().toString();
        final UUID paymentId = UUID.randomUUID();
        final String paymentMethod = UUID.randomUUID().toString().substring(0, 20);
        final String paymentType = UUID.randomUUID().toString().substring(0, 20);
        final String pluginName = UUID.randomUUID().toString().substring(0, 20);
        final String processingStatus = UUID.randomUUID().toString();
        final BigDecimal requestedAmount = BigDecimal.ZERO;
        final DateTime updatedDate = new DateTime(DateTimeZone.UTC);

        return new BusinessInvoicePayment(accountKey, amount, attemptId,
                                          cardCountry, cardType, createdDate,
                                          currency, effectiveDate, invoiceId,
                                          paymentError, paymentId, paymentMethod,
                                          paymentType, pluginName, processingStatus,
                                          requestedAmount, updatedDate);
    }
}
