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
import com.ning.billing.analytics.model.BusinessInvoiceFieldModelDao;

public class TestBusinessInvoiceFieldSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessInvoiceFieldSqlDao invoiceFieldSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoiceFieldSqlDao = dbi.onDemand(BusinessInvoiceFieldSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String invoiceId = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 30);
        final String value = UUID.randomUUID().toString();

        // Verify initial state
        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId, internalCallContext).size(), 0);
        Assert.assertEquals(invoiceFieldSqlDao.removeField(invoiceId, name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(invoiceFieldSqlDao.addField(invoiceId, name, value, internalCallContext), 1);
        final List<BusinessInvoiceFieldModelDao> fieldsForInvoice = invoiceFieldSqlDao.getFieldsForInvoice(invoiceId, internalCallContext);
        Assert.assertEquals(fieldsForInvoice.size(), 1);

        // Retrieve it
        final BusinessInvoiceFieldModelDao invoiceField = fieldsForInvoice.get(0);
        Assert.assertEquals(invoiceField.getInvoiceId().toString(), invoiceId);
        Assert.assertEquals(invoiceField.getName(), name);
        Assert.assertEquals(invoiceField.getValue(), value);

        // Delete it
        Assert.assertEquals(invoiceFieldSqlDao.removeField(invoiceId, name, internalCallContext), 1);
        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String invoiceId1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 30);
        final String invoiceId2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 30);

        // Add a field to both invoices
        Assert.assertEquals(invoiceFieldSqlDao.addField(invoiceId1, name1, UUID.randomUUID().toString(), internalCallContext), 1);
        Assert.assertEquals(invoiceFieldSqlDao.addField(invoiceId2, name2, UUID.randomUUID().toString(), internalCallContext), 1);

        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId1, internalCallContext).size(), 1);
        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId2, internalCallContext).size(), 1);

        // Remove the field for the first invoice
        Assert.assertEquals(invoiceFieldSqlDao.removeField(invoiceId1, name1, internalCallContext), 1);

        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId1, internalCallContext).size(), 0);
        Assert.assertEquals(invoiceFieldSqlDao.getFieldsForInvoice(invoiceId2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoiceFieldSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
