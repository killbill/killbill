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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Tree of invoice items for a given account.
 * <p/>
 * <p>It contains a map of <tt>SubscriptionItemTree</tt> and the logic is executed independently for all items
 * associated to a given subscription. That also means that invoice item adjustment which cross subscriptions
 * can't be correctly handled when they compete with other forms of adjustments.
 * <p/>
 * <p>The class is not thread safe, there is no such use case today, and there is a lifecyle to respect:
 * <ul>
 * <li>Add existing invoice items
 * <li>Build the tree,
 * <li>Merge the proposed list
 * <li>Retrieves final list
 * <ul/>
 */
public class AccountItemTree {

    private final UUID accountId;
    private final UUID targetInvoiceId;
    private final Map<UUID, SubscriptionItemTree> subscriptionItemTree;
    private final List<InvoiceItem> allExistingItems;
    private final List<InvoiceItem> pendingItemAdj;

    private boolean isBuilt;

    public AccountItemTree(final UUID accountId, final UUID targetInvoiceId) {
        this.accountId = accountId;
        this.targetInvoiceId = targetInvoiceId;
        this.subscriptionItemTree = new HashMap<UUID, SubscriptionItemTree>();
        this.isBuilt = false;
        this.allExistingItems = new LinkedList<InvoiceItem>();
        this.pendingItemAdj = new LinkedList<InvoiceItem>();
    }

    /**
     * build the subscription trees after they have been populated with existing items on disk
     */
    public void build() {
        Preconditions.checkState(!isBuilt);

        if (pendingItemAdj.size() > 0) {
            for (final InvoiceItem item : pendingItemAdj) {
                addExistingItem(item, true);
            }
            pendingItemAdj.clear();
        }
        for (final SubscriptionItemTree tree : subscriptionItemTree.values()) {
            tree.build();
        }
        isBuilt = true;
    }

    /**
     * Populate tree from existing items on disk
     *
     * @param existingItem an item read on disk
     */
    public void addExistingItem(final InvoiceItem existingItem) {
        addExistingItem(existingItem, false);
    }

    private void addExistingItem(final InvoiceItem existingItem, final boolean failOnMissingSubscription) {
        Preconditions.checkState(!isBuilt);

        // Only used to retrieve the original item for linked items
        allExistingItems.add(existingItem);

        if (existingItem.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ) {
            final InvoiceItem linkedInvoiceItem = getLinkedInvoiceItem(existingItem, allExistingItems);
            if (linkedInvoiceItem != null &&
                linkedInvoiceItem.getInvoiceItemType() != InvoiceItemType.RECURRING &&
                linkedInvoiceItem.getInvoiceItemType() != InvoiceItemType.FIXED) {
                // We only care about adjustments for recurring and fixed items when building the tree
                // (we assume that REPAIR_ADJ and ITEM_ADJ items cannot be adjusted)
                return;
            }
        }

        final UUID subscriptionId = getSubscriptionId(existingItem, allExistingItems);
        Preconditions.checkState(subscriptionId != null || !failOnMissingSubscription, "Missing subscription id");

        if (subscriptionId == null && existingItem.getInvoiceItemType() == InvoiceItemType.ITEM_ADJ) {
            pendingItemAdj.add(existingItem);
            return;
        }

        if (!subscriptionItemTree.containsKey(subscriptionId)) {
            subscriptionItemTree.put(subscriptionId, new SubscriptionItemTree(subscriptionId, targetInvoiceId));
        }
        final SubscriptionItemTree tree = subscriptionItemTree.get(subscriptionId);
        tree.addItem(existingItem);
    }

    /**
     * Rebuild the new tree by merging current on-disk existing view with new proposed list.
     *
     * @param proposedItems list of proposed item that should be merged with current existing view
     */
    public void mergeWithProposedItems(final List<InvoiceItem> proposedItems) {

        build();
        for (final SubscriptionItemTree tree : subscriptionItemTree.values()) {
            tree.flatten(true);
        }

        for (final InvoiceItem item : proposedItems) {
            final UUID subscriptionId = getSubscriptionId(item, null);
            SubscriptionItemTree tree = subscriptionItemTree.get(subscriptionId);
            if (tree == null) {
                tree = new SubscriptionItemTree(subscriptionId, targetInvoiceId);
                subscriptionItemTree.put(subscriptionId, tree);
            }
            tree.mergeProposedItem(item);
        }

        for (final SubscriptionItemTree tree : subscriptionItemTree.values()) {
            tree.buildForMerge();
        }
    }

    /**
     * @return the resulting list of items that should be written to disk
     */
    public List<InvoiceItem> getResultingItemList() {
        final List<InvoiceItem> result = new ArrayList<InvoiceItem>();
        for (final SubscriptionItemTree tree : subscriptionItemTree.values()) {
            final List<InvoiceItem> simplifiedView = tree.getView();
            if (simplifiedView.size() > 0) {
                result.addAll(simplifiedView);
            }
        }
        return result;
    }

    public UUID getAccountId() {
        return accountId;
    }

    private UUID getSubscriptionId(final InvoiceItem item, final List<InvoiceItem> allItems) {
        if (item.getInvoiceItemType() == InvoiceItemType.RECURRING ||
            item.getInvoiceItemType() == InvoiceItemType.FIXED) {
            return item.getSubscriptionId();
        } else {
            final InvoiceItem linkedItem = getLinkedInvoiceItem(item, allItems);
            return linkedItem != null ? linkedItem.getSubscriptionId() : null;
        }
    }

    private InvoiceItem getLinkedInvoiceItem(final InvoiceItem item, final Iterable<InvoiceItem> allItems) {
        return Iterables.tryFind(allItems, new Predicate<InvoiceItem>() {
            @Override
            public boolean apply(final InvoiceItem input) {
                return input.getId().equals(item.getLinkedItemId());
            }
        }).orNull();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AccountItemTree{");
        sb.append("subscriptionItemTree=").append(subscriptionItemTree);
        sb.append('}');
        return sb.toString();
    }

    public String prettyPrint() {
        final StringBuilder stringBuilder = new StringBuilder("AccountItemTree (accountId=").append(accountId).append(")\n");
        for (final Entry<UUID, SubscriptionItemTree> subscriptionItemTreeEntry : subscriptionItemTree.entrySet()) {
            stringBuilder.append("Subscription: ")
                         .append(subscriptionItemTreeEntry.getKey())
                         .append("\n")
                         .append(TreePrinter.print(subscriptionItemTreeEntry.getValue().getRoot()))
                         .append("\n");
        }
        return stringBuilder.toString();
    }
}
