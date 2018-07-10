/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestExternalChargeInvoiceItem extends InvoiceTestSuiteNoDB {

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID invoiceId = UUID.randomUUID();
        final UUID accountId = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final String description = UUID.randomUUID().toString();
        final LocalDate effectiveDate = clock.getUTCToday();
        final BigDecimal amount = BigDecimal.TEN;
        final Currency currency = Currency.GBP;
        final ExternalChargeInvoiceItem item = new ExternalChargeInvoiceItem(id, invoiceId, accountId, bundleId, description,
                                                                             effectiveDate, effectiveDate, amount, currency, null);
        Assert.assertEquals(item.getAccountId(), accountId);
        Assert.assertEquals(item.getAmount().compareTo(amount), 0);
        Assert.assertEquals(item.getBundleId(), bundleId);
        Assert.assertEquals(item.getCurrency(), currency);
        Assert.assertEquals(item.getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        Assert.assertEquals(item.getDescription(), description);
        Assert.assertEquals(item.getStartDate().compareTo(effectiveDate), 0);
        Assert.assertEquals(item.getEndDate().compareTo(effectiveDate), 0);
        Assert.assertNull(item.getPlanName());
        Assert.assertNull(item.getLinkedItemId());
        Assert.assertNull(item.getPhaseName());
        Assert.assertNull(item.getRate());
        Assert.assertNull(item.getSubscriptionId());
        Assert.assertNull(item.getItemDetails());

        Assert.assertEquals(item, item);

        final ExternalChargeInvoiceItem otherItem = new ExternalChargeInvoiceItem(id, invoiceId, UUID.randomUUID(), bundleId,
                                                                                  description, effectiveDate, effectiveDate, amount, currency, null);
        Assert.assertNotEquals(otherItem, item);

        // Check comparison (done by start date)
        final ExternalChargeInvoiceItem itemBefore = new ExternalChargeInvoiceItem(id, invoiceId, accountId, bundleId, description,
                                                                                   effectiveDate.minusDays(1), effectiveDate.minusDays(1), amount, currency, null);
        Assert.assertFalse(itemBefore.matches(item));
        final ExternalChargeInvoiceItem itemAfter = new ExternalChargeInvoiceItem(id, invoiceId, accountId, bundleId, description,
                                                                                  effectiveDate.plusDays(1), effectiveDate.minusDays(1), amount, currency, null);
        Assert.assertFalse(itemAfter.matches(item));
    }
}
