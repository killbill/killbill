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

package com.ning.billing.analytics.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoicePaymentModelDao;
import com.ning.billing.catalog.api.Currency;

public class TestBusinessInvoicePaymentSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessInvoicePaymentSqlDao invoicePaymentSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoicePaymentSqlDao = dbi.onDemand(BusinessInvoicePaymentSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String extFirstPaymentRefId = UUID.randomUUID().toString();
        final String extSecondPaymentRefId = UUID.randomUUID().toString();
        final String accountKey = UUID.randomUUID().toString();
        final BusinessInvoicePaymentModelDao invoicePayment = createInvoicePayment(extFirstPaymentRefId, extSecondPaymentRefId, accountKey);

        // Verify initial state
        Assert.assertNull(invoicePaymentSqlDao.getInvoicePayment(invoicePayment.getPaymentId().toString(), internalCallContext));
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey(), internalCallContext).size(), 0);

        // Add the invoice payment
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment, internalCallContext), 1);

        // Retrieve it
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePayment(invoicePayment.getPaymentId().toString(), internalCallContext), invoicePayment);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey(), internalCallContext).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey(), internalCallContext).get(0), invoicePayment);

        // Delete it
        Assert.assertEquals(invoicePaymentSqlDao.deleteInvoicePayment(invoicePayment.getPaymentId().toString(), internalCallContext), 1);
        Assert.assertNull(invoicePaymentSqlDao.getInvoicePayment(invoicePayment.getPaymentId().toString(), internalCallContext));
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment.getAccountKey(), internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String extFirstPaymentRefId1 = UUID.randomUUID().toString();
        final String extSecondPaymentRefId1 = UUID.randomUUID().toString();
        final String accountKey1 = UUID.randomUUID().toString();
        final BusinessInvoicePaymentModelDao invoicePayment1 = createInvoicePayment(extFirstPaymentRefId1, extSecondPaymentRefId1, accountKey1);
        final String extFirstPaymentRefId2 = UUID.randomUUID().toString();
        final String extSecondPaymentRefId2 = UUID.randomUUID().toString();
        final String accountKey2 = UUID.randomUUID().toString();
        final BusinessInvoicePaymentModelDao invoicePayment2 = createInvoicePayment(extFirstPaymentRefId2, extSecondPaymentRefId2, accountKey2);

        // Create both invoice payments
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment1, internalCallContext), 1);
        Assert.assertEquals(invoicePaymentSqlDao.createInvoicePayment(invoicePayment2, internalCallContext), 1);

        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePayment(invoicePayment1.getPaymentId().toString(), internalCallContext), invoicePayment1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePayment(invoicePayment2.getPaymentId().toString(), internalCallContext), invoicePayment2);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment1.getAccountKey(), internalCallContext).size(), 1);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment2.getAccountKey(), internalCallContext).size(), 1);

        // Remove the first invoice payment
        Assert.assertEquals(invoicePaymentSqlDao.deleteInvoicePayment(invoicePayment1.getPaymentId().toString(), internalCallContext), 1);

        Assert.assertNull(invoicePaymentSqlDao.getInvoicePayment(invoicePayment1.getPaymentId().toString(), internalCallContext));
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePayment(invoicePayment2.getPaymentId().toString(), internalCallContext), invoicePayment2);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment1.getAccountKey(), internalCallContext).size(), 0);
        Assert.assertEquals(invoicePaymentSqlDao.getInvoicePaymentsForAccountByKey(invoicePayment2.getAccountKey(), internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoicePaymentSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    private BusinessInvoicePaymentModelDao createInvoicePayment(final String extFirstPaymentRefId, final String extSecondPaymentRefId, final String accountKey) {
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
        final String invoicePaymentType = UUID.randomUUID().toString().substring(0, 10);
        final UUID linkedInvoicePaymentId = UUID.randomUUID();

        return new BusinessInvoicePaymentModelDao(accountKey, amount, extFirstPaymentRefId, extSecondPaymentRefId,
                                                  cardCountry, cardType, createdDate,
                                                  currency, effectiveDate, invoiceId,
                                                  paymentError, paymentId, paymentMethod,
                                                  paymentType, pluginName, processingStatus,
                                                  requestedAmount, updatedDate, invoicePaymentType,
                                                  linkedInvoicePaymentId);
    }
}
