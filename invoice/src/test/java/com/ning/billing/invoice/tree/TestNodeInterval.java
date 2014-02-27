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
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.tree.Item.ItemAction;
import com.ning.billing.invoice.tree.NodeInterval.AddNodeCallback;

import static org.testng.Assert.assertEquals;

public class TestNodeInterval /* extends InvoiceTestSuiteNoDB  */ {


    private AddNodeCallback CALLBACK = new DummyAddNodeCallback();

    public class DummyNodeInterval extends NodeInterval {

        public DummyNodeInterval() {
        }

        public DummyNodeInterval(final NodeInterval parent, final LocalDate startDate, final LocalDate endDate) {
            super(parent, startDate, endDate);
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



    private void checkNode(final NodeInterval node, final int expectedChildren, final DummyNodeInterval expectedParent, final DummyNodeInterval expectedLeftChild, final DummyNodeInterval expectedRightSibling) {
        assertEquals(node.getNbChildren(), expectedChildren);
        assertEquals(node.getParent(), expectedParent);
        assertEquals(node.getRightSibling(), expectedRightSibling);
        assertEquals(node.getLeftChild(), expectedLeftChild);
        assertEquals(node.getLeftChild(), expectedLeftChild);
    }

    private DummyNodeInterval createNodeInterval(final String startDate, final String endDate) {
        return new DummyNodeInterval(null, new LocalDate(startDate), new LocalDate(endDate));
    }

}
