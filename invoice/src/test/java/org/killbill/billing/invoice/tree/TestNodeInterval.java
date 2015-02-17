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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.tree.NodeInterval.AddNodeCallback;
import org.killbill.billing.invoice.tree.NodeInterval.BuildNodeCallback;
import org.killbill.billing.invoice.tree.NodeInterval.SearchCallback;
import org.killbill.billing.invoice.tree.NodeInterval.WalkCallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestNodeInterval /* extends InvoiceTestSuiteNoDB  */ {

    private AddNodeCallback CALLBACK = new DummyAddNodeCallback();

    public class DummyNodeInterval extends NodeInterval {

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
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DummyNodeInterval)) {
                return false;
            }

            final DummyNodeInterval that = (DummyNodeInterval) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    public class DummyAddNodeCallback implements AddNodeCallback {

        @Override
        public boolean onExistingNode(final NodeInterval existingNode) {
            return false;
        }

        @Override
        public boolean shouldInsertNode(final NodeInterval insertionNode) {
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
    public void testAddExistingItemWithRebalance() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-5");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final DummyNodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);
        root.addNode(thirdChildLevel1, CALLBACK);

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 2, top, firstChildLevel2, secondChildLevel1);
        checkNode(secondChildLevel1, 0, top, null, thirdChildLevel1);
        checkNode(thirdChildLevel1, 1, top, thirdChildLevel2, null);

        checkNode(firstChildLevel2, 0, firstChildLevel1, null, secondChildLevel2);
        checkNode(secondChildLevel2, 0, firstChildLevel1, null, null);
        checkNode(thirdChildLevel2, 0, thirdChildLevel1, null, null);
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
    public void testSearch() {
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

        final DummyNodeInterval firstChildLevel3 = createNodeInterval("2014-01-01", "2014-01-02");
        final DummyNodeInterval secondChildLevel3 = createNodeInterval("2014-01-03", "2014-01-04");
        root.addNode(firstChildLevel3, CALLBACK);
        root.addNode(secondChildLevel3, CALLBACK);

        final NodeInterval search1 = root.findNode(new LocalDate("2014-01-04"), new SearchCallback() {
            @Override
            public boolean isMatch(final NodeInterval curNode) {
                return ((DummyNodeInterval) curNode).getId().equals(secondChildLevel3.getId());
            }
        });
        checkInterval(search1, secondChildLevel3);

        final NodeInterval search2 = root.findNode(new SearchCallback() {
            @Override
            public boolean isMatch(final NodeInterval curNode) {
                return ((DummyNodeInterval) curNode).getId().equals(thirdChildLevel2.getId());
            }
        });
        checkInterval(search2, thirdChildLevel2);

        final NodeInterval nullSearch = root.findNode(new SearchCallback() {
            @Override
            public boolean isMatch(final NodeInterval curNode) {
                return ((DummyNodeInterval) curNode).getId().equals("foo");
            }
        });
        assertNull(nullSearch);
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

    @Test(groups = "fast")
    public void testRemoveLeftChildWithGrandChildren() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-20");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-21", "2014-01-31");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);


        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-04", "2014-01-10");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-11", "2014-01-20");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        // Let's verify we get it right prior removing the node
        final List<NodeInterval> expectedNodes = new ArrayList<NodeInterval>();
        expectedNodes.add(root);
        expectedNodes.add(top);
        expectedNodes.add(firstChildLevel1);
        expectedNodes.add(firstChildLevel2);
        expectedNodes.add(secondChildLevel2);
        expectedNodes.add(thirdChildLevel2);
        expectedNodes.add(secondChildLevel1);

        root.walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                Assert.assertEquals(curNode, expectedNodes.remove(0));
            }
        });

        // Remove node and verify again
        top.removeChild(firstChildLevel1);

        final List<NodeInterval> expectedNodesAfterRemoval = new ArrayList<NodeInterval>();
        expectedNodesAfterRemoval.add(root);
        expectedNodesAfterRemoval.add(top);
        expectedNodesAfterRemoval.add(firstChildLevel2);
        expectedNodesAfterRemoval.add(secondChildLevel2);
        expectedNodesAfterRemoval.add(thirdChildLevel2);
        expectedNodesAfterRemoval.add(secondChildLevel1);

        root.walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                Assert.assertEquals(curNode, expectedNodesAfterRemoval.remove(0));
            }
        });
    }

    @Test(groups = "fast")
    public void testRemoveMiddleChildWithGrandChildren() {
        final DummyNodeInterval root = new DummyNodeInterval();

        final DummyNodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addNode(top, CALLBACK);

        final DummyNodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-20");
        final DummyNodeInterval secondChildLevel1 = createNodeInterval("2014-01-21", "2014-01-31");
        root.addNode(firstChildLevel1, CALLBACK);
        root.addNode(secondChildLevel1, CALLBACK);


        final DummyNodeInterval firstChildLevel2 = createNodeInterval("2014-01-21", "2014-01-23");
        final DummyNodeInterval secondChildLevel2 = createNodeInterval("2014-01-24", "2014-01-25");
        final DummyNodeInterval thirdChildLevel2 = createNodeInterval("2014-01-26", "2014-01-31");
        root.addNode(firstChildLevel2, CALLBACK);
        root.addNode(secondChildLevel2, CALLBACK);
        root.addNode(thirdChildLevel2, CALLBACK);

        // Original List without removing node:
        final List<NodeInterval> expectedNodes = new ArrayList<NodeInterval>();
        expectedNodes.add(root);
        expectedNodes.add(top);
        expectedNodes.add(firstChildLevel1);
        expectedNodes.add(secondChildLevel1);
        expectedNodes.add(firstChildLevel2);
        expectedNodes.add(secondChildLevel2);
        expectedNodes.add(thirdChildLevel2);

        root.walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                Assert.assertEquals(curNode, expectedNodes.remove(0));
            }
        });

        top.removeChild(secondChildLevel1);

        final List<NodeInterval> expectedNodesAfterRemoval = new ArrayList<NodeInterval>();
        expectedNodesAfterRemoval.add(root);
        expectedNodesAfterRemoval.add(top);
        expectedNodesAfterRemoval.add(firstChildLevel1);
        expectedNodesAfterRemoval.add(firstChildLevel2);
        expectedNodesAfterRemoval.add(secondChildLevel2);
        expectedNodesAfterRemoval.add(thirdChildLevel2);

        root.walkTree(new WalkCallback() {
            @Override
            public void onCurrentNode(final int depth, final NodeInterval curNode, final NodeInterval parent) {
                Assert.assertEquals(curNode, expectedNodesAfterRemoval.remove(0));
            }
        });

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
