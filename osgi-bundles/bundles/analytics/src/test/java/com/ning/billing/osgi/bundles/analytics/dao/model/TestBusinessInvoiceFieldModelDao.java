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

public class TestBusinessInvoiceFieldModelDao extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final BusinessInvoiceFieldModelDao invoiceFieldModelDao = new BusinessInvoiceFieldModelDao(account,
                                                                                                   accountRecordId,
                                                                                                   customField,
                                                                                                   fieldRecordId,
                                                                                                   auditLog,
                                                                                                   tenantRecordId);
        verifyBusinessModelDaoBase(invoiceFieldModelDao, accountRecordId, tenantRecordId);
        Assert.assertEquals(invoiceFieldModelDao.getCreatedDate(), customField.getCreatedDate());
        Assert.assertEquals(invoiceFieldModelDao.getCustomFieldRecordId(), fieldRecordId);
        Assert.assertEquals(invoiceFieldModelDao.getInvoiceId(), customField.getObjectId());
        Assert.assertEquals(invoiceFieldModelDao.getName(), customField.getFieldName());
        Assert.assertEquals(invoiceFieldModelDao.getValue(), customField.getFieldValue());
    }
}
