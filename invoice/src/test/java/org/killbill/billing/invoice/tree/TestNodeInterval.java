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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.tree.NodeInterval.AddNodeCallback;
import org.killbill.billing.invoice.tree.NodeInterval.BuildNodeCallback;
import org.killbill.billing.invoice.tree.NodeInterval.WalkCallback;
import org.testng.annotations.Test;

import com.google.common.base.Preconditions;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestNodeInterval extends InvoiceTestSuiteNoDB {

    private final AddNodeCallback CALLBACK = new DummyAddNodeCallback();

    public static class DummyNodeInterval extends ItemsNodeInterval {

        private final UUID id;

        public DummyNodeInterval() {
            this.id = UUID.randomUUID();
        }

        public DummyNodeInterval(final NodeInterval parent, final LocalDate startDate, final LocalDate endDate) {
            super(parent, startDate, endDate);
            this.id = UUID.randomUUID();
        }

        public UUID getId() {
            return id;
        }

        @Override
        public ItemsNodeInterval[] split(final LocalDate splitDate) {
            Preconditions.checkState(splitDate.compareTo(start) > 0 && splitDate.compareTo(end) < 0,
                                     String.format("Unexpected item split with startDate='%s' and endDate='%s'", start, end));
            Preconditions.checkState(leftChild == null);
            Preconditions.checkState(rightSibling == null);

            final DummyNodeInterval split1 = new DummyNodeInterval(this.parent, this.start, splitDate);
            final DummyNodeInterval split2 = new DummyNodeInterval(this.parent, splitDate, this.end);
            final DummyNodeInterval[] result = new DummyNodeInterval[2];
            result[0] = split1;
            result[1] = split2;
            return result;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("DummyNodeInterval{");
            sb.append("start=").append(start);
            sb.append(", end=").append(end);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DummyNodeInterval)) {
                return false;
            }

            final DummyNodeInterval that = (DummyNodeInterval) o;

            return id != null ? id.equals(that.id) : that.id == null;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    public static class DummyAddNodeCallback implements AddNodeCallback {

        @Override
        public boolean onExistingNode(final NodeInterval existingNode, final ItemsNodeInterval updatedNewNode) {
            return false;
        }

        @Override
        public boolean shouldInsertNode(final NodeInterval insertionNode, final ItemsNodeInterval updatedNewNode) {
            return true;
        }
    }

    @Test(groups = "fast")
    public void testAddExistingItemSimple() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);
        root.addNode(thirdChildLevel1, CALLBACK);

        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-5");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 2, top, firstChildLevel2, secondChildLevel1);
        checkNode(secondChildLevel1, 0, top, null, thirdChildLevel1);
        checkNode(thirdChildLevel1, 1, top, thirdChildLevel2, null);

        checkNode(firstChildLevel2, 0, firstChildLevel1, null, secondChildLevel2);
        checkNode(secondChildLevel2, 0, firstChildLevel1, null, null);
        checkNode(thirdChildLevel2, 0, thirdChildLevel1, null, null);
    }


    @Test(groups = "fast")
    public void testAddOverlapNode1() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-03", "2014-01-07");
        root.addNode(secondChildLevel1, CALLBACK);

        final DummyNodeInterval newNode = createNodeInterval("2014-01-01", "2014-01-04");
        root.addNode(newNode, CALLBACK);

        // Newly created node "split" from newNode
        final DummyNodeInterval firstChildLevel1 = (DummyNodeInterval) top.getLeftChild();
        assertEquals(firstChildLevel1.getStart(), new LocalDate("2014-01-01"));
        assertEquals(firstChildLevel1.getEnd(), new LocalDate("2014-01-03"));

        // Newly created node "split" from newNode
        final DummyNodeInterval firstChildLevel2 = (DummyNodeInterval) secondChildLevel1.getLeftChild();
        assertEquals(firstChildLevel2.getStart(), new LocalDate("2014-01-03"));
        assertEquals(firstChildLevel2.getEnd(), new LocalDate("2014-01-04"));

        checkNode(top, 2, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 0, top, null, secondChildLevel1);
        checkNode(secondChildLevel1, 1, top, firstChildLevel2, null);

        checkNode(firstChildLevel2, 0, secondChildLevel1, null, null);
    }

    @Test(groups = "fast")
    public void testAddOverlapNode2() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        root.addNode(firstChildLevel1, CALLBACK);
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-12", "2014-01-15");
        root.addNode(thirdChildLevel1, CALLBACK);

        final DummyNodeInterval newNode = createNodeInterval("2014-01-07", "2014-01-13");
        root.addNode(newNode, CALLBACK);

        // Newly created node "split" from newNode
        final DummyNodeInterval secondChildLevel1 = (DummyNodeInterval) firstChildLevel1.getRightSibling();
        assertEquals(secondChildLevel1.getStart(), new LocalDate("2014-01-07"));
        assertEquals(secondChildLevel1.getEnd(), new LocalDate("2014-01-12"));

        // Newly created node "split" from newNode
        final DummyNodeInterval firstChildLevel2 = (DummyNodeInterval) thirdChildLevel1.getLeftChild();
        assertEquals(firstChildLevel2.getStart(), new LocalDate("2014-01-12"));
        assertEquals(firstChildLevel2.getEnd(), new LocalDate("2014-01-13"));

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 0, top, null, secondChildLevel1);
        checkNode(secondChildLevel1, 0, top, null, thirdChildLevel1);
        checkNode(thirdChildLevel1, 1, top, firstChildLevel2, null);

        checkNode(firstChildLevel2, 0, thirdChildLevel1, null, null);
    }

    @Test(groups = "fast")
    public void testAddOverlapNode3() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        root.addNode(firstChildLevel1, CALLBACK);
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-12", "2014-01-15");
        root.addNode(thirdChildLevel1, CALLBACK);

        final DummyNodeInterval newNode = createNodeInterval("2014-01-06", "2014-01-12");
        root.addNode(newNode, CALLBACK);

        // Newly created node "split" from newNode
        final DummyNodeInterval firstChildLevel2 = (DummyNodeInterval) firstChildLevel1.getLeftChild();
        assertEquals(firstChildLevel2.getStart(), new LocalDate("2014-01-06"));
        assertEquals(firstChildLevel2.getEnd(), new LocalDate("2014-01-07"));

        // Newly created node "split" from newNode
        final DummyNodeInterval secondChildLevel1 = (DummyNodeInterval) firstChildLevel1.getRightSibling();
        assertEquals(secondChildLevel1.getStart(), new LocalDate("2014-01-07"));
        assertEquals(secondChildLevel1.getEnd(), new LocalDate("2014-01-12"));

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 1, top, firstChildLevel2, secondChildLevel1);
        checkNode(secondChildLevel1, 0, top, null, thirdChildLevel1);
        checkNode(thirdChildLevel1, 0, top, null, null);

        checkNode(firstChildLevel2, 0, firstChildLevel1, null, null);
    }

    @Test(groups = "fast")
    public void testAddOverlapNode4() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        root.addNode(firstChildLevel1, CALLBACK);
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-12", "2014-01-15");
        root.addNode(secondChildLevel1, CALLBACK);

        final DummyNodeInterval newNode = createNodeInterval("2014-01-14", "2014-01-18");
        root.addNode(newNode, CALLBACK);

        // Newly created node "split" from newNode
        final DummyNodeInterval firstChildLevel2 = (DummyNodeInterval) secondChildLevel1.getLeftChild();
        assertEquals(firstChildLevel2.getStart(), new LocalDate("2014-01-14"));
        assertEquals(firstChildLevel2.getEnd(), new LocalDate("2014-01-15"));

        // Newly created node "split" from newNode
        final DummyNodeInterval thirdChildLevel1 = (DummyNodeInterval) secondChildLevel1.getRightSibling();
        assertEquals(thirdChildLevel1.getStart(), new LocalDate("2014-01-15"));
        assertEquals(thirdChildLevel1.getEnd(), new LocalDate("2014-01-18"));

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 0, top, null, secondChildLevel1);
        checkNode(secondChildLevel1, 1, top, firstChildLevel2, thirdChildLevel1);
        checkNode(thirdChildLevel1, 0, top, null, null);

        checkNode(firstChildLevel2, 0, secondChildLevel1, null, null);
    }

    @Test(groups = "fast")
    public void testBuild() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);
        root.addNode(thirdChildLevel1, CALLBACK);

        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-5");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        final List<NodeInterval> output = new LinkedList<NodeInterval>();

        // Just build the missing pieces.
        root.build(new BuildNodeCallback() {
            @Override
            public void onMissingInterval(final NodeInterval curNode, final LocalDate startDate, final LocalDate endDate) {
                output.add(createNodeInterval(startDate, endDate));
            }

            @Override
            public void onLastNode(final NodeInterval curNode) {
                // Nothing
                return;
            }
        });

        final List<NodeInterval> expected = new LinkedList<NodeInterval>();
        expected.add(createNodeInterval("2014-01-05", "2014-01-07"));
        expected.add(createNodeInterval("2014-01-07", "2014-01-08"));
        expected.add(createNodeInterval("2014-01-15", "2014-01-16"));
        expected.add(createNodeInterval("2014-01-17", "2014-02-01"));

        assertEquals(output.size(), expected.size());
        checkInterval(output.get(0), expected.get(0));
        checkInterval(output.get(1), expected.get(1));
    }

    @Test(groups = "fast")
    public void testWalkTree() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval firstChildLevel0 = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(firstChildLevel0, CALLBACK);

        final DummyNodeInterval secondChildLevel0 = createNodeInterval("2014-02-01", "2014-03-01");
        root.addNode(secondChildLevel0, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);
        root.addNode(thirdChildLevel1, CALLBACK);

        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-05");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        final DummyNodeInterval firstChildLevel3 = createNodeInterval("2014-01-01", "2014-01-02");
        final DummyNodeInterval secondChildLevel3 = createNodeInterval("2014-01-03", "2014-01-04");
        root.addNode(firstChildLevel3, CALLBACK);
        root.addNode(secondChildLevel3, CALLBACK);

        final List<NodeInterval> expected = new LinkedList<NodeInterval>();
        expected.add(root);
        expected.add(firstChildLevel0);
        expected.add(firstChildLevel1);
        expected.add(firstChildLevel2);
        expected.add(firstChildLevel3);
        expected.add(secondChildLevel2);
        expected.add(secondChildLevel3);
        expected.add(secondChildLevel1);
        expected.add(thirdChildLevel1);
        expected.add(thirdChildLevel2);
        expected.add(secondChildLevel0);

        final List<NodeInterval> result = new LinkedList<NodeInterval>();
        root.walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                result.add(curNode);
            }
        });

        assertEquals(result.size(), expected.size());
        for (int i = 0; i < result.size(); i++) {
            if (i == 0) {
                assertTrue(result.get(0).isRoot());
                checkInterval(result.get(0), createNodeInterval("2014-01-01", "2014-03-01"));
            } else {
                checkInterval(result.get(i), expected.get(i));
            }
        }
    }


    private void checkInterval(final NodeInterval real, final NodeInterval expected) {
        assertEquals(real.getStart(), expected.getStart());
        assertEquals(real.getEnd(), expected.getEnd());
    }

    private void checkNode(final NodeInterval node, final int expectedChildren, final DummyNodeInterval expectedParent, final DummyNodeInterval expectedLeftChild, final DummyNodeInterval expectedRightSibling) {
        assertEquals(node.getNbChildren(), expectedChildren);
        assertEquals(node.getParent(), expectedParent);
        assertEquals(node.getRightSibling(), expectedRightSibling);
        assertEquals(node.getLeftChild(), expectedLeftChild);
        assertEquals(node.getLeftChild(), expectedLeftChild);
    }

    private DummyNodeInterval createNodeInterval(final LocalDate startDate, final LocalDate endDate) {
        return new DummyNodeInterval(null, startDate, endDate);
    }

    private DummyNodeInterval createNodeInterval(final String startDate, final String endDate) {
        return createNodeInterval(new LocalDate(startDate), new LocalDate(endDate));
    }
}
