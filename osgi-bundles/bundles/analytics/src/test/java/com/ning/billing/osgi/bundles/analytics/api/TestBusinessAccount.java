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

import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountModelDao;

public class TestBusinessAccount extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final BusinessAccountModelDao accountModelDao = new BusinessAccountModelDao(account,
                                                                                    BigDecimal.ONE,
                                                                                    invoice,
                                                                                    payment,
                                                                                    auditLog);
        final BusinessAccount businessAccount = new BusinessAccount(accountModelDao);

        verifyBusinessEntityBase(businessAccount);
        Assert.assertEquals(businessAccount.getEmail(), accountModelDao.getEmail());
        Assert.assertEquals(businessAccount.getFirstNameLength(), accountModelDao.getFirstNameLength());
        Assert.assertEquals(businessAccount.getCurrency(), accountModelDao.getCurrency());
        Assert.assertEquals(businessAccount.getBillingCycleDayLocal(), accountModelDao.getBillingCycleDayLocal());
        Assert.assertEquals(businessAccount.getAddress1(), accountModelDao.getAddress1());
        Assert.assertEquals(businessAccount.getAddress2(), accountModelDao.getAddress2());
        Assert.assertEquals(businessAccount.getCompanyName(), accountModelDao.getCompanyName());
        Assert.assertEquals(businessAccount.getCity(), accountModelDao.getCity());
        Assert.assertEquals(businessAccount.getStateOrProvince(), accountModelDao.getStateOrProvince());
        Assert.assertEquals(businessAccount.getCountry(), accountModelDao.getCountry());
        Assert.assertEquals(businessAccount.getPostalCode(), accountModelDao.getPostalCode());
        Assert.assertEquals(businessAccount.getPhone(), accountModelDao.getPhone());
        Assert.assertEquals(businessAccount.getMigrated(), accountModelDao.getMigrated());
        Assert.assertEquals(businessAccount.getNotifiedForInvoices(), accountModelDao.getNotifiedForInvoices());
        Assert.assertEquals(businessAccount.getBalance(), accountModelDao.getBalance());
        Assert.assertEquals(businessAccount.getLastInvoiceDate(), accountModelDao.getLastInvoiceDate());
        Assert.assertEquals(businessAccount.getLastPaymentDate(), accountModelDao.getLastPaymentDate());
        Assert.assertEquals(businessAccount.getLastPaymentStatus(), accountModelDao.getLastPaymentStatus());
    }
}
