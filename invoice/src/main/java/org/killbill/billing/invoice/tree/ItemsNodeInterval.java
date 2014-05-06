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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import org.killbill.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.base.Preconditions;

public class ItemsNodeInterval extends NodeInterval {

    private ItemsInterval items;

    public ItemsNodeInterval() {
        this.items = new ItemsInterval(this);
    }

    public ItemsNodeInterval(final NodeInterval parent, final Item item) {
        super(parent, item.getStartDate(), item.getEndDate());
        this.items = new ItemsInterval(this, item);
    }

    @JsonIgnore
    public ItemsInterval getItemsInterval() {
        return items;
    }

    public List<Item> getItems() {
        return items.getItems();
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
     */
    public void buildForExistingItems(final List<Item> output) {
        build(new BuildNodeCallback() {
            @Override
            public void onMissingInterval(final NodeInterval curNode, final LocalDate startDate, final LocalDate endDate) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                items.buildForMissingInterval(startDate, endDate, output, false);
            }

            @Override
            public boolean onLastNode(final NodeInterval curNode) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                return items.buildFromItems(output, false);
            }
        });
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
    public void mergeExistingAndProposed(final List<Item> output) {
        build(new BuildNodeCallback() {
            @Override
            public void onMissingInterval(final NodeInterval curNode, final LocalDate startDate, final LocalDate endDate) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                items.buildForMissingInterval(startDate, endDate, output, true);
            }

            @Override
            public boolean onLastNode(final NodeInterval curNode) {
                final ItemsInterval items = ((ItemsNodeInterval) curNode).getItemsInterval();
                return items.buildFromItems(output, true);
            }
        });
    }

    /**
     * Add existing item into the tree.
     *
     * @param newNode an existing item
     */
    public boolean addExistingItem(final ItemsNodeInterval newNode) {

        return addNode(newNode, new AddNodeCallback() {
            @Override
            public boolean onExistingNode(final NodeInterval existingNode) {
                if (!existingNode.isRoot() && newNode.getStart().compareTo(existingNode.getStart()) == 0 && newNode.getEnd().compareTo(existingNode.getEnd()) == 0) {
                    final Item item = newNode.getItems().get(0);
                    final ItemsInterval existingOrNewNodeItems = ((ItemsNodeInterval) existingNode).getItemsInterval();
                    existingOrNewNodeItems.insertSortedItem(item);
                }
                // There is no new node added but instead we just populated the list of items for the already existing node.
                return false;
            }

            @Override
            public boolean shouldInsertNode(final NodeInterval insertionNode) {
                // Always want to insert node in the tree when we find the right place.
                return true;
            }
        });
    }

    /**
     * Add proposed item into the (flattened and reversed) tree.
     *
     * @param newNode a new proposed item
     * @return true if the item was merged and will trigger a repair or false if the proposed item should be kept as such
     *         and no repair generated.
     */
    public boolean addProposedItem(final ItemsNodeInterval newNode) {

        return addNode(newNode, new AddNodeCallback() {
            @Override
            public boolean onExistingNode(final NodeInterval existingNode) {
                if (!shouldInsertNode(existingNode)) {
                    return false;
                }

                Preconditions.checkState(newNode.getStart().compareTo(existingNode.getStart()) == 0 && newNode.getEnd().compareTo(existingNode.getEnd()) == 0);
                final Item item = newNode.getItems().get(0);
                final ItemsInterval existingOrNewNodeItems = ((ItemsNodeInterval) existingNode).getItemsInterval();
                existingOrNewNodeItems.cancelItems(item);
                // In the merge logic, whether we really insert the node or find an existing node on which to insert items should be seen
                // as an insertion (so as to avoid keeping that proposed item, see how return value of addProposedItem is used)
                return true;
            }

            @Override
            public boolean shouldInsertNode(final NodeInterval insertionNode) {
                // The root level is solely for the reversed existing items. If there is a new node that does not fit below the level
                // of reversed existing items, we want to return false and keep it outside of the tree. It should be 'kept as such'.
                if (insertionNode.isRoot()) {
                    return false;
                }

                final ItemsInterval insertionNodeItems = ((ItemsNodeInterval) insertionNode).getItemsInterval();
                Preconditions.checkState(insertionNodeItems.getItems().size() == 1, "Expected existing node to have only one item");
                final Item insertionNodeItem = insertionNodeItems.getItems().get(0);
                final Item newNodeItem = newNode.getItems().get(0);

                // If we receive a new proposed that is the same kind as the reversed existing we want to insert it to generate
                // a piece of repair
                if (insertionNodeItem.isSameKind(newNodeItem)) {
                    return true;
                    // If not, then keep the proposed outside of the tree.
                } else {
                    return false;
                }
            }
        });
    }

    /**
     * Add the adjustment amount on the item specified by the targetId.
     *
     * @param adjustementDate date of the adjustment
     * @param amount          amount of the adjustment
     * @param targetId        item that has been adjusted
     */
    public void addAdjustment(final LocalDate adjustementDate, final BigDecimal amount, final UUID targetId) {
        // TODO we should really be using findNode(adjustementDate, new SearchCallback() instead but wrong dates in test
        // creates test panic.
        final NodeInterval node = findNode(new SearchCallback() {
            @Override
            public boolean isMatch(final NodeInterval curNode) {
                return ((ItemsNodeInterval) curNode).getItemsInterval().containsItem(targetId);
            }
        });
        Preconditions.checkNotNull(node, "Cannot add adjustement for item = " + targetId + ", date = " + adjustementDate);
        ((ItemsNodeInterval) node).setAdjustment(amount.negate(), targetId);
    }

    public void jsonSerializeTree(final ObjectMapper mapper, final OutputStream output) throws IOException {

        final JsonGenerator generator = mapper.getFactory().createJsonGenerator(output);
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
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deserialize tree", e);
                }
            }
        });
        generator.close();
    }

    protected void setAdjustment(final BigDecimal amount, final UUID linkedId) {
        items.setAdjustment(amount, linkedId);
    }

}
