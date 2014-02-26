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

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.tree.Item.ItemAction;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

/**
 * Tree of invoice items for a given subscription.
 */
public class SubscriptionItemTree {

    private boolean isBuilt;

    private final UUID subscriptionId;
    private NodeInterval root;

    private List<Item> items;

    private List<InvoiceItem> existingFixedItems;
    private List<InvoiceItem> remainingFixedItems;
    private List<InvoiceItem> pendingItemAdj;

    private static final Comparator<InvoiceItem> INVOICE_ITEM_COMPARATOR = new Comparator<InvoiceItem>() {
        @Override
        public int compare(final InvoiceItem o1, final InvoiceItem o2) {
            int startDateComp = o1.getStartDate().compareTo(o2.getStartDate());
            if (startDateComp != 0) {
                return startDateComp;
            }
            int itemTypeComp = Integer.compare(o1.getInvoiceItemType().ordinal(), o2.getInvoiceItemType().ordinal());
            if (itemTypeComp != 0) {
                return itemTypeComp;
            }
            Preconditions.checkState(false, "Unexpected list of items for subscription " + o1.getSubscriptionId());
            // Never reached...
            return 0;
        }
    };

    public SubscriptionItemTree(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.root = new NodeInterval();
        this.items = new LinkedList<Item>();
        this.existingFixedItems = new LinkedList<InvoiceItem>();
        this.remainingFixedItems = new LinkedList<InvoiceItem>();
        this.pendingItemAdj = new LinkedList<InvoiceItem>();
        this.isBuilt = false;
    }

    /**
     * Build the tree to return the list of existing items.
     */
    public void build() {
        Preconditions.checkState(!isBuilt);
        for (InvoiceItem item : pendingItemAdj) {
            root.addAdjustment(item.getStartDate(), item.getAmount(), item.getLinkedItemId());
        }
        pendingItemAdj.clear();
        root.build(items, false);
        isBuilt = true;
    }

    /**
     * Flattens the tree so its depth only has one levl below root -- becomes a list.
     * <p>
     * If the tree was not built, it is first built. The list of items is cleared and the state is now reset to unbuilt.
     *
     * @param reverse whether to reverse the existing items (recurring items now show up as CANCEL instead of ADD)
     */
    public void flatten(boolean reverse) {
        if (!isBuilt) {
            build();
        }
        root = new NodeInterval();
        for (Item item : items) {
            Preconditions.checkState(item.getAction() == ItemAction.ADD);
            root.addExistingItem(new NodeInterval(root, new Item(item, reverse ? ItemAction.CANCEL : ItemAction.ADD)));
        }
        items.clear();
        isBuilt = false;
    }

    public void buildForMerge() {
        Preconditions.checkState(!isBuilt);
        root.build(items, true);
        isBuilt = true;
    }

    /**
     * Add an existing item in the tree.
     *
     * @param invoiceItem new existing invoice item on disk.
     */
    public void addItem(final InvoiceItem invoiceItem) {

        Preconditions.checkState(!isBuilt);
        switch (invoiceItem.getInvoiceItemType()) {
            case RECURRING:
                root.addExistingItem(new NodeInterval(root, new Item(invoiceItem, ItemAction.ADD)));
                break;

            case REPAIR_ADJ:
                root.addExistingItem(new NodeInterval(root, new Item(invoiceItem, ItemAction.CANCEL)));
                break;

            case FIXED:
                existingFixedItems.add(invoiceItem);
                break;

            case ITEM_ADJ:
                pendingItemAdj.add(invoiceItem);
                break;

            default:
                break;
        }
    }

    /**
     * Merge a new proposed ietm in the tree.
     *
     * @param invoiceItem new proposed item that should be merged in the existing tree
     */
    public void mergeProposedItem(final InvoiceItem invoiceItem) {

        Preconditions.checkState(!isBuilt);
        switch (invoiceItem.getInvoiceItemType()) {
            case RECURRING:
                final boolean result = root.mergeProposedItem(new NodeInterval(root, new Item(invoiceItem, ItemAction.ADD)));
                if (!result) {
                    items.add(new Item(invoiceItem, ItemAction.ADD));
                }
                break;

            case FIXED:
                final InvoiceItem existingItem = Iterables.tryFind(existingFixedItems, new Predicate<InvoiceItem>() {
                    @Override
                    public boolean apply(final InvoiceItem input) {
                        return input.matches(invoiceItem);
                    }
                }).orNull();
                if (existingItem == null) {
                    remainingFixedItems.add(invoiceItem);
                }
                break;

            default:
                Preconditions.checkState(false, "Unexpected proposed item " + invoiceItem);
        }

    }

    /**
     * Can be called prior or after merge with proposed items.
     * <ul>
     * <li>When called prior, the merge this gives a flat view of the existing items on disk
     * <li>When called after the merge with proposed items, this gives the list of items that should now be written to disk -- new fixed, recurring and repair.
     * </ul>
     * @return a flat view of the items in the tree.
     */
    public List<InvoiceItem> getView() {

        final List<InvoiceItem> tmp = new LinkedList<InvoiceItem>();
        tmp.addAll(remainingFixedItems);
        tmp.addAll(Collections2.filter(Collections2.transform(items, new Function<Item, InvoiceItem>() {
            @Override
            public InvoiceItem apply(final Item input) {
                return input.toInvoiceItem();
            }
        }), new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(@Nullable final InvoiceItem input) {
                return input != null;
            }
        }));

        final List<InvoiceItem> result = Ordering.<InvoiceItem>from(INVOICE_ITEM_COMPARATOR).sortedCopy(tmp);
        checkItemsListState(result);
        return result;
    }

    // Verify there is no double billing, and no double repair (credits)
    private void checkItemsListState(final List<InvoiceItem> orderedList) {

        LocalDate prevRecurringEndDate = null;
        LocalDate prevRepairEndDate = null;
        for (InvoiceItem cur : orderedList) {
            switch (cur.getInvoiceItemType()) {
                case FIXED:
                    break;

                case RECURRING:
                    if (prevRecurringEndDate != null) {
                        Preconditions.checkState(prevRecurringEndDate.compareTo(cur.getStartDate()) <= 0);
                    }
                    prevRecurringEndDate = cur.getEndDate();
                    break;

                case REPAIR_ADJ:
                    if (prevRepairEndDate != null) {
                        Preconditions.checkState(prevRepairEndDate.compareTo(cur.getStartDate()) <= 0);
                    }
                    prevRepairEndDate = cur.getEndDate();
                    break;

                default:
                    Preconditions.checkState(false, "Unexpected item type " + cur.getInvoiceItemType());
            }
        }
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionItemTree)) {
            return false;
        }

        final SubscriptionItemTree that = (SubscriptionItemTree) o;

        if (root != null ? !root.equals(that.root) : that.root != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = subscriptionId != null ? subscriptionId.hashCode() : 0;
        result = 31 * result + (root != null ? root.hashCode() : 0);
        return result;
    }

}
