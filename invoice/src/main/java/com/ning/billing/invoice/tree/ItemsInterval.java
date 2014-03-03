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

import com.ning.billing.invoice.tree.Item.ItemAction;

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
     */
    public void buildFromItems(final List<Item> output, final boolean mergeMode) {
        final Item item  = getResultingItem(mergeMode);
        if (item != null) {
            output.add(item);
        }
    }

    private Item getResultingItem(final boolean mergeMode) {
        return mergeMode ? getResulting_CANCEL_Item() : getResulting_ADD_Item();
    }

    private Item getResulting_CANCEL_Item() {
        Preconditions.checkState(items.size() == 0 || items.size() == 1);
        return Iterables.tryFind(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getAction() == ItemAction.CANCEL;
            }
        }).orNull();
    }


    private Item getResulting_ADD_Item() {

        final Set<UUID> repairedIds = new HashSet<UUID>();
        final ListIterator<Item> it = items.listIterator(items.size());

        while (it.hasPrevious()) {
            final Item cur = it.previous();
            switch (cur.getAction()) {
                case ADD:
                    // If we found a CANCEL item pointing to that item then don't return it as it was repair (full repair scenario)
                    if (!repairedIds.contains(cur.getId())) {
                        return cur;
                    }
                    break;

                case CANCEL:
                    // In all cases populate the set with the id of target item being repaired
                    if (cur.getLinkedId() != null) {
                        repairedIds.add(cur.getLinkedId());
                    }
                    break;
            }
        }
        return null;
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

        final Item item  = getResultingItem(mergeMode);
        if (item == null) {
            return null;
        }

        final Item result = new Item(item.toProratedInvoiceItem(startDate, endDate), item.getAction());
        if (item.getAction() == ItemAction.CANCEL && result != null) {
            item.incrementCurrentRepairedAmount(result.getAmount());
        }
        return result;
    }
}
