/*
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.annotations.VisibleForTesting;

public class TreePrinter {

    public static String print(final NodeInterval root) {
        return print(buildCoordinates(root));
    }

    private static String print(final SortedMap<XY, NodeInterval> tree) {
        // Make left most node start at X=0
        translate(tree);

        final AtomicInteger totalOrdering = new AtomicInteger(64);
        final Map<String, NodeInterval> legend = new LinkedHashMap<String, NodeInterval>();

        final List<StringBuilder> builders = new LinkedList<StringBuilder>();
        for (int level = 0; level >= maxOffset(tree).Y; level--) {
            builders.add(new StringBuilder());
            // Draw edges for that level
            drawLevel(true, level, tree, builders, totalOrdering, legend);

            // Draw nodes for that level
            builders.add(new StringBuilder());
            drawLevel(false, level, tree, builders, totalOrdering, legend);
        }

        final StringBuilder builder = new StringBuilder();
        for (final StringBuilder levelBuilder : builders) {
            builder.append(levelBuilder.toString());
        }

        builder.append("\n");
        for (final Entry<String, NodeInterval> entry : legend.entrySet()) {
            builder.append(entry.getKey()).append(": ");
            appendNodeDetails(entry.getValue(), builder);
            builder.append("\n");
        }
        return builder.toString();
    }

    private static void drawLevel(final boolean drawEdges,
                                  final int level,
                                  final SortedMap<XY, NodeInterval> tree,
                                  final List<StringBuilder> builders,
                                  final AtomicInteger totalOrdering,
                                  final Map<String, NodeInterval> legend) {
        if (drawEdges && level == 0) {
            // Nothing to do for root
            return;
        }

        final StringBuilder builder = builders.get(builders.size() - 1);

        int posX = 0;
        boolean sibling;
        for (final Entry<XY, NodeInterval> entry : tree.entrySet()) {
            final XY levelXY = entry.getKey();
            if (levelXY.Y > level) {
                // Sorted - we haven't reached that level yet
                continue;
            } else if (levelXY.Y < level) {
                // Sorted - we are done for that level
                break;
            }

            sibling = levelXY.parent == null;
            while (posX < levelXY.X) {
                if (drawEdges || !sibling || level == 0) {
                    builder.append(" ");
                } else {
                    // Link siblings
                    builder.append("-");
                }
                posX++;
            }

            if (drawEdges) {
                if (sibling) {
                    builder.append(" ");
                } else {
                    builder.append("/");
                }
            } else {
                if (sibling && level != 0) {
                    builder.append("");
                }

                // Print this node
                String node = Character.toString((char) totalOrdering.incrementAndGet());
                if (sibling && level != 0) {
                    node = node.toLowerCase();
                }
                legend.put(node, entry.getValue());
                builder.append(node);
            }
            posX++;
        }
        builder.append("\n");
    }

    private static void appendNodeDetails(final NodeInterval interval, final StringBuilder builder) {
        builder.append("[")
               .append(interval.getStart())
               .append(",")
               .append(interval.getEnd())
               .append("]");

        if (interval instanceof ItemsNodeInterval) {
            if (((ItemsNodeInterval) interval).getItems().isEmpty()) {
                return;
            }

            builder.append("(");
            final List<Item> items = ((ItemsNodeInterval) interval).getItems();
            for (int i = 0; i < items.size(); i++) {
                final Item item = items.get(i);
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(item.getAction().name().charAt(0));
            }
            builder.append(")");
        }
    }

    @VisibleForTesting
    static SortedMap<XY, NodeInterval> buildCoordinates(final NodeInterval root) {
        final XY reference = new XY(0, 0);

        final SortedMap<XY, NodeInterval> result = new TreeMap<XY, NodeInterval>();
        result.put(reference, root);
        result.putAll(buildCoordinates(root, reference));

        return result;
    }

    private static Map<XY, NodeInterval> buildCoordinates(final NodeInterval root, final XY initialCoords) {
        final Map<XY, NodeInterval> result = new HashMap<XY, NodeInterval>();
        if (root == null) {
            return result;
        }

        // Compute the coordinate of the left most child
        NodeInterval curChild = (NodeInterval) root.getLeftChild();
        if (curChild == null) {
            return result;
        }

        XY curXY = leftChildXY(initialCoords);
        result.put(curXY, curChild);
        // Compute the coordinates of the tree below that child
        result.putAll(buildCoordinates(curChild, curXY));

        curChild = (ItemsNodeInterval) curChild.getRightSibling();
        while (curChild != null) {
            curXY = rightSiblingXY(curXY);

            // Compute the coordinates of the tree below that child
            final Map<XY, NodeInterval> subtree = buildCoordinates(curChild, curXY);
            final XY offset = translate(subtree);
            translate(offset, curXY);
            result.put(curXY, curChild);
            result.putAll(subtree);

            curChild = (NodeInterval) curChild.getRightSibling();
        }

        return result;
    }

    private static XY translate(final Map<XY, NodeInterval> subtree) {
        final XY offset = maxOffset(subtree);
        for (final XY xy : subtree.keySet()) {
            translate(offset, xy);
        }
        return offset;
    }

    private static void translate(final XY offset, final XY xy) {
        xy.X = xy.X - offset.X;
    }

    private static XY maxOffset(final Map<XY, NodeInterval> tree) {
        final XY res = new XY(0, 0);
        for (final XY xy : tree.keySet()) {
            if (xy.X < res.X) {
                res.X = xy.X;
            }
            if (xy.Y < res.Y) {
                res.Y = xy.Y;
            }
        }
        return res;
    }

    private static XY leftChildXY(final XY parent) {
        return new XY(parent.X - 1, parent.Y - 1, parent);
    }

    private static XY rightSiblingXY(final XY leftSibling) {
        return new XY(leftSibling.X + 1, leftSibling.Y);
    }

    static class XY implements Comparable<XY> {

        int X;
        int Y;
        XY parent;

        public XY(final int x, final int y) {
            this(x, y, null);
        }

        public XY(final int x, final int y, final XY parent) {
            X = x;
            Y = y;
            this.parent = parent;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("(");
            sb.append(X);
            sb.append(",").append(Y);
            sb.append(')');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final XY xy = (XY) o;

            if (X != xy.X) {
                return false;
            }
            return Y == xy.Y;

        }

        @Override
        public int hashCode() {
            int result = X;
            result = 31 * result + Y;
            return result;
        }

        @Override
        public int compareTo(final XY o) {
            return Y == o.Y ? X < o.X ? -1 : X == o.X ? 0 : 1 : Y < o.Y ? 1 : -1;
        }
    }
}
