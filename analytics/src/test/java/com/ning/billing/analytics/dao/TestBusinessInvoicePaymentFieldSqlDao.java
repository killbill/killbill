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

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoicePaymentField;

public class TestBusinessInvoicePaymentFieldSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {
    private BusinessInvoicePaymentFieldSqlDao invoicePaymentFieldSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoicePaymentFieldSqlDao = dbi.onDemand(BusinessInvoicePaymentFieldSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String paymentId = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 30);
        final String value = UUID.randomUUID().toString();

        // Verify initial state
        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId, internalCallContext).size(), 0);
        Assert.assertEquals(invoicePaymentFieldSqlDao.removeField(paymentId, name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(invoicePaymentFieldSqlDao.addField(paymentId, name, value, internalCallContext), 1);
        final List<BusinessInvoicePaymentField> fieldsForInvoicePayment = invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId, internalCallContext);
        Assert.assertEquals(fieldsForInvoicePayment.size(), 1);

        // Retrieve it
        final BusinessInvoicePaymentField invoicePaymentField = fieldsForInvoicePayment.get(0);
        Assert.assertEquals(invoicePaymentField.getPaymentId().toString(), paymentId);
        Assert.assertEquals(invoicePaymentField.getName(), name);
        Assert.assertEquals(invoicePaymentField.getValue(), value);

        // Delete it
        Assert.assertEquals(invoicePaymentFieldSqlDao.removeField(paymentId, name, internalCallContext), 1);
        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String paymentId1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 30);
        final String paymentId2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 30);

        // Add a field to both invoice payments
        Assert.assertEquals(invoicePaymentFieldSqlDao.addField(paymentId1, name1, UUID.randomUUID().toString(), internalCallContext), 1);
        Assert.assertEquals(invoicePaymentFieldSqlDao.addField(paymentId2, name2, UUID.randomUUID().toString(), internalCallContext), 1);

        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId1, internalCallContext).size(), 1);
        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId2, internalCallContext).size(), 1);

        // Remove the field for the first invoice payment
        Assert.assertEquals(invoicePaymentFieldSqlDao.removeField(paymentId1, name1, internalCallContext), 1);

        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId1, internalCallContext).size(), 0);
        Assert.assertEquals(invoicePaymentFieldSqlDao.getFieldsForInvoicePayment(paymentId2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoicePaymentFieldSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
