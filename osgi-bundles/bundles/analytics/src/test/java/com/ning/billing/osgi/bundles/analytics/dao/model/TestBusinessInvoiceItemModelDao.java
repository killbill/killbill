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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessInvoiceItemModelDao extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructorWithNulls() throws Exception {
        final BusinessInvoiceItemModelDao invoiceItemModelDao = new BusinessInvoiceItemModelDao(account,
                                                                                                accountRecordId,
                                                                                                invoice,
                                                                                                invoiceItem,
                                                                                                invoiceItemRecordId,
                                                                                                null,
                                                                                                null,
                                                                                                null,
                                                                                                auditLog,
                                                                                                tenantRecordId);
        verifyInvoiceItemFields(invoiceItemModelDao);
        Assert.assertNull(invoiceItemModelDao.getBundleExternalKey());
        Assert.assertNull(invoiceItemModelDao.getProductName());
        Assert.assertNull(invoiceItemModelDao.getProductType());
        Assert.assertNull(invoiceItemModelDao.getProductCategory());
        Assert.assertNull(invoiceItemModelDao.getSlug());
        Assert.assertNull(invoiceItemModelDao.getPhase());
        Assert.assertNull(invoiceItemModelDao.getBillingPeriod());
    }

    @Test(groups = "fast")
    public void testConstructorWithoutNulls() throws Exception {
        final BusinessInvoiceItemModelDao invoiceItemModelDao = new BusinessInvoiceItemModelDao(account,
                                                                                                accountRecordId,
                                                                                                invoice,
                                                                                                invoiceItem,
                                                                                                invoiceItemRecordId,
                                                                                                bundle,
                                                                                                plan,
                                                                                                phase,
                                                                                                auditLog,
                                                                                                tenantRecordId);
        verifyInvoiceItemFields(invoiceItemModelDao);
        Assert.assertEquals(invoiceItemModelDao.getBundleExternalKey(), bundle.getExternalKey());
        Assert.assertEquals(invoiceItemModelDao.getProductName(), plan.getProduct().getName());
        Assert.assertEquals(invoiceItemModelDao.getProductType(), plan.getProduct().getCatalogName());
        Assert.assertEquals(invoiceItemModelDao.getProductCategory(), plan.getProduct().getCategory().toString());
        Assert.assertEquals(invoiceItemModelDao.getSlug(), phase.getName());
        Assert.assertEquals(invoiceItemModelDao.getPhase(), phase.getPhaseType().toString());
        Assert.assertEquals(invoiceItemModelDao.getBillingPeriod(), phase.getBillingPeriod().toString());
    }

    private void verifyInvoiceItemFields(final BusinessInvoiceItemModelDao invoiceItemModelDao) {
        verifyBusinessModelDaoBase(invoiceItemModelDao, accountRecordId, tenantRecordId);
        Assert.assertEquals(invoiceItemModelDao.getCreatedDate(), invoiceItem.getCreatedDate());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceItemRecordId(), invoiceItemRecordId);
        Assert.assertEquals(invoiceItemModelDao.getItemId(), invoiceItem.getId());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceId(), invoice.getId());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceNumber(), invoice.getInvoiceNumber());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceCreatedDate(), invoice.getCreatedDate());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceCurrency(), invoice.getCurrency().toString());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceBalance(), invoice.getBalance());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceAmountPaid(), invoice.getPaidAmount());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceAmountCharged(), invoice.getChargedAmount());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceOriginalAmountCharged(), invoice.getOriginalChargedAmount());
        Assert.assertEquals(invoiceItemModelDao.getInvoiceAmountCredited(), invoice.getCreditAdjAmount());
        Assert.assertEquals(invoiceItemModelDao.getItemType(), invoiceItem.getInvoiceItemType().toString());
        //Assert.assertEquals(invoiceItemModelDao.getRevenueRecognizable(), /* TODO */);
        Assert.assertEquals(invoiceItemModelDao.getStartDate(), invoiceItem.getStartDate());
        Assert.assertEquals(invoiceItemModelDao.getAmount(), invoiceItem.getAmount());
        Assert.assertEquals(invoiceItemModelDao.getCurrency(), invoiceItem.getCurrency().toString());
        Assert.assertEquals(invoiceItemModelDao.getLinkedItemId(), invoiceItem.getLinkedItemId());
        Assert.assertEquals(invoiceItemModelDao.getEndDate(), invoiceItem.getEndDate());
    }
}
