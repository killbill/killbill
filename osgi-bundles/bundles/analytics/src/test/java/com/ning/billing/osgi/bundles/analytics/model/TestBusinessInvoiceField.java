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

package com.ning.billing.osgi.bundles.analytics.model;

import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteNoDB;
import com.ning.billing.osgi.bundles.analytics.dao.model.BusinessInvoiceFieldModelDao;

public class TestBusinessInvoiceField extends AnalyticsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final UUID invoiceId = UUID.randomUUID();
        final String name = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final BusinessInvoiceFieldModelDao invoiceField = new BusinessInvoiceFieldModelDao(invoiceId,
                                                                                           name,
                                                                                           value);
        Assert.assertSame(invoiceField, invoiceField);
        Assert.assertEquals(invoiceField, invoiceField);
        Assert.assertTrue(invoiceField.equals(invoiceField));
        Assert.assertEquals(invoiceField.getInvoiceId(), invoiceId);
        Assert.assertEquals(invoiceField.getName(), name);
        Assert.assertEquals(invoiceField.getValue(), value);

        final BusinessInvoiceFieldModelDao otherInvoiceField = new BusinessInvoiceFieldModelDao(UUID.randomUUID(),
                                                                                                UUID.randomUUID().toString(),
                                                                                                UUID.randomUUID().toString());
        Assert.assertFalse(invoiceField.equals(otherInvoiceField));
    }
}
