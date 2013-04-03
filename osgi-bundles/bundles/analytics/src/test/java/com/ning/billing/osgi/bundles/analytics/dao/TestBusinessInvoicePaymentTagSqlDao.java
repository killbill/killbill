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

package com.ning.billing.osgi.bundles.analytics.dao;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentTagModelDao;

public class TestBusinessInvoicePaymentTagSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String paymentId = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 20);

        // Verify initial state
        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId, internalCallContext).size(), 0);
        Assert.assertEquals(invoicePaymentTagSqlDao.removeTag(paymentId, name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(invoicePaymentTagSqlDao.addTag(paymentId, name, internalCallContext), 1);
        final List<BusinessInvoicePaymentTagModelDao> tagsForInvoicePayment = invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId, internalCallContext);
        Assert.assertEquals(tagsForInvoicePayment.size(), 1);

        // Retrieve it
        final BusinessInvoicePaymentTagModelDao invoicePaymentTag = tagsForInvoicePayment.get(0);
        Assert.assertEquals(invoicePaymentTag.getPaymentId().toString(), paymentId);
        Assert.assertEquals(invoicePaymentTag.getName(), name);

        // Delete it
        Assert.assertEquals(invoicePaymentTagSqlDao.removeTag(paymentId, name, internalCallContext), 1);
        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String paymentId1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 20);
        final String paymentId2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 20);

        // Add a tag to both invoice payments
        Assert.assertEquals(invoicePaymentTagSqlDao.addTag(paymentId1, name1, internalCallContext), 1);
        Assert.assertEquals(invoicePaymentTagSqlDao.addTag(paymentId2, name2, internalCallContext), 1);

        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId1, internalCallContext).size(), 1);
        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId2, internalCallContext).size(), 1);

        // Remove the tag for the first invoice payment
        Assert.assertEquals(invoicePaymentTagSqlDao.removeTag(paymentId1, name1, internalCallContext), 1);

        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId1, internalCallContext).size(), 0);
        Assert.assertEquals(invoicePaymentTagSqlDao.getTagsForInvoicePayment(paymentId2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoicePaymentTagSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
