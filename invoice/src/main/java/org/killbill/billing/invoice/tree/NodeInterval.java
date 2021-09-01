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

import javax.annotation.Nullable;

import org.joda.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;

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

    // Convenient (static) method to insert a node that belongs below parent, right to prevNode (if not null) and before nextNode (if not null)
    private static boolean insertNode(final NodeInterval parentNode, @Nullable final NodeInterval prevNode, @Nullable final NodeInterval nextNode, final NodeInterval newNode, final AddNodeCallback callback) {
        if (!callback.shouldInsertNode(parentNode, (ItemsNodeInterval) newNode)) {
            return false;
        }
        newNode.parent = parentNode;
        if (prevNode == null) {
            parentNode.leftChild = newNode;
        } else {
            prevNode.rightSibling = newNode;
        }
        newNode.rightSibling = nextNode;
        return true;
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

        // We've found a exact node matching that newNode interval
        if (!isRoot() && newNode.getStart().compareTo(start) == 0 && newNode.getEnd().compareTo(end) == 0) {
            return callback.onExistingNode(this, (ItemsNodeInterval) newNode);
        }

        // Initialize (or update) the root interval
        computeRootInterval(newNode);

        // No leftChild, insert as the leftChild
        if (leftChild == null) {
            return insertNode(this, null, null, newNode, callback);
        }

        //
        // Go through children to decide where to insert newNode:
        // All the possibilities are summarized with the few cases below and
        // further recursions for the right part of the newNode's split
        //
        NodeInterval prevChild = null;
        NodeInterval curChild = leftChild;
        while (curChild != null) {
            if (newNode.getStart().compareTo(curChild.getStart()) < 0) {
                if (newNode.getEnd().compareTo(curChild.getStart()) <= 0) {
                    // newNode is strictly prior curChild, insert before
                    //         |------| curChild
                    // |-------|    newNode
                    // |----|     newNode
                    return insertNode(this, prevChild, curChild, newNode, callback);
                } else {
                    // newNode starts prior curChild, but overlaps, split on the curChild#start
                    // Note that it does not matter what happens with newNode#end, and whether it even spans multiple children,
                    // the recursion will take care of it.
                    //         |------| curChild          |-----| (rightChild)
                    // |----------|     newNode
                    // |-----------------------------------------------------------|  newNode
                    final NodeInterval[] newNodes = ((ItemsNodeInterval) newNode).split(curChild.getStart());
                    curChild.getParent().addNode(newNodes[0], callback);
                    return curChild.getParent().addNode(newNodes[1], callback);
                }
            } else if (newNode.getEnd().compareTo(curChild.getEnd()) <= 0) {
                // newNode is contained within curChild, recurse to have curChild become the parent
                //         |------| curChild
                //         |------|   newNode
                //         |----|     newNode
                //           |----|   newNode
                //           |-|     newNode
                return curChild.addNode(newNode, callback);
            } else if (newNode.getStart().compareTo(curChild.getEnd()) < 0) {
                // newNode starts after curChild#start, but overlaps curChild, split on the curChild#end
                // Note that it does not matter what happens with newNode#end, and whether it even spans multiple children,
                // the recursion will take care of it.
                //         |------| curChild           |-----| (rightChild)
                //         |---------|   newNode
                //            |---------|   newNode
                //            |--------------------------------------------------------|   newNode
                final NodeInterval[] newNodes = ((ItemsNodeInterval) newNode).split(curChild.getEnd());
                curChild.getParent().addNode(newNodes[0], callback);
                return curChild.getParent().addNode(newNodes[1], callback);
            } else {
                // Go to next child and try again...
                prevChild = curChild;
                curChild = curChild.rightSibling;
            }
        }
        // We reached to end, this node belongs after the last child, insert
        //         |------| curChild (last one)
        //                |---------|   newNode
        //                     |---------|   newNode
        return insertNode(this, prevChild, null, newNode, callback);
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
    public void walkTree(final WalkCallback callback) {
        Preconditions.checkNotNull(callback);
        walkTreeWithDepth(callback, 0);
    }

    private void walkTreeWithDepth(final WalkCallback callback, final int depth) {

        Preconditions.checkNotNull(callback);
        callback.onCurrentNode(depth, this, parent);

        NodeInterval curChild = leftChild;
        while (curChild != null) {
            curChild.walkTreeWithDepth(callback, (depth + 1));
            curChild = curChild.getRightSibling();
        }
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
        final StringBuilder sb = new StringBuilder();
        sb.append("[")
          .append(start)
          .append(",")
          .append(end)
          .append("]");
        return sb.toString();
    }

    public String toStringVerbose() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append(this);
        if (parent == null) {
            sb.append(", prnt=").append(parent);
        } else {
            sb.append(", prnt=[")
              .append(parent.getStart())
              .append(",")
              .append(parent.getEnd())
              .append("]");
        }
        if (leftChild == null) {
            sb.append(", lCh=").append(leftChild);
        } else {
            sb.append(", lCh=[")
              .append(leftChild.getStart())
              .append(",")
              .append(leftChild.getEnd())
              .append("]");
        }
        if (rightSibling == null) {
            sb.append(", rSib=").append(rightSibling);
        } else {
            sb.append(", rSib=[")
              .append(rightSibling.getStart())
              .append(",")
              .append(rightSibling.getEnd())
              .append("]");
        }
        sb.append('}');
        return sb.toString();
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

        void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent);
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
        void onMissingInterval(NodeInterval curNode, LocalDate startDate, LocalDate endDate);

        /**
         * Called when we hit a node with no children
         *
         * @param curNode current node
         */
        void onLastNode(NodeInterval curNode);
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
        boolean onExistingNode(final NodeInterval existingNode, final ItemsNodeInterval updatedNewNode);

        /**
         * Called prior to insert the new node in the tree
         *
         * @param insertionNode the parent node where this new node would be inserted
         * @return true if addNode should proceed with the insertion and false otherwise
         */
        boolean shouldInsertNode(final NodeInterval insertionNode, final ItemsNodeInterval updatedNewNode);
    }
}
