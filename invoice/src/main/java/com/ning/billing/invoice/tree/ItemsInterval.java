/*
 * Copyright 2010-2014 Ning, Inc.
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

package com.ning.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;

import org.joda.time.Days;
import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.generator.InvoiceDateUtils;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.invoice.model.RecurringInvoiceItem;

import com.google.common.collect.Lists;

public class ItemsInterval {

    private static final int ROUNDING_MODE = InvoicingConfiguration.getRoundingMode();
    private static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();


    private LinkedList<InvoiceItem> items;

    public ItemsInterval() {
        this(null);
    }

    public ItemsInterval(final InvoiceItem initialItem) {
        this.items = Lists.newLinkedList();
        if (initialItem != null) {
            items.add(initialItem);
        }
    }

    public InvoiceItem createRecuringItem(LocalDate startDate, LocalDate endDate) {
        Iterator<InvoiceItem> it = items.iterator();
        while (it.hasNext()) {
            final InvoiceItem cur = it.next();
            if (cur.getInvoiceItemType() == InvoiceItemType.RECURRING) {
                int nbTotalRepairedDays = Days.daysBetween(cur.getStartDate(), cur.getEndDate()).getDays();
                final BigDecimal amount = InvoiceDateUtils.calculateProrationBetweenDates(startDate, endDate, nbTotalRepairedDays).multiply(cur.getRate()).setScale(NUMBER_OF_DECIMALS, ROUNDING_MODE);
                return new RecurringInvoiceItem(cur.getInvoiceId(), cur.getAccountId(), cur.getBundleId(), cur.getSubscriptionId(),
                                                cur.getPlanName(), cur.getPhaseName(), startDate, endDate, amount, cur.getRate(), cur.getCurrency());
            }
        }
        return null;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public void build(final List<InvoiceItem> output) {


        final Set<UUID> repairedIds = new HashSet<UUID>();
        ListIterator<InvoiceItem> it = items.listIterator(items.size());
        while (it.hasPrevious()) {
            final InvoiceItem cur = it.previous();
            switch (cur.getInvoiceItemType()) {
                case FIXED:
                case RECURRING:
                    // The only time we could see that true is a case of full repair, when the repair
                    // points to an item that will end up in the same ItemsInterval
                    if (!repairedIds.contains(cur.getId())) {
                        output.add(cur);
                    }
                    break;

                case REPAIR_ADJ:
                    repairedIds.add(cur.getLinkedItemId());
                    break;

                case ITEM_ADJ:
                    // TODO Not implemented
                    break;

                // Ignored
                case EXTERNAL_CHARGE:
                case CBA_ADJ:
                case CREDIT_ADJ:
                case REFUND_ADJ:
                default:
            }
        }
    }

    public void insertSortedItem(final InvoiceItem item) {
        items.add(item);
        Collections.sort(items, new Comparator<InvoiceItem>() {
            @Override
            public int compare(final InvoiceItem o1, final InvoiceItem o2) {

                final int type1 = o1.getInvoiceItemType().ordinal();
                final int type2 = o2.getInvoiceItemType().ordinal();
                return (type1 < type2) ? -1 : ((type1 == type2) ? 0 : 1);
            }
        });
    }
}
