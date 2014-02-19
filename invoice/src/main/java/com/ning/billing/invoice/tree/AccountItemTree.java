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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceItemType;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class AccountItemTree {

    private final UUID accountId;
    private final Map<UUID, SubscriptionItemTree> subscriptionItemTree;

    public AccountItemTree(final UUID accountId) {
        this.accountId = accountId;
        this.subscriptionItemTree = new HashMap<UUID, SubscriptionItemTree>();
    }

    public void addItem(final InvoiceItem item, final List<InvoiceItem> allItems) {
        if (item.getInvoiceItemType() != InvoiceItemType.RECURRING && item.getInvoiceItemType() != InvoiceItemType.REPAIR_ADJ) {
            return;
        }
        final UUID subscriptionId  = getSubscriptionId(item, allItems);

        if (!subscriptionItemTree.containsKey(subscriptionId)) {
            subscriptionItemTree.put(subscriptionId, new SubscriptionItemTree(subscriptionId));
        }
        final SubscriptionItemTree tree = subscriptionItemTree.get(subscriptionId);
        tree.addItem(item);
    }

    public void build() {
        for (SubscriptionItemTree tree : subscriptionItemTree.values()) {
            tree.build();
        }
    }

    public List<InvoiceItem> getCurrentExistingItemsView() {

        final List<InvoiceItem> result = new ArrayList<InvoiceItem>();
        for (SubscriptionItemTree tree : subscriptionItemTree.values()) {
            final List<InvoiceItem> simplifiedView = tree.getSimplifiedView();
            if (simplifiedView.size() > 0) {
                result.addAll(simplifiedView);
            }
        }
        return result;
    }

    private UUID getSubscriptionId(final InvoiceItem item, final List<InvoiceItem> allItems) {
        if (item.getInvoiceItemType() == InvoiceItemType.RECURRING) {
            return item.getSubscriptionId();
        } else {
            final InvoiceItem linkedItem  = Iterables.tryFind(allItems, new Predicate<InvoiceItem>() {
                @Override
                public boolean apply(final InvoiceItem input) {
                    return item.getLinkedItemId().equals(input.getId());
                }
            }).get();
            return linkedItem.getSubscriptionId();
        }

    }
}
