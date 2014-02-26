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
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.model.RecurringInvoiceItem;
import com.ning.billing.invoice.tree.Item.ItemAction;

import static org.testng.Assert.assertEquals;

public class TestNodeInterval /* extends InvoiceTestSuiteNoDB  */ {

    private final UUID invoiceId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID bundleId = UUID.randomUUID();
    private final String planName = "my-plan";
    private final String phaseName = "my-phase";
    private final Currency currency = Currency.USD;

    @Test(groups = "fast")
    public void testAddExistingItemSimple() {
        final NodeInterval root = new NodeInterval();

        final NodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addExistingItem(top);

        final NodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final NodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final NodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addExistingItem(firstChildLevel1);
        root.addExistingItem(secondChildLevel1);
        root.addExistingItem(thirdChildLevel1);

        final NodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final NodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-5");
        final NodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addExistingItem(firstChildLevel2);
        root.addExistingItem(secondChildLevel2);
        root.addExistingItem(thirdChildLevel2);

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
        final NodeInterval root = new NodeInterval();

        final NodeInterval top = createNodeInterval("2014-01-01", "2014-02-01");
        root.addExistingItem(top);

        final NodeInterval firstChildLevel2 = createNodeInterval("2014-01-01", "2014-01-03");
        final NodeInterval secondChildLevel2 = createNodeInterval("2014-01-03", "2014-01-5");
        final NodeInterval thirdChildLevel2 = createNodeInterval("2014-01-16", "2014-01-17");
        root.addExistingItem(firstChildLevel2);
        root.addExistingItem(secondChildLevel2);
        root.addExistingItem(thirdChildLevel2);

        final NodeInterval firstChildLevel1 = createNodeInterval("2014-01-01", "2014-01-07");
        final NodeInterval secondChildLevel1 = createNodeInterval("2014-01-08", "2014-01-15");
        final NodeInterval thirdChildLevel1 = createNodeInterval("2014-01-16", "2014-02-01");
        root.addExistingItem(firstChildLevel1);
        root.addExistingItem(secondChildLevel1);
        root.addExistingItem(thirdChildLevel1);

        checkNode(top, 3, root, firstChildLevel1, null);
        checkNode(firstChildLevel1, 2, top, firstChildLevel2, secondChildLevel1);
        checkNode(secondChildLevel1, 0, top, null, thirdChildLevel1);
        checkNode(thirdChildLevel1, 1, top, thirdChildLevel2, null);

        checkNode(firstChildLevel2, 0, firstChildLevel1, null, secondChildLevel2);
        checkNode(secondChildLevel2, 0, firstChildLevel1, null, null);
        checkNode(thirdChildLevel2, 0, thirdChildLevel1, null, null);
    }



    private void checkNode(final NodeInterval node, final int expectedChildren, final NodeInterval expectedParent, final NodeInterval expectedLeftChild, final NodeInterval expectedRightSibling) {
        assertEquals(node.getNbChildren(), expectedChildren);
        assertEquals(node.getParent(), expectedParent);
        assertEquals(node.getRightSibling(), expectedRightSibling);
        assertEquals(node.getLeftChild(), expectedLeftChild);
        assertEquals(node.getLeftChild(), expectedLeftChild);
    }

    private NodeInterval createNodeInterval(final String startDate, final String endDate) {
        return new NodeInterval(null, createItem(startDate, endDate));
    }

    private Item createItem(final String startDate, final String endDate) {
        final BigDecimal amount = BigDecimal.TEN;
        final BigDecimal rate = BigDecimal.TEN;
        final InvoiceItem invoiceItem = new RecurringInvoiceItem(invoiceId, accountId, bundleId, subscriptionId, planName, phaseName, new LocalDate(startDate), new LocalDate(endDate), amount, rate, currency);
        Item item = new Item(invoiceItem, ItemAction.ADD);
        return item;
    }
}
