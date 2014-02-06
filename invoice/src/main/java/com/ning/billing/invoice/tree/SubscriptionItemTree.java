package com.ning.billing.invoice.tree;

import java.util.List;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoiceItem;

public class SubscriptionItemTree {

    private final UUID subscriptionId;
    private final NodeInterval root;

    public SubscriptionItemTree(final UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
        this.root = new NodeInterval();
    }

    public void addItem(final InvoiceItem item) {
        root.addItem(item);
    }


    public void build(final List<InvoiceItem> output) {
        // compute start and end for root

        root.build(output);

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
