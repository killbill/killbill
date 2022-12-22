/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.invoice.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.utils.collect.Iterables;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUsageInvoiceItemGeneratorUnit extends InvoiceTestSuiteNoDB {

    final Map<String, Usage> DEFAULT_KNOWN_USAGE = Map.of("usage1", createUsage(BillingMode.IN_ARREAR),
                                                          "usage2", createUsage(BillingMode.IN_ADVANCE), // IN_ADVANCE will get excluded
                                                          "usage3", createUsage(BillingMode.IN_ARREAR));

    // We have total 4 valid invoice items that match knownUsages
    final Iterable<Invoice> DEFAULT_EXISTING_INVOICES = List.of(createInvoice(Map.of("usage0", InvoiceItemType.USAGE,      // invalid
                                                                                     "usage1", InvoiceItemType.ITEM_ADJ)), // invalid

                                                                createInvoice(Map.of("usage1", InvoiceItemType.USAGE)),    // valid

                                                                createInvoice(Map.of("usage1", InvoiceItemType.USAGE,      // valid
                                                                                     "usage2", InvoiceItemType.FIXED)),    // invalid

                                                                createInvoice(Map.of("usage2", InvoiceItemType.USAGE,      // invalid. "usage2" is IN_ADVANCE
                                                                                     "usage3", InvoiceItemType.RECURRING)),// invalid

                                                                createInvoice(Map.of("usage1", InvoiceItemType.USAGE,      // valid
                                                                                     "usage2", InvoiceItemType.TAX,        // invalid
                                                                                     "usage3", InvoiceItemType.USAGE))     // valid
                                                      );

    private static UsageInvoiceItemGenerator createGenerator() {
        final RawUsageOptimizer optimizer = mock(RawUsageOptimizer.class);
        final InvoiceConfig invoiceConfig = mock(InvoiceConfig.class);
        final UsageInvoiceItemGenerator generator = new UsageInvoiceItemGenerator(optimizer, invoiceConfig);

        return Mockito.spy(generator);
    }

    private static Usage createUsage(final BillingMode billingMode) {
        final Usage usage = mock(Usage.class);
        when(usage.getBillingMode()).thenReturn(billingMode);
        return usage;
    }

    // In this scenario, subscriptionId of invoice.invoiceItems will remain the same.
    private static Invoice createInvoice(final Map<String, InvoiceItemType> invoiceItemsUsageNameAndType) {
        final UUID subscriptionId = UUIDs.randomUUID();
        final List<InvoiceItem> invoiceItems = new ArrayList<>();
        for (final Entry<String, InvoiceItemType> entry : invoiceItemsUsageNameAndType.entrySet()) {
            final InvoiceItem item = mock(InvoiceItem.class);
            when(item.getUsageName()).thenReturn(entry.getKey());
            when(item.getInvoiceItemType()).thenReturn(entry.getValue());
            when(item.getSubscriptionId()).thenReturn(subscriptionId);
            invoiceItems.add(item);
        }
        final Invoice invoice = mock(Invoice.class);
        when(invoice.getId()).thenReturn(UUIDs.randomUUID());
        when(invoice.getInvoiceItems()).thenReturn(invoiceItems);
        return invoice;
    }

    @Test(groups = "fast")
    public void testGetUsageInArrearItems() {
        final UsageInvoiceItemGenerator generator = createGenerator();
        final Iterable<InvoiceItem> invoiceItems = generator.getUsageInArrearItems(DEFAULT_KNOWN_USAGE, DEFAULT_EXISTING_INVOICES);
        final List<InvoiceItem> list = Iterables.toUnmodifiableList(invoiceItems);

        Assert.assertEquals(list.size(), 4);
    }

    @Test(groups = "fast")
    public void testExtractPerSubscriptionExistingInArrearUsageItems() {
        final UsageInvoiceItemGenerator generator = createGenerator();
        final Map<UUID, List<InvoiceItem>> items = generator.extractPerSubscriptionExistingInArrearUsageItems(
                DEFAULT_KNOWN_USAGE,
                DEFAULT_EXISTING_INVOICES);

        // size = 3. Because in #extractPerSubscriptionExistingInArrearUsageItems(), we grouped invoiceItems by subscriptionId,
        // and in our scenario (#createInvoice()) subscriptionId for each invoiceItems in an invoice remaining the same.
        Assert.assertEquals(items.size(), 3);
    }
}
