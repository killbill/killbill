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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.InvoiceTestSuiteNoDB;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;

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
                                                                             effectiveDate, amount, currency);
        Assert.assertEquals(item.getAccountId(), accountId);
        Assert.assertEquals(item.getAmount(), amount);
        Assert.assertEquals(item.getBundleId(), bundleId);
        Assert.assertEquals(item.getCurrency(), currency);
        Assert.assertEquals(item.getInvoiceItemType(), InvoiceItemType.EXTERNAL_CHARGE);
        Assert.assertEquals(item.getPlanName(), description);
        Assert.assertNull(item.getEndDate());
        Assert.assertNull(item.getLinkedItemId());
        Assert.assertNull(item.getPhaseName());
        Assert.assertNull(item.getRate());
        Assert.assertNull(item.getSubscriptionId());

        Assert.assertEquals(item, item);

        final ExternalChargeInvoiceItem otherItem = new ExternalChargeInvoiceItem(id, invoiceId, UUID.randomUUID(), bundleId,
                                                                                  description, effectiveDate, amount, currency);
        Assert.assertNotEquals(otherItem, item);

        // Check comparison (done by start date)
        final ExternalChargeInvoiceItem itemBefore = new ExternalChargeInvoiceItem(id, invoiceId, accountId, bundleId, description,
                                                                                   effectiveDate.minusDays(1), amount, currency);
        Assert.assertFalse(itemBefore.matches(item));
        final ExternalChargeInvoiceItem itemAfter = new ExternalChargeInvoiceItem(id, invoiceId, accountId, bundleId, description,
                                                                                  effectiveDate.plusDays(1), amount, currency);
        Assert.assertFalse(itemAfter.matches(item));
    }
}
