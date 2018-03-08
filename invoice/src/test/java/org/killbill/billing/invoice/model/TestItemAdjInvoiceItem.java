/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestItemAdjInvoiceItem extends InvoiceTestSuiteNoDB {

    @Test(groups = "fast")
    public void testType() {
        final InvoiceItem invoiceItem = new ItemAdjInvoiceItem(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                                                               new LocalDate(2010, 1, 1), null, new BigDecimal("7.00"), Currency.USD,
                                                               UUID.randomUUID(), null);
        Assert.assertEquals(invoiceItem.getInvoiceItemType(), InvoiceItemType.ITEM_ADJ);
    }
}
