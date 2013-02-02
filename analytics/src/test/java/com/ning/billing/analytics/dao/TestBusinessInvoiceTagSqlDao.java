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

import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.analytics.model.BusinessInvoiceTagModelDao;

public class TestBusinessInvoiceTagSqlDao extends AnalyticsTestSuiteWithEmbeddedDB {

    private BusinessInvoiceTagSqlDao invoiceTagSqlDao;

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
        final IDBI dbi = helper.getDBI();
        invoiceTagSqlDao = dbi.onDemand(BusinessInvoiceTagSqlDao.class);
    }

    @Test(groups = "slow")
    public void testCRUD() throws Exception {
        final String invoiceId = UUID.randomUUID().toString();
        final String name = UUID.randomUUID().toString().substring(0, 20);

        // Verify initial state
        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId, internalCallContext).size(), 0);
        Assert.assertEquals(invoiceTagSqlDao.removeTag(invoiceId, name, internalCallContext), 0);

        // Add an entry
        Assert.assertEquals(invoiceTagSqlDao.addTag(invoiceId, name, internalCallContext), 1);
        final List<BusinessInvoiceTagModelDao> tagsForInvoice = invoiceTagSqlDao.getTagsForInvoice(invoiceId, internalCallContext);
        Assert.assertEquals(tagsForInvoice.size(), 1);

        // Retrieve it
        final BusinessInvoiceTagModelDao invoiceTag = tagsForInvoice.get(0);
        Assert.assertEquals(invoiceTag.getInvoiceId().toString(), invoiceId);
        Assert.assertEquals(invoiceTag.getName(), name);

        // Delete it
        Assert.assertEquals(invoiceTagSqlDao.removeTag(invoiceId, name, internalCallContext), 1);
        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId, internalCallContext).size(), 0);
    }

    @Test(groups = "slow")
    public void testSegmentation() throws Exception {
        final String invoiceId1 = UUID.randomUUID().toString();
        final String name1 = UUID.randomUUID().toString().substring(0, 20);
        final String invoiceId2 = UUID.randomUUID().toString();
        final String name2 = UUID.randomUUID().toString().substring(0, 20);

        // Add a tag to both invoices
        Assert.assertEquals(invoiceTagSqlDao.addTag(invoiceId1, name1, internalCallContext), 1);
        Assert.assertEquals(invoiceTagSqlDao.addTag(invoiceId2, name2, internalCallContext), 1);

        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId1, internalCallContext).size(), 1);
        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId2, internalCallContext).size(), 1);

        // Remove the tag for the first invoice
        Assert.assertEquals(invoiceTagSqlDao.removeTag(invoiceId1, name1, internalCallContext), 1);

        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId1, internalCallContext).size(), 0);
        Assert.assertEquals(invoiceTagSqlDao.getTagsForInvoice(invoiceId2, internalCallContext).size(), 1);
    }

    @Test(groups = "slow")
    public void testHealthCheck() throws Exception {
        // HealthCheck test to make sure MySQL is setup properly
        try {
            invoiceTagSqlDao.test(internalCallContext);
        } catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }
}
