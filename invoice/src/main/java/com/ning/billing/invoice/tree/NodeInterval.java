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

    public void build(final List<Item> output, boolean isParentRepair, boolean mergeMode) {

        // There is no sub-interval, just add our own items.
        if (leftChild == null) {
            items.buildFromItems(output, isParentRepair, mergeMode);
            return;
        }

        LocalDate curDate = start;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.getStart().compareTo(curDate) > 0) {
                items.buildForMissingInterval(curDate, curChild.getStart(), output, mergeMode);
            }
            curChild.build(output, isRepairNode(), mergeMode);
            curDate = curChild.getEnd();
            curChild = curChild.getRightSibling();
        }
        if (curDate.compareTo(end) < 0) {
            items.buildForMissingInterval(curDate, end, output, mergeMode);
        }
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

    public void addAdjustment(final LocalDate adjustementDate, final BigDecimal amount, final UUID linkedId) {
        NodeInterval node = findNode(adjustementDate, linkedId);
        if (node == null) {
            throw new TreeNodeException("Cannot add adjustement for item = " + linkedId + ", date = " + adjustementDate);
        }
        node.setAdjustment(amount.negate(), linkedId);
    }

    private void setAdjustment(final BigDecimal amount, final UUID linkedId) {
        items.setAdjustment(amount, linkedId);
    }

    private NodeInterval findNode(final LocalDate date, final UUID targetItemId) {
        if (!isRoot()) {
            throw new TreeNodeException("findNode can only be called from root");
        }
        return findNodeRecursively2(this, date, targetItemId);
    }

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


    public boolean mergeProposedItem(final NodeInterval newNode) {

        Preconditions.checkState(newNode.getItems().size() == 1, "Expected new node to have only one item");
        final Item newNodeItem = newNode.getItems().get(0);

        if (!isRoot() && newNodeItem.getStartDate().compareTo(start) == 0 && newNodeItem.getEndDate().compareTo(end) == 0) {
            items.cancelItems(newNodeItem);
            return true;
        }
        computeRootInterval(newNode);

        if (leftChild == null) {
            leftChild = newNode;
            return true;
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        do {
            Preconditions.checkState(!curChild.isItemOverlap(newNodeItem), "Item " + newNodeItem + " overlaps " + curChild);

            if (curChild.isItemContained(newNodeItem)) {
                final Item existingNodeItem = curChild.getItems().get(0);

                Preconditions.checkState(curChild.getItems().size() == 1, "Expected existing node to have only one item");
                if (existingNodeItem.isSameKind(newNodeItem)) {
                    curChild.mergeProposedItem(newNode);
                    return true;
                } else {
                    return false;
                }
            }

            if (newNodeItem.getStartDate().compareTo(curChild.getStart()) < 0) {
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

        prevChild.rightSibling = newNode;
        return true;
    }

    public void addNodeInterval(final NodeInterval newNode) {
        final Item item = newNode.getItems().get(0);
        if (!isRoot() && item.getStartDate().compareTo(start) == 0 && item.getEndDate().compareTo(end) == 0) {
            items.insertSortedItem(item);
            return;
        }
        computeRootInterval(newNode);
        addNode(newNode);
    }

    public boolean isRepairNode() {
        return items.isRepairNode();
    }

    // STEPH TODO are parents correctly maintained and/or do we need them?
    private void addNode(final NodeInterval newNode) {
        final Item item = newNode.getItems().get(0);
        if (leftChild == null) {
            leftChild = newNode;
            return;
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        do {
            if (curChild.isItemContained(item)) {
                curChild.addNodeInterval(newNode);
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
        } while (curChild != null);

        prevChild.rightSibling = newNode;
    }

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

        newNode.rightSibling = toBeRebalanced.get(toBeRebalanced.size() - 1).rightSibling;
        if (prevRebalanced == null) {
            leftChild = newNode;
        } else {
            prevRebalanced.rightSibling = newNode;
        }

        NodeInterval prev = null;
        for (NodeInterval cur : toBeRebalanced) {
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

    public List<Item> getItems() {
        return items.getItems();
    }

    public boolean containsItem(final UUID targetId) {
        return items.containsItem(targetId);
    }
}
