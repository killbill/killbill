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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.tree.Item.ItemAction;
import org.killbill.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

/**
 * Node in the SubscriptionItemTree
 */
public class ItemsNodeInterval extends NodeInterval {

    private final ItemsInterval items;

    public ItemsNodeInterval() {
        this.items = new ItemsInterval(this);
    }

    private ItemsNodeInterval(final ItemsInterval items) {
        this.items = items;
    }

    public ItemsNodeInterval(final NodeInterval parent, final Item item) {
        super(parent, item.getStartDate(), item.getEndDate());
        this.items = new ItemsInterval(this, item);
    }

    public ItemsNodeInterval(final NodeInterval parent, final LocalDate startDate, final LocalDate endDate) {
        super(parent, startDate, endDate);
        this.items = new ItemsInterval(this);
    }

    public ItemsNodeInterval[] split(final LocalDate splitDate) {

        Preconditions.checkState(splitDate.compareTo(start) > 0 && splitDate.compareTo(end) < 0,
                                 String.format("Unexpected item split with startDate='%s' and endDate='%s', splitDate='%s'", start, end, splitDate));

        Preconditions.checkState(leftChild == null);
        Preconditions.checkState(rightSibling == null);

        final List<Item> rawItems = items.getItems();
        Preconditions.checkState(rawItems.size() == 1, "Interval should have a single item: " + rawItems);

        final Item[] splitItems = rawItems.get(0).split(splitDate);

        final ItemsNodeInterval split1 = new ItemsNodeInterval(this.parent, splitItems[0]);
        final ItemsNodeInterval split2 = new ItemsNodeInterval(this.parent, splitItems[1]);
        final ItemsNodeInterval[] result = new ItemsNodeInterval[2];
        result[0] = split1;
        result[1] = split2;
        return result;
    }

    @JsonIgnore
    public ItemsInterval getItemsInterval() {
        return items;
    }

    public List<Item> getItems() {
        return items.getItems();
    }

    /**
     * Add existing item into the tree
     *
     * @param newNode an existing item
     */
    public void addExistingItem(final ItemsNodeInterval newNode) {
        Preconditions.checkState(newNode.getItems().size() == 1, "Invalid node=%s", newNode);

        addNode(newNode,
                new AddNodeCallback() {
                    @Override
                    public boolean onExistingNode(final NodeInterval existingNode, final ItemsNodeInterval updatedNewNode) {

                        final Item item = updatedNewNode.getItems().get(0);
                        final ItemsInterval existingOrNewNodeItems = ((ItemsNodeInterval) existingNode).getItemsInterval();
                        existingOrNewNodeItems.add(item);
                        // There is no new node added but instead we just populated the list of items for the already existing node
                        return false;
                    }

                    @Override
                    public boolean shouldInsertNode(final NodeInterval insertionNode, final ItemsNodeInterval updatedNewNode) {
                        // Always want to insert node in the tree when we find the right place.
                        return true;
                    }
                });
    }

    /**
     * <p/>
     * There is no limit in the depth of the tree,
     * and the build strategy is to first consider the lowest child for a given period
     * and go up the tree adding missing interval if needed. For e.g, one of the possible scenario:
     * <pre>
     * D1                                                  D2
     * |---------------------------------------------------|   Plan P1
     *       D1'             D2'
     *       |---------------|/////////////////////////////|   Plan P2, REPAIR
     *
     *  In that case we will generate:
     *  [D1,D1') on Plan P1; [D1', D2') on Plan P2, and [D2', D2) repair item
     *
     * <pre/>
     *
     * In the merge mode, the strategy is different, the tree is fairly shallow
     * and the goal is to generate the repair items; @see addProposedItem
     *
     * @param output result list of built items
     * @param targetInvoiceId
     */
    public void buildForExistingItems(final Collection<Item> output, final UUID targetInvoiceId) {
        // Sanity on the tree
        validateTree();
        build(output, targetInvoiceId, false);
    }

    /**
     * Add proposed item into the (flattened and reversed) tree
     *
     * @param newNode a new proposed item
     * @return true if the item was merged and will trigger a repair or false if the proposed item should be kept as such and no repair generated
     */
    public List<ItemsNodeInterval> addProposedItem(final ItemsNodeInterval newNode) {
        Preconditions.checkState(newNode.getItems().size() == 1, "Invalid node=%s", newNode);

        final List<ItemsNodeInterval> newNodes = new LinkedList<>();

        addNode(newNode, new AddNodeCallback() {
            @Override
            public boolean onExistingNode(final NodeInterval existingNode, final ItemsNodeInterval updatedNewNode) {

                final Item item = updatedNewNode.getItems().get(0);

                // If we receive a new proposed that is the same kind as the reversed existing (current node),
                // we match existing and proposed. If not, we keep the proposed item as-is outside of the tree.
                if (isSameKind((ItemsNodeInterval) existingNode, item)) {
                    final ItemsInterval existingOrNewNodeItems = ((ItemsNodeInterval) existingNode).getItemsInterval();
                    existingOrNewNodeItems.cancelItems(item);
                    return true;
                } else {
                    newNodes.add(updatedNewNode);
                    return false;
                }
            }

            @Override
            public boolean shouldInsertNode(final NodeInterval insertionNode, final ItemsNodeInterval updatedNewNode) {

                final Item item = updatedNewNode.getItems().get(0);

                // At this stage, we're currently merging a proposed item that does not fit any of the existing intervals.
                // If this new node is about to be inserted at the root level, this means the proposed item overlaps any
                // existing item. We keep these as-is, outside of the tree: they will become part of the resulting list.
                if (insertionNode.isRoot()) {
                    // If updatedNewNode was rebalanced and it is fully repaired by its children it just gets canceled out.
                    LocalDate curDate = updatedNewNode.start;
                    NodeInterval curChild = updatedNewNode.leftChild;
                    while (curChild != null &&
                           curChild.start.equals(curDate) &&
                           isSameKind((ItemsNodeInterval) curChild, item)) {
                        curDate = curChild.end;
                        curChild = curChild.rightSibling;
                    }

                    if (curDate.equals(updatedNewNode.end)) {
                        updatedNewNode.getItems().clear();
                        curChild = updatedNewNode.leftChild;
                        while (curChild != null) {
                            ((ItemsNodeInterval) curChild).getItems().clear();
                            curChild = curChild.rightSibling;
                        }
                        return false;
                    }

                    newNodes.add(updatedNewNode);
                    return false;
                }

                // If we receive a new proposed that is the same kind as the reversed existing (parent node),
                // we want to insert it to generate a piece of repair (see SubscriptionItemTree#buildForMerge).
                // If not, we keep the proposed item as-is outside of the tree.
                final boolean result = isSameKind((ItemsNodeInterval) insertionNode, item);
                if (!result) {
                    newNodes.add(updatedNewNode);
                }
                return result;
            }

            private boolean isSameKind(final ItemsNodeInterval insertionNode, final Item item) {
                final List<Item> insertionNodeItems = insertionNode.getItems();
                Preconditions.checkState(insertionNodeItems.size() == 1, "Expected existing node to have only one item");
                final Item insertionNodeItem = insertionNodeItems.get(0);
                return insertionNodeItem.isSameKind(item);
            }
        });

        return newNodes;
    }

    /**
     * The merge tree is initially constructed by flattening all the existing items and reversing them (CANCEL node).
     * <p/>
     * That means that if we were to not merge any new proposed items, we would end up with only those reversed existing
     * items, and they would all end up repaired-- which is what we want.
     * <p/>
     * However, if there are new proposed items, then we look to see if they are children one our existing reverse items
     * so that we can generate the repair pieces missing. For e.g, below is one scenario among so many:
     * <p/>
     * <pre>
     * D1                                                  D2
     * |---------------------------------------------------| (existing reversed (CANCEL) item
     *       D1'             D2'
     *       |---------------| (proposed same plan)
     * </pre>
     * In that case we want to generated a repair for [D1, D1') and [D2',D2)
     * <p/>
     * Note that this tree is never very deep, only 3 levels max, with exiting at the first level
     * and proposed that are the for the exact same plan but for different dates below.
     *
     * @param output result list of built items
     */
    public void mergeExistingAndProposed(final Collection<Item> output, final UUID targetInvoiceId) {
        build(output, targetInvoiceId, true);
    }

    /**
     * Add the adjustment amount on the item specified by the targetId.
     *
     * @return linked item if fully adjusted, null otherwise
     */
    public Item addAdjustment(final InvoiceItem item) {
        final UUID targetId = item.getLinkedItemId();

        final NodeInterval node = findNode(new SearchCallback() {
            @Override
            public boolean isMatch(final NodeInterval curNode) {
                return ((ItemsNodeInterval) curNode).getItemsInterval().findItem(targetId) != null;
            }
        });
        Preconditions.checkNotNull(node, "Unable to find item interval for id='%s', tree=%s", targetId, this);

        final ItemsInterval targetItemsInterval = ((ItemsNodeInterval) node).getItemsInterval();
        final Item targetItem = targetItemsInterval.findItem(targetId);
        Preconditions.checkNotNull(targetItem, "Unable to find item with id='%s', itemsInterval=%s", targetId, targetItemsInterval);

        final BigDecimal adjustmentAmount = item.getAmount().negate();
        targetItem.incrementAdjustedAmount(adjustmentAmount);
        return null;
    }

    private void build(final Collection<Item> output, final UUID targetInvoiceId, final boolean mergeMode) {
        final List<Item> tmpOutput = new LinkedList<Item>(output);
        output.clear();
        build(new BuildNodeCallback() {
            @Override
            public void onMissingInterval(final NodeInterval curNode, final LocalDate startDate, final LocalDate endDate) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                items.buildForMissingInterval(startDate, endDate, targetInvoiceId, tmpOutput, mergeMode);
            }

            @Override
            public void onLastNode(final NodeInterval curNode) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                items.buildFromItems(tmpOutput, mergeMode);
            }
        });

        //
        // Join items that were previously split to fit in the tree as necessary.
        //
        // 1. Build a map for each item pointing to a heap of (potential) split items
        final Map<UUID, PriorityQueue<Item>> joinMap = new HashMap<>();
        for (final Item i : tmpOutput) {
            PriorityQueue<Item> l = joinMap.get(i.getId());
            if (l == null) {
                l = new PriorityQueue<>(new Comparator<Item>() {
                    @Override
                    public int compare(final Item o1, final Item o2) {
                        return o1.getStartDate().compareTo(o2.getEndDate());
                    }
                });
                joinMap.put(i.getId(), l);
            }
            l.add(i);
        }

        // 2. For each entry in the map, check which items can be re-joined based on their contiguous periods
        for (final PriorityQueue<Item> v : joinMap.values()) {

            Item prev = v.poll();
            Item cur = null;
            while ((cur = v.poll()) != null) {
                if (prev.getEndDate().compareTo(cur.getStartDate()) == 0) {
                    prev = Item.join(prev, cur);
                } else {
                    output.add(prev);
                    prev = cur;
                }
            }
            if (prev != null) {
                output.add(prev);
            }
        }
    }

    //
    // This is not strictly necessary -- just there to add a layer of sanity on what our tree contains
    //
    private void validateTree() {
        final NodeInterval root = this;
        walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {

                if (curNode.isRoot()) {
                    return;
                }

                final ItemsInterval curNodeItems = ((ItemsNodeInterval) curNode).getItemsInterval();
                for (final Item curCancelItem : curNodeItems.get_CANCEL_items()) {
                    // Sanity: cancelled items should only be in the same node or parents
                    if (curNode.getLeftChild() != null) {
                        curNode.getLeftChild()
                               .walkTree(new WalkCallback() {
                                   @Override
                                   public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                                       final ItemsInterval curChildItems = ((ItemsNodeInterval) curNode).getItemsInterval();
                                       final Item cancelledItem = curChildItems.getCancelledItemIfExists(curCancelItem.getLinkedId());
                                       Preconditions.checkState(cancelledItem == null, "Invalid cancelledItem=%s for cancelItem=%s", cancelledItem, curCancelItem);
                                   }
                               });
                    }

                    // Sanity: make sure the CANCEL item points to an ADD item
                    final NodeInterval nodeIntervalForCancelledItem = root.findNode(new SearchCallback() {
                        @Override
                        public boolean isMatch(final NodeInterval curNode) {
                            final ItemsInterval curChildItems = ((ItemsNodeInterval) curNode).getItemsInterval();
                            final Item cancelledItem = curChildItems.getCancelledItemIfExists(curCancelItem.getLinkedId());
                            return cancelledItem != null;
                        }
                    });
                    Preconditions.checkState(nodeIntervalForCancelledItem != null, "Missing cancelledItem for cancelItem=%s", curCancelItem);
                }

                for (final Item curAddItem : curNodeItems.get_ADD_items()) {
                    // Sanity: verify the item hasn't been repaired too much
                    if (curNode.getLeftChild() != null) {
                        final AtomicReference<BigDecimal> totalRepaired = new AtomicReference<BigDecimal>(BigDecimal.ZERO);
                        curNode.getLeftChild()
                               .walkTree(new WalkCallback() {
                                   @Override
                                   public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                                       final ItemsInterval curChildItems = ((ItemsNodeInterval) curNode).getItemsInterval();
                                       final Item cancelledItem = curChildItems.getCancellingItemIfExists(curAddItem.getId());
                                       if (cancelledItem != null && curAddItem.getId().equals(cancelledItem.getLinkedId())) {
                                           totalRepaired.set(totalRepaired.get().add(cancelledItem.getAmount()));
                                       }
                                   }
                               });
                        Preconditions.checkState(curAddItem.getNetAmount().compareTo(totalRepaired.get()) >= 0, "Item %s overly repaired", curAddItem);
                    }

                    // Old behavior compatibility for full item adjustment (Temp code should go away as move in time)
                    // If we see a fully adjusted item and an existing child (one ADD item), we discard the fully adjusted item
                    // in such a way that we are left with the child that will look like the proposed and nothing will be generated.
                    if (curAddItem.isFullyAdjusted()) {
                        final NodeInterval leftChild = curNode.getLeftChild();
                        if (leftChild != null) {
                            final ItemsInterval leftChildItems = ((ItemsNodeInterval) leftChild).getItemsInterval();
                            if (leftChildItems.getItems().size() == 1 && leftChildItems.getItems().get(0).getAction() == ItemAction.ADD) {
                                curNodeItems.remove(curAddItem);
                            }
                        }
                    }
                }
            }
        });
    }

    @VisibleForTesting
    public void jsonSerializeTree(final ObjectMapper mapper, final OutputStream output) throws IOException {
        final JsonGenerator generator = mapper.getFactory().createGenerator(output);
        generator.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        walkTree(new WalkCallback() {

            private int curDepth = 0;

            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                final ItemsNodeInterval node = (ItemsNodeInterval) curNode;
                if (node.isRoot()) {
                    return;
                }

                try {
                    if (curDepth < depth) {
                        generator.writeStartArray();
                        curDepth = depth;
                    } else if (curDepth > depth) {
                        generator.writeEndArray();
                        curDepth = depth;
                    }
                    generator.writeObject(node);
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to deserialize tree", e);
                }
            }
        });
        generator.close();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ItemsNodeInterval{");
        sb.append("items=").append(items);
        sb.append('}');
        return sb.toString();
    }
}
