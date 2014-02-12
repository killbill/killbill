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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoiceItem;

public class SubscriptionItemTree {

    private final UUID subscriptionId;
    private final NodeInterval root;

    private List<InvoiceItem> fixedOrRecuringItems;

    public SubscriptionItemTree(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.root = new NodeInterval();
    }

    public void addItem(final InvoiceItem item) {
        root.addItem(item);
    }


    public void build() {
        if (fixedOrRecuringItems == null) {
            fixedOrRecuringItems = new LinkedList<InvoiceItem>();
            root.build(fixedOrRecuringItems);
        }
    }

    public List<InvoiceItem> getSimplifiedView() {
        return fixedOrRecuringItems;
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
