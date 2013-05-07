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

package com.ning.billing.osgi.bundles.analytics.utils;

import java.util.Collection;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

/**
 * Utilities to manipulate invoice and invoice items.
 */
public class BusinessInvoiceUtils {

    public static boolean isRevenueRecognizable(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItemsOnAllInvoices) {
        final Collection<InvoiceItem> otherInvoiceItems = Collections2.filter(otherInvoiceItemsOnAllInvoices, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceId().equals(invoiceItem.getInvoiceId());
            }
        });

        // All items are recognizable except user generated credit (CBA_ADJ and CREDIT_ADJ on their own invoice)
        return !(InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                 (otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CREDIT_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Invoice adjustments
    public static boolean isInvoiceAdjustmentItem(final InvoiceItem invoiceItem, final Collection<InvoiceItem> otherInvoiceItemsOnAllInvoices) {
        final Collection<InvoiceItem> otherInvoiceItems = Collections2.filter(otherInvoiceItemsOnAllInvoices, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getInvoiceId().equals(invoiceItem.getInvoiceId());
            }
        });

        // Either REFUND_ADJ
        return InvoiceItemType.REFUND_ADJ.equals(invoiceItem.getInvoiceItemType()) ||
               // Or invoice level credit, i.e. credit adj, but NOT on its on own invoice
               // Note: the negative credit adj items (internal generation of account level credits) doesn't figure in analytics
               (InvoiceItemType.CREDIT_ADJ.equals(invoiceItem.getInvoiceItemType()) &&
                !(otherInvoiceItems.size() == 1 &&
                  InvoiceItemType.CBA_ADJ.equals(otherInvoiceItems.iterator().next().getInvoiceItemType()) &&
                  otherInvoiceItems.iterator().next().getInvoiceId().equals(invoiceItem.getInvoiceId()) &&
                  otherInvoiceItems.iterator().next().getAmount().compareTo(invoiceItem.getAmount().negate()) == 0));
    }

    // Item adjustments
    public static boolean isInvoiceItemAdjustmentItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.ITEM_ADJ.equals(invoiceItem.getInvoiceItemType()) || InvoiceItemType.REPAIR_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Account credits, gained or consumed
    public static boolean isAccountCreditItem(final InvoiceItem invoiceItem) {
        return InvoiceItemType.CBA_ADJ.equals(invoiceItem.getInvoiceItemType());
    }

    // Regular line item (charges)
    public static boolean isCharge(final InvoiceItem invoiceItem) {
        return InvoiceItemType.EXTERNAL_CHARGE.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.FIXED.equals(invoiceItem.getInvoiceItemType()) ||
               InvoiceItemType.RECURRING.equals(invoiceItem.getInvoiceItemType());
    }
}
