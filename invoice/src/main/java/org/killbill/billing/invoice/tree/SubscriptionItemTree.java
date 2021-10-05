/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2021 Equinix, Inc
 * Copyright 2014-2021 The Billing Project, LLC
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

package org.killbill.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.tree.Item.ItemAction;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

/**
 * Tree of invoice items for a given subscription
 */
public class SubscriptionItemTree {

    private final List<Item> items = new LinkedList<Item>();
    private final List<InvoiceItem> existingIgnoredItems = new LinkedList<InvoiceItem>();
    private final List<InvoiceItem> remainingIgnoredItems = new LinkedList<InvoiceItem>();
    private final List<InvoiceItem> pendingItemAdj = new LinkedList<InvoiceItem>();

    private final UUID targetInvoiceId;
    private final UUID subscriptionId;

    private ItemsNodeInterval root = new ItemsNodeInterval();
    private boolean isBuilt = false;
    private boolean isMerged = false;

    private static final Comparator<InvoiceItem> INVOICE_ITEM_COMPARATOR = new Comparator<InvoiceItem>() {
        @Override
        public int compare(final InvoiceItem o1, final InvoiceItem o2) {
            final int startDateComp = o1.getStartDate().compareTo(o2.getStartDate());
            if (startDateComp != 0) {
                return startDateComp;
            }
            final int itemTypeComp = (o1.getInvoiceItemType().ordinal() < o2.getInvoiceItemType().ordinal() ? -1 :
                                      (o1.getInvoiceItemType().ordinal() == o2.getInvoiceItemType().ordinal() ? 0 : 1));
            if (itemTypeComp != 0) {
                return itemTypeComp;
            }
            Preconditions.checkState(false, "Unexpected list of items for subscription " + o1.getSubscriptionId() +
                                            ", type(item1) = " + o1.getInvoiceItemType() + ", start(item1) = " + o1.getStartDate() +
                                            ", type(item12) = " + o2.getInvoiceItemType() + ", start(item2) = " + o2.getStartDate());
            // Never reached...
            return 0;
        }
    };

    // targetInvoiceId is the new invoice id being generated
    public SubscriptionItemTree(final UUID subscriptionId, final UUID targetInvoiceId) {
        this.subscriptionId = subscriptionId;
        this.targetInvoiceId = targetInvoiceId;
    }

    /**
     * Add an existing item in the tree. A new node is inserted or an existing one updated, if one for the same period already exists.
     *
     * @param invoiceItem new existing invoice item on disk.
     */
    public void addItem(final InvoiceItem invoiceItem) {
        Preconditions.checkState(!isBuilt, "Tree already built, unable to add new invoiceItem=%s", invoiceItem);

        switch (invoiceItem.getInvoiceItemType()) {
            case RECURRING:
                if (invoiceItem.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                    // Nothing to repair -- https://github.com/killbill/killbill/issues/783
                    existingIgnoredItems.add(invoiceItem);
                } else {
                    root.addExistingItem(new ItemsNodeInterval(root, new Item(invoiceItem, targetInvoiceId, ItemAction.ADD)));
                }
                break;

            case REPAIR_ADJ:
                root.addExistingItem(new ItemsNodeInterval(root, new Item(invoiceItem, targetInvoiceId, ItemAction.CANCEL)));
                break;

            case FIXED:
                existingIgnoredItems.add(invoiceItem);
                break;

            case ITEM_ADJ:
                pendingItemAdj.add(invoiceItem);
                break;

            default:
                break;
        }
    }

    /**
     * Build the tree and process adjustments
     */
    public void build() {
        Preconditions.checkState(!isBuilt);

        for (final InvoiceItem item : pendingItemAdj) {
            // If the linked item was ignored, ignore this adjustment too
            final InvoiceItem ignoredLinkedItem = Iterables.tryFind(existingIgnoredItems, new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem input) {
                    return input.getId().equals(item.getLinkedItemId());
                }
            }).orNull();
            if (ignoredLinkedItem == null) {
                root.addAdjustment(item);
            }
        }
        pendingItemAdj.clear();

        root.buildForExistingItems(items, targetInvoiceId);

        isBuilt = true;
    }

    /**
     * Flattens the tree so its depth only has one level below root -- becomes a list.
     * <p>
     * If the tree was not built, it is first built. The list of items is cleared and the state is now reset to unbuilt.
     *
     * @param reverse whether to reverse the existing items (recurring items now show up as CANCEL instead of ADD)
     */
    public void flatten(final boolean reverse) {
        if (!isBuilt) {
            build();
        }

        root = new ItemsNodeInterval();
        for (final Item item : items) {
            Preconditions.checkState(item.getAction() == ItemAction.ADD);
            root.addExistingItem(new ItemsNodeInterval(root, new Item(item, reverse ? ItemAction.CANCEL : ItemAction.ADD)));
        }
        items.clear();
        isBuilt = false;
    }

    /**
     * Merge a new proposed item in the tree.
     *
     * @param invoiceItem new proposed item that should be merged in the existing tree
     */
    public void mergeProposedItem(final InvoiceItem invoiceItem) {
        Preconditions.checkState(!isBuilt, "Tree already built, unable to add new invoiceItem=%s", invoiceItem);

        // Check if it was an existing item ignored for tree purposes (e.g. FIXED or $0 RECURRING, both of which aren't repaired)
        final InvoiceItem existingItem = Iterables.tryFind(existingIgnoredItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.matches(invoiceItem);
            }
        }).orNull();
        if (existingItem != null) {
            return;
        }

        switch (invoiceItem.getInvoiceItemType()) {
            case RECURRING:
                // merged means we've either matched the proposed to an existing, or triggered a repair
                final List<ItemsNodeInterval> newNodes = root.addProposedItem(new ItemsNodeInterval(root, new Item(invoiceItem, targetInvoiceId, ItemAction.ADD)));
                for (final ItemsNodeInterval cur : newNodes) {
                    items.addAll(cur.getItems());
                }
                break;

            case FIXED:
                remainingIgnoredItems.add(invoiceItem);
                break;

            default:
                Preconditions.checkState(false, "Unexpected proposed item " + invoiceItem);
        }
    }

    // Build tree post merge
    public void buildForMerge() {
        Preconditions.checkState(!isBuilt, "Tree already built");
        root.mergeExistingAndProposed(items, targetInvoiceId);
        isBuilt = true;
        isMerged = true;
    }

    /**
     * Can be called prior or after merge with proposed items.
     * <ul>
     * <li>When called prior, the merge this gives a flat view of the existing items on disk
     * <li>When called after the merge with proposed items, this gives the list of items that should now be written to disk -- new fixed, recurring and repair.
     * </ul>
     *
     * @return a flat view of the items in the tree.
     */
    public List<InvoiceItem> getView() {

        final List<InvoiceItem> tmp = new LinkedList<InvoiceItem>();
        tmp.addAll(remainingIgnoredItems);
        for (final Item item : items) {
            if (item != null) {
                tmp.add(item.toInvoiceItem());
            }
        }

        final List<InvoiceItem> result = Ordering.<InvoiceItem>from(INVOICE_ITEM_COMPARATOR).sortedCopy(tmp);
        checkItemsListState(result);
        return result;
    }

    // Verify there is no double billing, and no double repair (credits)
    private void checkItemsListState(final List<InvoiceItem> orderedList) {

        LocalDate prevRecurringEndDate = null;
        LocalDate prevRepairEndDate = null;
        for (final InvoiceItem cur : orderedList) {
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubscriptionItemTree{");
        sb.append("targetInvoiceId=").append(targetInvoiceId);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", root=").append(root);
        sb.append(", isBuilt=").append(isBuilt);
        sb.append(", isMerged=").append(isMerged);
        sb.append(", items=").append(items);
        sb.append(", existingIgnoredItems=").append(existingIgnoredItems);
        sb.append(", remainingIgnoredItems=").append(remainingIgnoredItems);
        sb.append(", pendingItemAdj=").append(pendingItemAdj);
        sb.append('}');
        return sb.toString();
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

    @VisibleForTesting
    ItemsNodeInterval getRoot() {
        return root;
    }
}
