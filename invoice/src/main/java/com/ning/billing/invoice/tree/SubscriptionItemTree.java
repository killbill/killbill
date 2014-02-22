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

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.tree.Item.ItemAction;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class SubscriptionItemTree {

    private boolean isBuilt;

    private final UUID subscriptionId;
    private NodeInterval root;

    private List<Item> items;

    private List<InvoiceItem> existingFixedItems;
    private List<InvoiceItem> remainingFixedItems;
    private List<InvoiceItem> pendingItemAdj;

    public SubscriptionItemTree(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.root = new NodeInterval();
        this.items = new LinkedList<Item>();
        this.existingFixedItems = new LinkedList<InvoiceItem>();
        this.remainingFixedItems = new LinkedList<InvoiceItem>();
        this.pendingItemAdj = new LinkedList<InvoiceItem>();
        this.isBuilt = false;
    }

    public void build() {
        Preconditions.checkState(!isBuilt);
        for (InvoiceItem item : pendingItemAdj) {
            root.addAdjustment(item.getStartDate(), item.getAmount(), item.getLinkedItemId());
        }
        pendingItemAdj.clear();
        root.build(items, false, false);
        isBuilt = true;
    }

    public void flatten(boolean reverse) {
        if (!isBuilt) {
            build();
        }
        root = new NodeInterval();
        for (Item item : items) {
            Preconditions.checkState(item.getAction() == ItemAction.ADD);
            root.addNodeInterval(new NodeInterval(root, new Item(item, reverse ? ItemAction.CANCEL : ItemAction.ADD)));
        }
        items.clear();
        isBuilt = false;
    }

    public void buildForMerge() {
        Preconditions.checkState(!isBuilt);
        root.build(items, false, true);
        isBuilt = true;
    }

    public void addItem(final InvoiceItem invoiceItem) {

        Preconditions.checkState(!isBuilt);
        switch (invoiceItem.getInvoiceItemType()) {
            case RECURRING:
                root.addNodeInterval(new NodeInterval(root, new Item(invoiceItem, ItemAction.ADD)));
                break;

            case REPAIR_ADJ:
                root.addNodeInterval(new NodeInterval(root, new Item(invoiceItem, ItemAction.CANCEL)));
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

    public List<InvoiceItem> getView() {

        // STEPH TODO check that nodeInterval don't overlap or throw. => double billing...
        final List<InvoiceItem> result = new LinkedList<InvoiceItem>();
        result.addAll(remainingFixedItems);
        result.addAll(Collections2.filter(Collections2.transform(items, new Function<Item, InvoiceItem>() {
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
        return result;
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
