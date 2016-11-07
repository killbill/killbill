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

import java.util.List;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class NodeInterval {

    protected NodeInterval parent;
    protected NodeInterval leftChild;
    protected NodeInterval rightSibling;

    protected LocalDate start;
    protected LocalDate end;

    public NodeInterval() {
        this(null, null, null);
    }

    public NodeInterval(final NodeInterval parent, final LocalDate startDate, final LocalDate endDate) {
        this.start = startDate;
        this.end = endDate;
        this.parent = parent;
        this.leftChild = null;
        this.rightSibling = null;
    }

    /**
     * Build the tree by calling the callback on the last node in the tree or remaining part with no children.
     *
     * @param callback the callback which perform the build logic.
     * @return whether or not the parent NodeInterval should ignore the period covered by the child (NodeInterval)
     */
    public void build(final BuildNodeCallback callback) {

        Preconditions.checkNotNull(callback);

        if (leftChild == null) {
            callback.onLastNode(this);
            return;
        }

        LocalDate curDate = start;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.getStart().compareTo(curDate) > 0) {
                callback.onMissingInterval(this, curDate, curChild.getStart());
            }
            curChild.build(callback);
            // Note that skip to child endDate, meaning that we always consider the child [start end]
            curDate = curChild.getEnd();
            curChild = curChild.getRightSibling();
        }

        // Finally if there is a hole at the end, we build the missing piece from ourselves
        if (curDate.compareTo(end) < 0) {
            callback.onMissingInterval(this, curDate, end);
        }
        return;
    }

    /**
     * Add a new node in the tree.
     *
     * @param newNode  the node to be added
     * @param callback the callback that will allow to specify insertion and return behavior.
     * @return true if node was inserted. Note that this is driven by the callback, this method is generic
     * and specific behavior can be tuned through specific callbacks.
     */
    public boolean addNode(final NodeInterval newNode, final AddNodeCallback callback) {

        Preconditions.checkNotNull(newNode);
        Preconditions.checkNotNull(callback);

        if (!isRoot() && newNode.getStart().compareTo(start) == 0 && newNode.getEnd().compareTo(end) == 0) {
            return callback.onExistingNode(this);
        }

        computeRootInterval(newNode);

        newNode.parent = this;
        if (leftChild == null) {
            if (callback.shouldInsertNode(this)) {
                leftChild = newNode;
                return true;
            } else {
                return false;
            }
        }

        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.isItemContained(newNode)) {
                return curChild.addNode(newNode, callback);
            }

            if (curChild.isItemOverlap(newNode)) {
                if (rebalance(newNode)) {
                    return callback.shouldInsertNode(this);
                }
            }

            if (newNode.getStart().compareTo(curChild.getStart()) < 0) {

                Preconditions.checkState(newNode.getEnd().compareTo(end) <= 0);

                if (callback.shouldInsertNode(this)) {
                    newNode.rightSibling = curChild;
                    if (prevChild == null) {
                        leftChild = newNode;
                    } else {
                        prevChild.rightSibling = newNode;
                    }
                    return true;
                } else {
                    return false;
                }
            }
            prevChild = curChild;
            curChild = curChild.rightSibling;
        }

        if (callback.shouldInsertNode(this)) {
            prevChild.rightSibling = newNode;
            return true;
        } else {
            return false;
        }
    }

    public void removeChild(final NodeInterval toBeRemoved) {
        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.isSame(toBeRemoved)) {
                if (prevChild == null) {
                    if (curChild.getLeftChild() == null) {
                        leftChild = curChild.getRightSibling();
                    } else {
                        leftChild = curChild.getLeftChild();
                        adjustRightMostChildSibling(curChild);
                    }
                } else {
                    if (curChild.getLeftChild() == null) {
                        prevChild.rightSibling = curChild.getRightSibling();
                    } else {
                        prevChild.rightSibling = curChild.getLeftChild();
                        adjustRightMostChildSibling(curChild);
                    }
                }
                break;
            }
            prevChild = curChild;
            curChild = curChild.getRightSibling();
        }
    }

    private void adjustRightMostChildSibling(final NodeInterval curNode) {
        NodeInterval tmpChild = curNode.getLeftChild();
        NodeInterval preTmpChild = null;
        while (tmpChild != null) {
            preTmpChild = tmpChild;
            tmpChild = tmpChild.getRightSibling();
        }
        preTmpChild.rightSibling = curNode.getRightSibling();
    }

    @JsonIgnore
    public boolean isPartitionedByChildren() {

        if (leftChild == null) {
            return false;
        }

        LocalDate curDate = start;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.getStart().compareTo(curDate) > 0) {
                return false;
            }
            curDate = curChild.getEnd();
            curChild = curChild.getRightSibling();
        }
        return (curDate.compareTo(end) == 0);
    }

    /**
     * Return the first node satisfying the date and match callback.
     *
     * @param targetDate target date for possible match nodes whose interval comprises that date
     * @param callback   custom logic to decide if a given node is a match
     * @return the found node or null if there is nothing.
     */
    public NodeInterval findNode(final LocalDate targetDate, final SearchCallback callback) {

        Preconditions.checkNotNull(callback);
        Preconditions.checkNotNull(targetDate);

        if (targetDate.compareTo(getStart()) < 0 || targetDate.compareTo(getEnd()) > 0) {
            return null;
        }

        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (curChild.getStart().compareTo(targetDate) <= 0 && curChild.getEnd().compareTo(targetDate) >= 0) {
                if (callback.isMatch(curChild)) {
                    return curChild;
                }
                NodeInterval result = curChild.findNode(targetDate, callback);
                if (result != null) {
                    return result;
                }
            }
            curChild = curChild.getRightSibling();
        }
        return null;
    }

    /**
     * Return the first node satisfying the date and match callback.
     *
     * @param callback custom logic to decide if a given node is a match
     * @return the found node or null if there is nothing.
     */
    public NodeInterval findNode(final SearchCallback callback) {

        Preconditions.checkNotNull(callback);
        if (callback.isMatch(this)) {
            return this;
        }

        NodeInterval curChild = leftChild;
        while (curChild != null) {
            final NodeInterval result = curChild.findNode(callback);
            if (result != null) {
                return result;
            }
            curChild = curChild.getRightSibling();
        }
        return null;
    }

    /**
     * Walk the tree (depth first search) and invoke callback for each node.
     *
     * @param callback
     */
    public void walkTree(WalkCallback callback) {
        Preconditions.checkNotNull(callback);
        walkTreeWithDepth(callback, 0);
    }

    private void walkTreeWithDepth(WalkCallback callback, int depth) {

        Preconditions.checkNotNull(callback);
        callback.onCurrentNode(depth, this, parent);

        NodeInterval curChild = leftChild;
        while (curChild != null) {
            curChild.walkTreeWithDepth(callback, (depth + 1));
            curChild = curChild.getRightSibling();
        }
    }

    public boolean isItemContained(final NodeInterval newNode) {
        return (newNode.getStart().compareTo(start) >= 0 &&
                newNode.getStart().compareTo(end) <= 0 &&
                newNode.getEnd().compareTo(start) >= 0 &&
                newNode.getEnd().compareTo(end) <= 0);
    }

    public boolean isItemOverlap(final NodeInterval newNode) {
        return ((newNode.getStart().compareTo(start) < 0 &&
                 newNode.getEnd().compareTo(end) >= 0) ||
                (newNode.getStart().compareTo(start) <= 0 &&
                 newNode.getEnd().compareTo(end) > 0));
    }

    @JsonIgnore
    public boolean isSame(final NodeInterval otherNode) {
        return ((otherNode.getStart().compareTo(start) == 0 &&
                 otherNode.getEnd().compareTo(end) == 0) &&
                otherNode.getParent().equals(parent));
    }

    @JsonIgnore
    public boolean isRoot() {
        return parent == null;
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    @JsonIgnore
    public NodeInterval getParent() {
        return parent;
    }

    @JsonIgnore
    public NodeInterval getLeftChild() {
        return leftChild;
    }

    @JsonIgnore
    public NodeInterval getRightSibling() {
        return rightSibling;
    }

    @JsonIgnore
    public int getNbChildren() {
        int result = 0;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            result++;
            curChild = curChild.rightSibling;
        }
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NodeInterval{");
        sb.append("this=[")
          .append(start)
          .append(",")
          .append(end)
          .append("]");
        if (parent == null) {
            sb.append(", parent=").append(parent);
        } else {
            sb.append(", parent=[")
              .append(parent.getStart())
              .append(",")
              .append(parent.getEnd())
              .append("]");
        }
        if (leftChild == null) {
            sb.append(", leftChild=").append(leftChild);
        } else {
            sb.append(", leftChild=[")
              .append(leftChild.getStart())
              .append(",")
              .append(leftChild.getEnd())
              .append("]");
        }
        if (rightSibling == null) {
            sb.append(", rightSibling=").append(rightSibling);
        } else {
            sb.append(", rightSibling=[")
              .append(rightSibling.getStart())
              .append(",")
              .append(rightSibling.getEnd())
              .append("]");
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Since items may be added out of order, there is no guarantee that we don't suddenly have a new node
     * whose interval emcompasses cuurent node(s). In which case we need to rebalance the tree.
     *
     * @param newNode node that triggered a rebalance operation
     */
    private boolean rebalance(final NodeInterval newNode) {

        NodeInterval prevRebalanced = null;
        NodeInterval curChild = leftChild;
        List<NodeInterval> toBeRebalanced = Lists.newLinkedList();
        do {
            if (curChild.isItemOverlap(newNode)) {
                toBeRebalanced.add(curChild);
            } else {
                if (toBeRebalanced.size() > 0) {
                    break;
                }
                prevRebalanced = curChild;
            }
            curChild = curChild.rightSibling;
        } while (curChild != null);

        if (toBeRebalanced.isEmpty()) {
            return false;
        }

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
        return true;
    }

    private void computeRootInterval(final NodeInterval newNode) {
        if (!isRoot()) {
            return;
        }
        this.start = (start == null || start.compareTo(newNode.getStart()) > 0) ? newNode.getStart() : start;
        this.end = (end == null || end.compareTo(newNode.getEnd()) < 0) ? newNode.getEnd() : end;
    }

    /**
     * Provides callback for walking the tree.
     */
    public interface WalkCallback {

        public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent);
    }

    /**
     * Provides custom logic for the search.
     */
    public interface SearchCallback {

        /**
         * Custom logic to decide which node to return.
         *
         * @param curNode found node
         * @return evaluates whether this is the node that should be returned
         */
        boolean isMatch(NodeInterval curNode);
    }

    /**
     * Provides the custom logic for when building resulting state from the tree.
     */
    public interface BuildNodeCallback {

        /**
         * Called when we hit a missing interval where there is no child.
         *
         * @param curNode   current node
         * @param startDate startDate of the new interval to build
         * @param endDate   endDate of the new interval to build
         */
        public void onMissingInterval(NodeInterval curNode, LocalDate startDate, LocalDate endDate);

        /**
         * Called when we hit a node with no children
         *
         * @param curNode current node
         */
        public void onLastNode(NodeInterval curNode);
    }

    /**
     * Provides the custom logic for when adding nodes in the tree.
     */
    public interface AddNodeCallback {

        /**
         * Called when trying to insert a new node in the tree but there is already
         * such a node for that same interval.
         *
         * @param existingNode
         * @return this is the return value for the addNode method
         */
        public boolean onExistingNode(final NodeInterval existingNode);

        /**
         * Called prior to insert the new node in the tree
         *
         * @param insertionNode the parent node where this new node would be inserted
         * @return true if addNode should proceed with the insertion and false otherwise
         */
        public boolean shouldInsertNode(final NodeInterval insertionNode);
    }
}
