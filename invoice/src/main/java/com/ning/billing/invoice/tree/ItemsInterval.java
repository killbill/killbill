package com.ning.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.model.RecurringInvoiceItem;

import com.google.common.collect.Lists;

public class ItemsInterval {

    private List<InvoiceItem> items;

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
                // TODO STEPH calculate amount
                final BigDecimal amount = BigDecimal.ONE;
                return new RecurringInvoiceItem(cur.getInvoiceId(), cur.getAccountId(), cur.getBundleId(), cur.getSubscriptionId(),
                                                cur.getPlanName(), cur.getPhaseName(), startDate, endDate, amount, cur.getRate(), cur.getCurrency());
            }
        }
        return null;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    // Remove cancelling items
    public void build(final List<InvoiceItem> output) {

        boolean foundRecuring = false;

        Iterator<InvoiceItem> it = items.iterator();
        while (it.hasNext()) {
            final InvoiceItem cur = it.next();
            switch (cur.getInvoiceItemType()) {
                case FIXED:
                    // TODO Not implemented
                    break;

                case RECURRING:
                    foundRecuring = true;
                    output.add(cur);
                    break;

                case REPAIR_ADJ:
                    if (!foundRecuring) {
                        output.add(cur);
                    }
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
