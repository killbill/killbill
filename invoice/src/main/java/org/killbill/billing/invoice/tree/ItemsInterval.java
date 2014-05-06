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

package org.killbill.billing.invoice.tree;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.UUID;

import org.joda.time.LocalDate;

import org.killbill.billing.invoice.tree.Item.ItemAction;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Keeps track of all the items existing on a specified interval.
 */
public class ItemsInterval {

    private final NodeInterval interval;
    private LinkedList<Item> items;

    public ItemsInterval(final NodeInterval interval) {
        this(interval, null);
    }

    public ItemsInterval(final NodeInterval interval, final Item initialItem) {
        this.interval = interval;
        this.items = Lists.newLinkedList();
        if (initialItem != null) {
            items.add(initialItem);
        }
    }

    public boolean containsItem(final UUID targetId) {
        return Iterables.tryFind(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getId().equals(targetId);
            }
        }).orNull() != null;
    }

    public void setAdjustment(final BigDecimal amount, final UUID targetId) {
        final Item item = Iterables.tryFind(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getId().equals(targetId);
            }
        }).get();
        item.incrementAdjustedAmount(amount);
    }

    public List<Item> getItems() {
        return items;
    }

    public void buildForMissingInterval(final LocalDate startDate, final LocalDate endDate, final List<Item> output, final boolean addRepair) {
        final Item item = createNewItem(startDate, endDate, addRepair);
        if (item != null) {
            output.add(item);
        }
    }

    /**
     * Determines what is left based on the mergeMode and the action for each item.
     *
     * @param output
     * @param mergeMode
     * @return whether or not the parent should ignore the interval covered by the child interval
     */
    public boolean buildFromItems(final List<Item> output, final boolean mergeMode) {
        final ItemWithResult itemWithResult  = getResultingItem(mergeMode);
        if (itemWithResult.getItem() != null) {
            output.add(itemWithResult.getItem());
        }
        return itemWithResult.isIgnorePeriod();
    }

    private ItemWithResult getResultingItem(final boolean mergeMode) {
        return mergeMode ? getResulting_CANCEL_Item() : getResulting_ADD_Item();
    }

    private ItemWithResult getResulting_CANCEL_Item() {
        Preconditions.checkState(items.size() == 0 || items.size() == 1);
        return new ItemWithResult(Iterables.tryFind(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getAction() == ItemAction.CANCEL;
            }
        }).orNull(), true);
    }


    private ItemWithResult getResulting_ADD_Item() {

        final Set<UUID> repairedIds = new HashSet<UUID>();
        final ListIterator<Item> it = items.listIterator(items.size());

        //
        // We can have an {0,n} pairs of ADD/CANCEL (cancelling each other), and in addition to that we could have:
        // a - One ADD that has not been cancelled => We want to return that item and let the parent (NodeInterval) know that it should ignore
        //   the period as this is accounted for with the item returned
        // b - One CANCEL => We return NO item but we also want the parent to know that it should ignore
        //   the period as this is accounted -- period should remain unbilled.
        // c - nothing => The parent should NOT ignore the period as there is no child element that can account for it.
        //
        while (it.hasPrevious()) {
            final Item cur = it.previous();
            switch (cur.getAction()) {
                case ADD:
                    // If we found a CANCEL item pointing to that item then don't return it as it was repair (full repair scenario)
                    if (!repairedIds.contains(cur.getId())) {
                        // Case a
                        return new ItemWithResult(cur, true);
                    } else {
                        // Remove from the list so we know if there is anything else (case b or c)
                        repairedIds.remove(cur.getId());
                    }

                case CANCEL:
                    // In all cases populate the set with the id of target item being repaired
                    if (cur.getLinkedId() != null) {
                        repairedIds.add(cur.getLinkedId());
                    }
                    break;
            }
        }
        return repairedIds.size() > 0 ?
               new ItemWithResult(null, true) :  /* case b */
               new ItemWithResult(null, false); /* case c */
    }


    // Just ensure that ADD items precedes CANCEL items
    public void insertSortedItem(final Item item) {
        items.add(item);
        Collections.sort(items, new Comparator<Item>() {
            @Override
            public int compare(final Item o1, final Item o2) {
                if (o1.getAction() == ItemAction.ADD && o2.getAction() == ItemAction.CANCEL) {
                    return -1;
                } else if (o1.getAction() == ItemAction.CANCEL && o2.getAction() == ItemAction.ADD) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
    }

    public void cancelItems(final Item item) {
        Preconditions.checkState(item.getAction() == ItemAction.ADD);
        Preconditions.checkState(items.size() == 1);
        Preconditions.checkState(items.get(0).getAction() == ItemAction.CANCEL);
        items.clear();
    }

    /**
     * Creates a new item.
     * <p/>
     * <ul>
     * <li>In normal mode, we only consider ADD items. This happens when for instance an existing item was partially repaired
     * and there is a need to create a new item which represents the part left -- that was not repaired.
     * <li>In mergeMode, we allow to create new items that are the missing repaired items (CANCEL).
     * </ul>
     *
     * @param startDate start date of the new item to create
     * @param endDate   end date of the new item to create
     * @param mergeMode mode to consider.
     * @return
     */
    private Item createNewItem(LocalDate startDate, LocalDate endDate, final boolean mergeMode) {

        final ItemWithResult itemWithResult  = getResultingItem(mergeMode);
        final Item item = itemWithResult.getItem();
        if (item == null) {
            return null;
        }

        final Item result = new Item(item.toProratedInvoiceItem(startDate, endDate), item.getAction());
        if (item.getAction() == ItemAction.CANCEL && result != null) {
            item.incrementCurrentRepairedAmount(result.getAmount());
        }
        return result;
    }

    private final class ItemWithResult {
        private final Item item;
        private final boolean ignorePeriod;

        private ItemWithResult(final Item item, final boolean ignorePeriod) {
            this.item = item;
            this.ignorePeriod = ignorePeriod;
        }
        public Item getItem() {
            return item;
        }
        public boolean isIgnorePeriod() {
            return ignorePeriod;
        }
    }
}
