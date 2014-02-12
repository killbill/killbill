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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoiceItem;

public class AccountItemTree {

    private final UUID accountId;
    private final Map<UUID, SubscriptionItemTree> subscriptionItemTree;

    public AccountItemTree(final UUID accountId) {
        this.accountId = accountId;
        this.subscriptionItemTree = new HashMap<UUID, SubscriptionItemTree>();
    }

    public void addItem(final InvoiceItem item) {
        if (!subscriptionItemTree.containsKey(item.getSubscriptionId())) {
            subscriptionItemTree.put(item.getSubscriptionId(), new SubscriptionItemTree(item.getSubscriptionId()));
        }
        final SubscriptionItemTree tree = subscriptionItemTree.get(item.getSubscriptionId());
        tree.addItem(item);
    }

    public List<InvoiceItem> computeNewItems(final List<InvoiceItem> proposedItems) {

        final SubscriptionItemTree curTree = null;
        for (InvoiceItem cur : proposedItems) {
            // STEPH
            if (curTree == null || curTree.getSubscriptionId().equals("foo")) {

            }
        }
        return null;
    }
}
