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

import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;

public class TestBusinessAccountModelDao extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructorWithNulls() throws Exception {
        final BusinessAccountModelDao businessAccount = new BusinessAccountModelDao(account, BigDecimal.ONE, null, null, auditLog);
        verifyAccountFields(businessAccount);
        Assert.assertEquals(businessAccount.getBalance(), BigDecimal.ONE);
        Assert.assertNull(businessAccount.getLastInvoiceDate());
        Assert.assertNull(businessAccount.getLastPaymentDate());
        Assert.assertNull(businessAccount.getLastPaymentStatus());
    }

    @Test(groups = "fast")
    public void testConstructorWithoutNulls() throws Exception {
        final BusinessAccountModelDao businessAccount = new BusinessAccountModelDao(account, BigDecimal.ONE, invoice, payment, auditLog);
        verifyAccountFields(businessAccount);
        Assert.assertEquals(businessAccount.getBalance(), BigDecimal.ONE);
        Assert.assertEquals(businessAccount.getLastInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(businessAccount.getLastPaymentDate(), payment.getEffectiveDate());
        Assert.assertEquals(businessAccount.getLastPaymentStatus(), payment.getPaymentStatus().toString());
    }

    private void verifyAccountFields(final BusinessAccountModelDao businessAccount) {
        verifyBusinessModelDaoBase(businessAccount);
        Assert.assertEquals(businessAccount.getEmail(), account.getEmail());
        Assert.assertEquals(businessAccount.getFirstNameLength(), account.getFirstNameLength());
        Assert.assertEquals(businessAccount.getCurrency(), account.getCurrency().toString());
        Assert.assertEquals(businessAccount.getBillingCycleDayLocal(), account.getBillCycleDayLocal());
        Assert.assertEquals(businessAccount.getAddress1(), account.getAddress1());
        Assert.assertEquals(businessAccount.getAddress2(), account.getAddress2());
        Assert.assertEquals(businessAccount.getCompanyName(), account.getCompanyName());
        Assert.assertEquals(businessAccount.getCity(), account.getCity());
        Assert.assertEquals(businessAccount.getStateOrProvince(), account.getStateOrProvince());
        Assert.assertEquals(businessAccount.getCountry(), account.getCountry());
        Assert.assertEquals(businessAccount.getPostalCode(), account.getPostalCode());
        Assert.assertEquals(businessAccount.getPhone(), account.getPhone());
        Assert.assertEquals(businessAccount.getMigrated(), account.isMigrated());
        Assert.assertEquals(businessAccount.getNotifiedForInvoices(), account.isNotifiedForInvoices());
    }
}
