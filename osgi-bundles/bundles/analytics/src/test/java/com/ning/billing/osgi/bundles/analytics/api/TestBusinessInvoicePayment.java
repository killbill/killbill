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
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentBaseModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentModelDao;

public class TestBusinessInvoicePayment extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final BusinessInvoicePaymentBaseModelDao invoicePaymentBaseModelDao = BusinessInvoicePaymentModelDao.create(account,
                                                                                                                    accountRecordId,
                                                                                                                    invoice,
                                                                                                                    invoicePayment,
                                                                                                                    invoicePaymentRecordId,
                                                                                                                    payment,
                                                                                                                    paymentMethod,
                                                                                                                    auditLog,
                                                                                                                    tenantRecordId);
        final BusinessInvoicePayment businessInvoicePayment = new BusinessInvoicePayment(invoicePaymentBaseModelDao);
        verifyBusinessEntityBase(businessInvoicePayment);
        Assert.assertEquals(businessInvoicePayment.getCreatedDate(), invoicePaymentBaseModelDao.getCreatedDate());
        Assert.assertEquals(businessInvoicePayment.getInvoicePaymentId(), invoicePaymentBaseModelDao.getInvoicePaymentId());
        Assert.assertEquals(businessInvoicePayment.getInvoiceId(), invoicePaymentBaseModelDao.getInvoiceId());
        Assert.assertEquals(businessInvoicePayment.getInvoiceNumber(), invoicePaymentBaseModelDao.getInvoiceNumber());
        Assert.assertEquals(businessInvoicePayment.getInvoiceCreatedDate(), invoicePaymentBaseModelDao.getInvoiceCreatedDate());
        Assert.assertEquals(businessInvoicePayment.getInvoiceDate(), invoicePaymentBaseModelDao.getInvoiceDate());
        Assert.assertEquals(businessInvoicePayment.getInvoiceTargetDate(), invoicePaymentBaseModelDao.getInvoiceTargetDate());
        Assert.assertEquals(businessInvoicePayment.getInvoiceCurrency(), invoicePaymentBaseModelDao.getInvoiceCurrency());
        Assert.assertEquals(businessInvoicePayment.getInvoiceBalance(), invoicePaymentBaseModelDao.getInvoiceBalance());
        Assert.assertEquals(businessInvoicePayment.getInvoiceAmountPaid(), invoicePaymentBaseModelDao.getInvoiceAmountPaid());
        Assert.assertEquals(businessInvoicePayment.getInvoiceAmountCharged(), invoicePaymentBaseModelDao.getInvoiceAmountCharged());
        Assert.assertEquals(businessInvoicePayment.getInvoiceOriginalAmountCharged(), invoicePaymentBaseModelDao.getInvoiceOriginalAmountCharged());
        Assert.assertEquals(businessInvoicePayment.getInvoiceAmountCredited(), invoicePaymentBaseModelDao.getInvoiceAmountCredited());
        Assert.assertEquals(businessInvoicePayment.getInvoicePaymentType(), invoicePaymentBaseModelDao.getInvoicePaymentType());
        Assert.assertEquals(businessInvoicePayment.getPaymentNumber(), invoicePaymentBaseModelDao.getPaymentNumber());
        Assert.assertEquals(businessInvoicePayment.getLinkedInvoicePaymentId(), invoicePaymentBaseModelDao.getLinkedInvoicePaymentId());
        Assert.assertEquals(businessInvoicePayment.getAmount(), invoicePaymentBaseModelDao.getAmount());
        Assert.assertEquals(businessInvoicePayment.getCurrency(), invoicePaymentBaseModelDao.getCurrency());
    }
}
