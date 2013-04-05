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

public class TestBusinessInvoicePaymentModelDao extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final BusinessInvoicePaymentModelDao invoicePaymentModelDao = new BusinessInvoicePaymentModelDao(account,
                                                                                                         accountRecordId,
                                                                                                         invoice,
                                                                                                         invoicePayment,
                                                                                                         invoicePaymentRecordId,
                                                                                                         payment,
                                                                                                         paymentMethod,
                                                                                                         auditLog,
                                                                                                         tenantRecordId);
        verifyBusinessModelDaoBase(invoicePaymentModelDao, accountRecordId, tenantRecordId);
        Assert.assertEquals(invoicePaymentModelDao.getInvoicePaymentRecordId(), invoicePaymentRecordId);
        Assert.assertEquals(invoicePaymentModelDao.getInvoicePaymentId(), invoicePayment.getId());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceId(), invoice.getId());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceNumber(), invoice.getInvoiceNumber());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceCreatedDate(), invoice.getCreatedDate());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceTargetDate(), invoice.getInvoiceDate());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceCurrency(), invoice.getCurrency().toString());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceBalance(), invoice.getBalance());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceAmountPaid(), invoice.getPaidAmount());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceAmountCharged(), invoice.getChargedAmount());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceOriginalAmountCharged(), invoice.getOriginalChargedAmount());
        Assert.assertEquals(invoicePaymentModelDao.getInvoiceAmountCredited(), invoice.getCreditAdjAmount());
        Assert.assertEquals(invoicePaymentModelDao.getInvoicePaymentType(), invoicePayment.getType().toString());
        //Assert.assertEquals(invoicePaymentModelDao.getPaymentNumber(), /* TODO */);
        Assert.assertEquals(invoicePaymentModelDao.getLinkedInvoicePaymentId(), invoicePayment.getLinkedInvoicePaymentId());
        Assert.assertEquals(invoicePaymentModelDao.getAmount(), payment.getAmount());
        Assert.assertEquals(invoicePaymentModelDao.getCurrency(), payment.getCurrency().toString());
    }
}
