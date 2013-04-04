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

import com.ning.billing.ObjectType;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessAccountFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceFieldModelDao;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoicePaymentFieldModelDao;

public class TestBusinessField extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructorAccount() throws Exception {
        final BusinessAccountFieldModelDao businessAccountFieldModelDao = new BusinessAccountFieldModelDao(account, customField, auditLog);
        final BusinessField businessField = BusinessField.create(businessAccountFieldModelDao);
        verifyBusinessField(businessField);
        Assert.assertEquals(businessField.getObjectType(), ObjectType.ACCOUNT);
    }

    @Test(groups = "fast")
    public void testConstructorInvoice() throws Exception {
        final BusinessInvoiceFieldModelDao businessInvoiceFieldModelDao = new BusinessInvoiceFieldModelDao(account, customField, auditLog);
        final BusinessField businessField = BusinessField.create(businessInvoiceFieldModelDao);
        verifyBusinessField(businessField);
        Assert.assertEquals(businessField.getObjectType(), ObjectType.INVOICE);
    }

    @Test(groups = "fast")
    public void testConstructorPayment() throws Exception {
        final BusinessInvoicePaymentFieldModelDao invoicePaymentFieldModelDao = new BusinessInvoicePaymentFieldModelDao(account, customField, auditLog);
        final BusinessField businessField = BusinessField.create(invoicePaymentFieldModelDao);
        verifyBusinessField(businessField);
        Assert.assertEquals(businessField.getObjectType(), ObjectType.INVOICE_PAYMENT);
    }

    private void verifyBusinessField(final BusinessField accountField) {
        verifyBusinessEntityBase(accountField);
        Assert.assertEquals(accountField.getName(), customField.getFieldName());
        Assert.assertEquals(accountField.getValue(), customField.getFieldValue());
    }
}
