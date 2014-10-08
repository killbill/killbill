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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    private final UUID targetInvoiceId;
    private final NodeInterval interval;
    private LinkedList<Item> items;

    public ItemsInterval(final NodeInterval interval, final UUID targetInvoiceId) {
        this(interval, targetInvoiceId, null);
    }

    public ItemsInterval(final NodeInterval interval, final UUID targetInvoiceId, final Item initialItem) {
        this.interval = interval;
        this.targetInvoiceId = targetInvoiceId;
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
    public void buildFromItems(final List<Item> output, final boolean mergeMode) {
        final Item item = getResultingItem(mergeMode);
        if (item != null) {
            output.add(item);
        }
    }

    /**
     * Remove all the cancelling pairs (ADD/CANCEL) for which CANCEL linkedId points to ADD id.
     *
     * @return true if there is no more items
     */
    public boolean mergeCancellingPairs() {

        final Map<UUID, List<Item>> tmp = new HashMap<UUID, List<Item>>();
        for (Item cur : items) {
            final UUID idToConsider = (cur.getAction() == ItemAction.ADD) ? cur.getId() : cur.getLinkedId();
            List<Item> listForItem = tmp.get(idToConsider);
            if (listForItem == null) {
                listForItem = new ArrayList<Item>(2);
                tmp.put(idToConsider, listForItem);
            }
            listForItem.add(cur);
        }

        for (List<Item> listForIds : tmp.values()) {
            if (listForIds.size() == 2) {
                items.remove(listForIds.get(0));
                items.remove(listForIds.get(1));
            }
        }
        return items.size() == 0;
    }

    public Iterable<Item> get_ADD_items() {
        return Iterables.filter(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getAction() == ItemAction.ADD;
            }
        });
    }

    public NodeInterval getNodeInterval() {
        return interval;
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

        //
        // At this point we pruned the items so that we can have either:
        // - 2 items (ADD + CANCEL, where CANCEL does NOT point to ADD item-- otherwise this is a cancelling pair that
        //            would have been removed in mergeCancellingPairs logic)
        // - 1 ADD item, simple enough we return it
        // - 1 CANCEL, there is nothing to return but the period will be ignored by the parent
        // - Nothing at all; this valid, this just means its original items got removed during mergeCancellingPairs logic,
        //   but its NodeInterval has children so it could not be deleted.
        //
        Preconditions.checkState(items.size() <= 2);

        final Item item = items.size() > 0 && items.get(0).getAction() == ItemAction.ADD ? items.get(0) : null;
        return item;
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

    public void remove(final Item item) {
        items.remove(item);
    }

    public Item getCancelledItemIfExists(final UUID targetId) {
        final Item item = Iterables.tryFind(items, new Predicate<Item>() {
            @Override
            public boolean apply(final Item input) {
                return input.getAction() == ItemAction.CANCEL && input.getLinkedId().equals(targetId);
            }
        }).orNull();
        return item;
    }

    public int size() {
        return items.size();
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

        final Item item = getResultingItem(mergeMode);
        if (item == null) {
            return null;
        }

        final Item result = new Item(item.toProratedInvoiceItem(startDate, endDate), targetInvoiceId, item.getAction());
        if (item.getAction() == ItemAction.CANCEL && result != null) {
            item.incrementCurrentRepairedAmount(result.getAmount());
        }
        return result;
    }

}
