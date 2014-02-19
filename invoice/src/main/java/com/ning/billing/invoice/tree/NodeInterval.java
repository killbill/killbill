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

import java.util.List;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.InvoiceItem;

import com.google.common.collect.Lists;

public class NodeInterval {

    private LocalDate start;
    private LocalDate end;
    private ItemsInterval items;

    private NodeInterval parent;
    private NodeInterval leftChild;
    private NodeInterval rightSibling;

    public NodeInterval() {
        this.items = new ItemsInterval();
    }

    public NodeInterval(final NodeInterval parent, final InvoiceItem item) {
        this.start = item.getStartDate();
        this.end = item.getEndDate();
        this.items = new ItemsInterval(item);
        this.parent = parent;
        this.leftChild = null;
        this.rightSibling = null;
    }

    public void build(final List<InvoiceItem> output) {

        // There is no sub-interval, just add our own items.
        if (leftChild == null) {
            items.buildFromItems(output);
            return;
        }

        LocalDate curDate = start;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.getStart().compareTo(curDate) > 0) {
               items.buildForNonRepairedItems(curDate, curChild.getStart(), output);
            }
            curChild.build(output);
            curDate = curChild.getEnd();
            curChild = curChild.getRightSibling();
        }
        if (curDate.compareTo(end) < 0) {
            items.buildForNonRepairedItems(curDate, end, output);
        }
    }

    public boolean isItemContained(final InvoiceItem item) {
        return (item.getStartDate().compareTo(start) >= 0 &&
                item.getStartDate().compareTo(end) <= 0 &&
                item.getEndDate().compareTo(start) >= 0 &&
                item.getEndDate().compareTo(end) <= 0);
    }

    public boolean isItemOverlap(final InvoiceItem item) {
        return ((item.getStartDate().compareTo(start) < 0 &&
                 item.getEndDate().compareTo(end) >= 0) ||
                (item.getStartDate().compareTo(start) <= 0 &&
                 item.getEndDate().compareTo(end) > 0));
    }

    public void addItem(final InvoiceItem item) {

        if (!isRoot() && item.getStartDate().compareTo(start) == 0 && item.getEndDate().compareTo(end) == 0) {
            items.insertSortedItem(item);
            return;
        }

        final NodeInterval newNode = new NodeInterval(this, item);
        computeRootInterval(newNode);
        addNode(newNode);
    }

    private void addNode(final NodeInterval newNode) {
        final InvoiceItem item = newNode.getItems().get(0);
        if (leftChild == null) {
            leftChild = newNode;
            return;
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        do {
            if (curChild.isItemContained(item)) {
                curChild.addItem(item);
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

        final InvoiceItem invoiceItem = newNode.getItems().get(0);

        NodeInterval prevRebalanced = null;
        NodeInterval curChild = leftChild;
        List<NodeInterval> toBeRebalanced = Lists.newLinkedList();
        do {
            if (curChild.isItemOverlap(invoiceItem)) {
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

    public List<InvoiceItem> getItems() {
        return items.getItems();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeInterval)) {
            return false;
        }

        final NodeInterval that = (NodeInterval) o;

        if (end != null ? !end.equals(that.end) : that.end != null) {
            return false;
        }
        if (items != null ? !items.equals(that.items) : that.items != null) {
            return false;
        }
        if (leftChild != null ? !leftChild.equals(that.leftChild) : that.leftChild != null) {
            return false;
        }
        if (parent != null ? !parent.equals(that.parent) : that.parent != null) {
            return false;
        }
        if (rightSibling != null ? !rightSibling.equals(that.rightSibling) : that.rightSibling != null) {
            return false;
        }
        if (start != null ? !start.equals(that.start) : that.start != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = start != null ? start.hashCode() : 0;
        result = 31 * result + (end != null ? end.hashCode() : 0);
        result = 31 * result + (items != null ? items.hashCode() : 0);
        result = 31 * result + (parent != null ? parent.hashCode() : 0);
        result = 31 * result + (leftChild != null ? leftChild.hashCode() : 0);
        result = 31 * result + (rightSibling != null ? rightSibling.hashCode() : 0);
        return result;
    }
}
