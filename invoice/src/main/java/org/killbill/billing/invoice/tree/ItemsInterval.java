/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.tree.Item.ItemAction;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * Keeps track of all the items existing on a specified ItemsNodeInterval
 */
public class ItemsInterval {

    // Parent (enclosing) interval
    private final ItemsNodeInterval interval;
    private final LinkedList<Item> items;

    public ItemsInterval(final ItemsNodeInterval interval) {
        this(interval, null);
    }

    public ItemsInterval(final ItemsNodeInterval interval, final Item initialItem) {
        this.interval = interval;
        this.items = Lists.newLinkedList();
        if (initialItem != null) {
            items.add(initialItem);
        }
    }

    public List<Item> getItems() {
        return items;
    }

    public Iterable<Item> get_ADD_items() {
        return findItems(ItemAction.ADD);
    }

    public Iterable<Item> get_CANCEL_items() {
        return findItems(ItemAction.CANCEL);
    }

    public Item getCancellingItemIfExists(final UUID targetId) {
        return Iterables.tryFind(items,
                                 new Predicate<Item>() {
                                     @Override
                                     public boolean apply(final Item input) {
                                         return input.getAction() == ItemAction.CANCEL && input.getLinkedId().equals(targetId);
                                     }
                                 }).orNull();
    }

    public Item getCancelledItemIfExists(final UUID linkedId) {
        return Iterables.tryFind(items,
                                 new Predicate<Item>() {
                                     @Override
                                     public boolean apply(final Item input) {
                                         return input.getAction() == ItemAction.ADD && input.getId().equals(linkedId);
                                     }
                                 }).orNull();
    }

    public NodeInterval getNodeInterval() {
        return interval;
    }

    public Item findItem(final UUID targetId) {
        final Collection<Item> matchingItems = Collections2.<Item>filter(items,
                                                                         new Predicate<Item>() {
                                                                             @Override
                                                                             public boolean apply(final Item input) {
                                                                                 return input.getId().equals(targetId);
                                                                             }
                                                                         });
        Preconditions.checkState(matchingItems.size() < 2, "Too many items matching id='%s' among items='%s'", targetId, items);
        return matchingItems.size() == 1 ? matchingItems.iterator().next() : null;
    }

    /**
     * Remove all the cancelling pairs (ADD/CANCEL) for which CANCEL linkedId points to ADD id.
     *
     * @return true if there is no more items
     */
    public boolean mergeCancellingPairs() {
        final Multimap<UUID, Item> cancellingPairPerInvoiceItemId = LinkedListMultimap.<UUID, Item>create();
        for (final Item item : items) {
            final UUID invoiceItemId = (item.getAction() == ItemAction.ADD) ? item.getId() : item.getLinkedId();
            cancellingPairPerInvoiceItemId.put(invoiceItemId, item);
        }

        for (final UUID invoiceItemId : cancellingPairPerInvoiceItemId.keySet()) {
            final Collection<Item> itemsToRemove = cancellingPairPerInvoiceItemId.get(invoiceItemId);
            Preconditions.checkState(itemsToRemove.size() <= 2, "Too many repairs for invoiceItemId='%s': %s", invoiceItemId, itemsToRemove);
            if (itemsToRemove.size() == 2) {
                for (final Item itemToRemove : itemsToRemove) {
                    items.remove(itemToRemove);
                }
            }
        }

        return items.isEmpty();
    }

    public void add(final Item item) {
        items.add(item);
    }

    public void cancelItems(final Item item) {
        Preconditions.checkState(item.getAction() == ItemAction.ADD);
        Preconditions.checkState(items.size() == 1);
        Preconditions.checkState(items.get(0).getAction() == ItemAction.CANCEL);
        items.clear();
    }

    public void remove(final Item item) {
        items.remove(item);
    }

    // Called for missing service periods
    public void buildForMissingInterval(@Nullable final LocalDate startDate, @Nullable final LocalDate endDate, @Nullable final UUID targetInvoiceId, final Collection<Item> output, final boolean addRepair) {
        final Item item = createNewItem(startDate, endDate, targetInvoiceId, addRepair);
        if (item != null) {
            output.add(item);
        }
    }

    // Called on the last node
    public void buildFromItems(final Collection<Item> output, final boolean mergeMode) {
        buildForMissingInterval(null, null, null, output, mergeMode);
    }

    /**
     * Create a new item based on the existing items and new service period
     * <p/>
     * <ul>
     * <li>During the build phase, we only consider ADD items. This happens when for instance an existing item was partially repaired
     * and there is a need to create a new item which represents the part left -- that was not repaired.
     * <li>During the merge phase, we create new items that are the missing repaired items (CANCEL).
     * </ul>
     *
     * @param startDate start date of the new item to create
     * @param endDate   end date of the new item to create
     * @param mergeMode mode to consider.
     * @return new item for this service period or null
     */
    private Item createNewItem(@Nullable final LocalDate startDate, @Nullable final LocalDate endDate, @Nullable final UUID targetInvoiceId, final boolean mergeMode) {
        // Find the ADD (build phase) or CANCEL (merge phase) item of this interval
        final Item item = getResultingItem(mergeMode);
        if (item == null || startDate == null || endDate == null || targetInvoiceId == null) {
            return item;
        }

        // Prorate (build phase) or repair (merge phase) this item, as needed
        final InvoiceItem proratedInvoiceItem = item.toProratedInvoiceItem(startDate, endDate);
        if (proratedInvoiceItem == null) {
            return null;
        } else {
            // Keep track of the repaired amount for this item
            item.incrementCurrentRepairedAmount(proratedInvoiceItem.getAmount().abs());
            return new Item(proratedInvoiceItem, targetInvoiceId, item.getAction());
        }
    }

    private Item getResultingItem(final boolean mergeMode) {
        return mergeMode ? getResulting_CANCEL_Item() : getResulting_ADD_Item();
    }

    private Item getResulting_CANCEL_Item() {
        Preconditions.checkState(items.size() <= 1, "Too many items=%s", items);
        return getResulting_CANCEL_ItemNoChecks();
    }

    private Item getResulting_CANCEL_ItemNoChecks() {
        return findItem(ItemAction.CANCEL);
    }

    private Item getResulting_ADD_Item() {
        //
        // At this point we pruned the items so that we can have either:
        // - 2 items (ADD + CANCEL, where CANCEL does NOT point to ADD item -- otherwise this is a cancelling pair that
        //            would have been removed in mergeCancellingPairs logic)
        // - 1 ADD item, simple enough we return it
        // - 1 CANCEL, there is nothing to return but the period will be ignored by the parent
        // - Nothing at all; this valid, this just means its original items got removed during mergeCancellingPairs logic,
        //   but its NodeInterval has children so it could not be deleted.
        //
        Preconditions.checkState(items.size() <= 2, "Double billing detected: %s", items);

        final Collection<Item> addItems = findItems(ItemAction.ADD);
        Preconditions.checkState(addItems.size() <= 1, "Double billing detected: %s", items);

        final Item item = findItem(ItemAction.ADD);

        // Double billing sanity check across nodes
        if (item != null) {
            final Set<UUID> addItemsCancelled = new HashSet<UUID>();
            final Item cancelItem = findItem(ItemAction.CANCEL);
            if (cancelItem != null) {
                Preconditions.checkState(cancelItem.getLinkedId() != null, "Invalid CANCEL item=%s", cancelItem);
                addItemsCancelled.add(cancelItem.getLinkedId());
            }
            final Set<UUID> addItemsToBeCancelled = new HashSet<UUID>();
            checkDoubleBilling(addItemsCancelled, addItemsToBeCancelled);
        }

        return item;
    }

    private void checkDoubleBilling(final Set<UUID> addItemsCancelled, final Set<UUID> addItemsToBeCancelled) {
        final ItemsNodeInterval parentNodeInterval = (ItemsNodeInterval) interval.getParent();
        if (parentNodeInterval == null) {
            Preconditions.checkState(addItemsCancelled.equals(addItemsToBeCancelled), "Double billing detected: addItemsCancelled=%s, addItemsToBeCancelled=%s", addItemsCancelled, addItemsToBeCancelled);
            return;
        }
        final ItemsInterval parentItemsInterval = parentNodeInterval.getItemsInterval();

        final Item parentAddItem = parentItemsInterval.getResulting_ADD_Item();
        if (parentAddItem != null) {
            Preconditions.checkState(parentAddItem.getId() != null, "Invalid ADD item=%s", parentAddItem);
            addItemsToBeCancelled.add(parentAddItem.getId());
        }

        final Item parentCancelItem = parentItemsInterval.getResulting_CANCEL_ItemNoChecks();
        if (parentCancelItem != null) {
            Preconditions.checkState(parentCancelItem.getLinkedId() != null, "Invalid CANCEL item=%s", parentCancelItem);
            addItemsCancelled.add(parentCancelItem.getLinkedId());
        }

        parentItemsInterval.checkDoubleBilling(addItemsCancelled, addItemsToBeCancelled);
    }

    private Item findItem(final ItemAction itemAction) {
        final Collection<Item> matchingItems = findItems(itemAction);
        return matchingItems.size() == 1 ? matchingItems.iterator().next() : null;
    }

    private Collection<Item> findItems(final ItemAction itemAction) {
        return Collections2.<Item>filter(items,
                                         new Predicate<Item>() {
                                             @Override
                                             public boolean apply(final Item input) {
                                                 return input.getAction() == itemAction;
                                             }
                                         });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ItemsInterval{");
        sb.append("items=").append(items);
        sb.append('}');
        return sb.toString();
    }
}
