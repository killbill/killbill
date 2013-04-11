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

package com.ning.billing.osgi.bundles.analytics.api;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceItemBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceModelDao;

import com.google.common.collect.ImmutableList;

public class TestBusinessInvoice extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final BusinessInvoiceModelDao invoiceModelDao = new BusinessInvoiceModelDao(account,
                                                                                    accountRecordId,
                                                                                    invoice,
                                                                                    invoiceRecordId,
                                                                                    auditLog,
                                                                                    tenantRecordId,
                                                                                    reportGroup);
        final BusinessInvoiceItemBaseModelDao invoiceItemBaseModelDao = BusinessInvoiceItemBaseModelDao.create(account,
                                                                                                               accountRecordId,
                                                                                                               invoice,
                                                                                                               invoiceItem,
                                                                                                               invoiceItemType,
                                                                                                               invoiceItemRecordId,
                                                                                                               secondInvoiceItemRecordId,
                                                                                                               bundle,
                                                                                                               plan,
                                                                                                               phase,
                                                                                                               auditLog,
                                                                                                               tenantRecordId,
                                                                                                               reportGroup);
        final BusinessInvoice businessInvoice = new BusinessInvoice(invoiceModelDao,
                                                                    ImmutableList.<BusinessInvoiceItemBaseModelDao>of(invoiceItemBaseModelDao));
        verifyBusinessEntityBase(businessInvoice);
        Assert.assertEquals(businessInvoice.getCreatedDate(), invoiceModelDao.getCreatedDate());
        Assert.assertEquals(businessInvoice.getInvoiceId(), invoiceModelDao.getInvoiceId());
        Assert.assertEquals(businessInvoice.getInvoiceNumber(), invoiceModelDao.getInvoiceNumber());
        Assert.assertEquals(businessInvoice.getInvoiceDate(), invoiceModelDao.getInvoiceDate());
        Assert.assertEquals(businessInvoice.getTargetDate(), invoiceModelDao.getTargetDate());
        Assert.assertEquals(businessInvoice.getCurrency(), invoiceModelDao.getCurrency());
        Assert.assertEquals(businessInvoice.getBalance(), invoiceModelDao.getBalance());
        Assert.assertEquals(businessInvoice.getAmountPaid(), invoiceModelDao.getAmountPaid());
        Assert.assertEquals(businessInvoice.getAmountCharged(), invoiceModelDao.getAmountCharged());
        Assert.assertEquals(businessInvoice.getOriginalAmountCharged(), invoiceModelDao.getOriginalAmountCharged());
        Assert.assertEquals(businessInvoice.getAmountCredited(), invoiceModelDao.getAmountCredited());
        Assert.assertEquals(businessInvoice.getInvoiceItems().size(), 1);

        final BusinessInvoiceItem businessInvoiceItem = businessInvoice.getInvoiceItems().get(0);
        verifyBusinessEntityBase(businessInvoiceItem);
        Assert.assertEquals(businessInvoiceItem.getCreatedDate(), invoiceItemBaseModelDao.getCreatedDate());
        Assert.assertEquals(businessInvoiceItem.getItemId(), invoiceItemBaseModelDao.getItemId());
        Assert.assertEquals(businessInvoiceItem.getInvoiceId(), invoiceItemBaseModelDao.getInvoiceId());
        Assert.assertEquals(businessInvoiceItem.getInvoiceNumber(), invoiceItemBaseModelDao.getInvoiceNumber());
        Assert.assertEquals(businessInvoiceItem.getInvoiceCreatedDate(), invoiceItemBaseModelDao.getInvoiceCreatedDate());
        Assert.assertEquals(businessInvoiceItem.getInvoiceDate(), invoiceItemBaseModelDao.getInvoiceDate());
        Assert.assertEquals(businessInvoiceItem.getInvoiceTargetDate(), invoiceItemBaseModelDao.getInvoiceTargetDate());
        Assert.assertEquals(businessInvoiceItem.getInvoiceCurrency(), invoiceItemBaseModelDao.getInvoiceCurrency());
        Assert.assertEquals(businessInvoiceItem.getInvoiceBalance(), invoiceItemBaseModelDao.getInvoiceBalance());
        Assert.assertEquals(businessInvoiceItem.getInvoiceAmountPaid(), invoiceItemBaseModelDao.getInvoiceAmountPaid());
        Assert.assertEquals(businessInvoiceItem.getInvoiceAmountCharged(), invoiceItemBaseModelDao.getInvoiceAmountCharged());
        Assert.assertEquals(businessInvoiceItem.getInvoiceOriginalAmountCharged(), invoiceItemBaseModelDao.getInvoiceOriginalAmountCharged());
        Assert.assertEquals(businessInvoiceItem.getInvoiceAmountCredited(), invoiceItemBaseModelDao.getInvoiceAmountCredited());
        Assert.assertEquals(businessInvoiceItem.getItemType(), invoiceItemBaseModelDao.getItemType());
        Assert.assertEquals(businessInvoiceItem.getRecognizable(), invoiceItemBaseModelDao.getRevenueRecognizable());
        Assert.assertEquals(businessInvoiceItem.getBundleExternalKey(), invoiceItemBaseModelDao.getBundleExternalKey());
        Assert.assertEquals(businessInvoiceItem.getProductName(), invoiceItemBaseModelDao.getProductName());
        Assert.assertEquals(businessInvoiceItem.getProductType(), invoiceItemBaseModelDao.getProductType());
        Assert.assertEquals(businessInvoiceItem.getProductCategory(), invoiceItemBaseModelDao.getProductCategory());
        Assert.assertEquals(businessInvoiceItem.getSlug(), invoiceItemBaseModelDao.getSlug());
        Assert.assertEquals(businessInvoiceItem.getPhase(), invoiceItemBaseModelDao.getPhase());
        Assert.assertEquals(businessInvoiceItem.getBillingPeriod(), invoiceItemBaseModelDao.getBillingPeriod());
        Assert.assertEquals(businessInvoiceItem.getStartDate(), invoiceItemBaseModelDao.getStartDate());
        Assert.assertEquals(businessInvoiceItem.getEndDate(), invoiceItemBaseModelDao.getEndDate());
        Assert.assertEquals(businessInvoiceItem.getAmount(), invoiceItemBaseModelDao.getAmount());
        Assert.assertEquals(businessInvoiceItem.getCurrency(), invoiceItemBaseModelDao.getCurrency());
        Assert.assertEquals(businessInvoiceItem.getLinkedItemId(), invoiceItemBaseModelDao.getLinkedItemId());
    }
}
