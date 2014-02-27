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
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class NodeInterval {

    private LocalDate start;
    private LocalDate end;
    private ItemsInterval items;

    private NodeInterval parent;
    private NodeInterval leftChild;
    private NodeInterval rightSibling;

    public NodeInterval() {
        this.items = new ItemsInterval(this);
    }

    public NodeInterval(final NodeInterval parent, final Item item) {
        this.start = item.getStartDate();
        this.end = item.getEndDate();
        this.items = new ItemsInterval(this, item);
        this.parent = parent;
        this.leftChild = null;
        this.rightSibling = null;
    }

    /**
     * Build the output list from the elements in the tree.
     * <p/>
     * In the simple mode, mergeMode = false, there is no limit in the depth of the tree,
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
     * and the goal is to generate the repair items; @see mergeProposedItem
     *
     * @param output    result list of items
     * @param mergeMode mode used to produce output list
     */
    public void build(final List<Item> output, final boolean mergeMode) {

        // There is no sub-interval, just add our own items.
        if (leftChild == null) {
            items.buildFromItems(output, mergeMode);
            return;
        }

        final NodeInterval lastChild = walkChildren(new ChildCallback() {

            // Start for the beginning of the interval and as we move through children keep the mark the end date for
            // each child period.
            LocalDate curDate = start;

            @Override
            public boolean performActionOnChild(final NodeInterval prevChild, final NodeInterval curChild) {
                // If there is a hole, that is no child, we build the missing piece from ourself
                if (curChild.getStart().compareTo(curDate) > 0) {
                    items.buildForMissingInterval(curDate, curChild.getStart(), output, mergeMode);
                }
                // Recursively build for the child
                curChild.build(output, mergeMode);
                curDate = curChild.getEnd();
                return false;
            }
        });
        // Finally if there is a hole at the end, we build the missing piece from ourself
        if (lastChild.getEnd().compareTo(end) < 0) {
            items.buildForMissingInterval(lastChild.getEnd(), end, output, mergeMode);
        }
    }

    private NodeInterval walkChildren(final ChildCallback callback) {

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            boolean shouldBreak = callback.performActionOnChild(prevChild, curChild);
            if (shouldBreak) {
                return curChild;
            }
            prevChild = curChild;
            curChild = curChild.getRightSibling();
        }
        return prevChild == null ? curChild : prevChild;
    }

    public interface ChildCallback {
        public boolean performActionOnChild(NodeInterval prevChild, NodeInterval child);
    }




    /**
     * The merge tree is initially constructed by flattening all the existing items and reversing them (CANCEL node).
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
     * @param newNode a new proposed item
     * @return true if the item was merged and will trigger a repair or false if the proposed item should be kept as such
     *         and no repair generated.
     */
    public boolean mergeProposedItem(final NodeInterval newNode) {

        Preconditions.checkState(newNode.getItems().size() == 1, "Expected new node to have only one item");
        final Item newNodeItem = newNode.getItems().get(0);

        if (!isRoot() && newNodeItem.getStartDate().compareTo(start) == 0 && newNodeItem.getEndDate().compareTo(end) == 0) {
            items.cancelItems(newNodeItem);
            return true;
        }
        computeRootInterval(newNode);

        if (leftChild == null) {
            // There is no existing items, only new proposed one, nothing to add in that merge tree
            if (isRoot()) {
                return false;
            } else {
                // Proposed item is the first child of an existing item with the same product info.
                newNode.parent = this;
                leftChild = newNode;
                return true;

            }
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        do {
            if (curChild.isItemContained(newNodeItem)) {
                final Item existingNodeItem = curChild.getItems().get(0);

                Preconditions.checkState(curChild.getItems().size() == 1, "Expected existing node to have only one item");
                if (existingNodeItem.isSameKind(newNodeItem)) {
                    // Proposed item has same product info than parent and is contained so insert it at the right place in the tree
                    curChild.mergeProposedItem(newNode);
                    return true;
                } else {
                    return false;
                }
            }

            if (newNodeItem.getStartDate().compareTo(curChild.getStart()) < 0) {
                newNode.parent = this;
                newNode.rightSibling = curChild;
                if (prevChild == null) {
                    leftChild = newNode;
                } else {
                    prevChild.rightSibling = newNode;
                }
                return true;
            }

            prevChild = curChild;
            curChild = curChild.rightSibling;
        } while (curChild != null);

        if (isRoot()) {
            // The new proposed item spans over a new interval, nothing to add in the merge tree
            return false;
        } else {
            newNode.parent = this;
            prevChild.rightSibling = newNode;
            return true;
        }
    }

    /**
     * Add an existing item in the tree of items.
     *
     * @param newNode new existing item to be added
     */
    public void addExistingItem(final NodeInterval newNode) {
        final Item item = newNode.getItems().get(0);
        if (!isRoot() && item.getStartDate().compareTo(start) == 0 && item.getEndDate().compareTo(end) == 0) {
            items.insertSortedItem(item);
            return;
        }
        computeRootInterval(newNode);
        addNode(newNode);
    }

    /**
     * Add the adjustment amount on the item specified by the targetId.
     *
     * @param adjustementDate date of the adjustment
     * @param amount amount of the adjustment
     * @param targetId item that has been adjusted
     */
    public void addAdjustment(final LocalDate adjustementDate, final BigDecimal amount, final UUID targetId) {
        NodeInterval node = findNode(adjustementDate, targetId);
        Preconditions.checkNotNull(node, "Cannot add adjustement for item = " + targetId + ", date = " + adjustementDate);
        node.setAdjustment(amount.negate(), targetId);
    }

    public boolean isItemContained(final Item item) {
        return (item.getStartDate().compareTo(start) >= 0 &&
                item.getStartDate().compareTo(end) <= 0 &&
                item.getEndDate().compareTo(start) >= 0 &&
                item.getEndDate().compareTo(end) <= 0);
    }

    public boolean isItemOverlap(final Item item) {
        return ((item.getStartDate().compareTo(start) < 0 &&
                 item.getEndDate().compareTo(end) >= 0) ||
                (item.getStartDate().compareTo(start) <= 0 &&
                 item.getEndDate().compareTo(end) > 0));
    }



    public boolean isRoot() {
        return parent == null;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    public NodeInterval getParent() {
        return parent;
    }

    public NodeInterval getLeftChild() {
        return leftChild;
    }

    public NodeInterval getRightSibling() {
        return rightSibling;
    }


    public int getNbChildren() {
        int result = 0;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            result++;
            curChild = curChild.rightSibling;
        }
        return result;
    }

    public List<Item> getItems() {
        return items.getItems();
    }

    public boolean containsItem(final UUID targetId) {
        return items.containsItem(targetId);
    }

    private void addNode(final NodeInterval newNode) {

        newNode.parent = this;
        final Item item = newNode.getItems().get(0);
        if (leftChild == null) {
            leftChild = newNode;
            return;
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.isItemContained(item)) {
                curChild.addExistingItem(newNode);
                return;
            }

            if (curChild.isItemOverlap(item)) {
                rebalance(newNode);
                return;
            }

            if (item.getStartDate().compareTo(curChild.getStart()) < 0) {
                newNode.rightSibling = curChild;
                if (prevChild == null) {
                    leftChild = newNode;
                } else {
                    prevChild.rightSibling = newNode;
                }
                return;
            }
            prevChild = curChild;
            curChild = curChild.rightSibling;
        }

        prevChild.rightSibling = newNode;
    }

    /**
     * Since items may be added out of order, there is no guarantee that we don't suddenly have a new node
     * whose interval emcompasses cuurent node(s). In which case we need to rebalance the tree.
     *
     * @param newNode node that triggered a rebalance operation
     */
    private void rebalance(final NodeInterval newNode) {

        final Item item = newNode.getItems().get(0);

        NodeInterval prevRebalanced = null;
        NodeInterval curChild = leftChild;
        List<NodeInterval> toBeRebalanced = Lists.newLinkedList();
        do {
            if (curChild.isItemOverlap(item)) {
                toBeRebalanced.add(curChild);
            } else {
                if (toBeRebalanced.size() > 0) {
                    break;
                }
                prevRebalanced = curChild;
            }
            curChild = curChild.rightSibling;
        } while (curChild != null);

        newNode.parent = this;
        final NodeInterval lastNodeToRebalance = toBeRebalanced.get(toBeRebalanced.size() - 1);
        newNode.rightSibling = lastNodeToRebalance.rightSibling;
        lastNodeToRebalance.rightSibling = null;
        if (prevRebalanced == null) {
            leftChild = newNode;
        } else {
            prevRebalanced.rightSibling = newNode;
        }

        NodeInterval prev = null;
        for (NodeInterval cur : toBeRebalanced) {
            cur.parent = newNode;
            if (prev == null) {
                newNode.leftChild = cur;
            } else {
                prev.rightSibling = cur;
            }
            prev = cur;
        }
    }

    private void computeRootInterval(final NodeInterval newNode) {
        if (!isRoot()) {
            return;
        }
        this.start = (start == null || start.compareTo(newNode.getStart()) > 0) ? newNode.getStart() : start;
        this.end = (end == null || end.compareTo(newNode.getEnd()) < 0) ? newNode.getEnd() : end;
    }

    private void setAdjustment(final BigDecimal amount, final UUID linkedId) {
        items.setAdjustment(amount, linkedId);
    }

    private NodeInterval findNode(final LocalDate date, final UUID targetItemId) {
        Preconditions.checkState(isRoot(), "findNode can only be called from root");
        return findNodeRecursively2(this, date, targetItemId);
    }

    // TODO That method should be use instead of findNodeRecursively2 to search the node more effectively using the time
    // but unfortunately that fails because of our test that use the wrong date when doing adjustments.
    private NodeInterval findNodeRecursively(final NodeInterval curNode, final LocalDate date, final UUID targetItemId) {
        if (date.compareTo(curNode.getStart()) < 0 || date.compareTo(curNode.getEnd()) > 0) {
            return null;
        }
        NodeInterval curChild = curNode.getLeftChild();
        while (curChild != null) {
            if (curChild.getStart().compareTo(date) <= 0 && curChild.getEnd().compareTo(date) >= 0) {
                if (curChild.containsItem(targetItemId)) {
                    return curChild;
                } else {
                    return findNodeRecursively(curChild, date, targetItemId);
                }
            }
            curChild = curChild.getRightSibling();
        }
        return null;
    }

    private NodeInterval findNodeRecursively2(final NodeInterval curNode, final LocalDate date, final UUID targetItemId) {

        if (!curNode.isRoot() && curNode.containsItem(targetItemId)) {
            return curNode;
        }

        NodeInterval curChild = curNode.getLeftChild();
        while (curChild != null) {
            final NodeInterval result = findNodeRecursively2(curChild, date, targetItemId);
            if (result != null) {
                return result;
            }
            curChild = curChild.getRightSibling();
        }
        return null;
    }
}
