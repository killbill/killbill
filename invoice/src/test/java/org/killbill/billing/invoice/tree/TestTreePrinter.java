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

import java.math.BigDecimal;
import java.util.Map;
import java.util.SortedMap;

import org.joda.time.LocalDate;
import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.tree.Item.ItemAction;
import org.killbill.billing.invoice.tree.TreePrinter.XY;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestTreePrinter extends InvoiceTestSuiteNoDB {

    private ItemsNodeInterval root;
    private ItemsNodeInterval node11;
    private ItemsNodeInterval node21;
    private ItemsNodeInterval node22;
    private ItemsNodeInterval node12;
    private ItemsNodeInterval node23;
    private ItemsNodeInterval node31;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final InvoiceItem item = Mockito.mock(InvoiceItem.class);
        Mockito.when(item.getAmount()).thenReturn(BigDecimal.ZERO);

        root = new ItemsNodeInterval(null, new Item(item, new LocalDate(2016, 1, 1), new LocalDate(2016, 2, 1), null, ItemAction.ADD));

        node11 = new ItemsNodeInterval(root, new Item(item, new LocalDate(2016, 1, 10), new LocalDate(2016, 1, 15), null, ItemAction.ADD));
        node21 = new ItemsNodeInterval(node11, new Item(item, new LocalDate(2016, 1, 10), new LocalDate(2016, 1, 12), null, ItemAction.ADD));
        node22 = new ItemsNodeInterval(node11, new Item(item, new LocalDate(2016, 1, 14), new LocalDate(2016, 1, 15), null, ItemAction.ADD));

        node12 = new ItemsNodeInterval(root, new Item(item, new LocalDate(2016, 1, 20), new LocalDate(2016, 1, 25), null, ItemAction.ADD));
        node23 = new ItemsNodeInterval(node12, new Item(item, new LocalDate(2016, 1, 22), new LocalDate(2016, 1, 24), null, ItemAction.ADD));
        node31 = new ItemsNodeInterval(node23, new Item(item, new LocalDate(2016, 1, 22), new LocalDate(2016, 1, 23), null, ItemAction.ADD));
    }

    @Test(groups = "fast")
    public void testSimpleTranslate() throws Exception {
        root.leftChild = node11;
        node11.rightSibling = node12;
        node12.leftChild = node23;

        final SortedMap<XY, NodeInterval> coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 4);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(1, -1)), node12);
        Assert.assertEquals(coords.get(new XY(0, -2)), node23);
        //System.out.println(TreePrinter.print(root));
    }

    @Test(groups = "fast")
    public void testComplexMultiLevelTree() throws Exception {
        Map<XY, NodeInterval> coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 1);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);

        root.leftChild = node11;

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 2);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);

        node11.rightSibling = node12;

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 3);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(0, -1)), node12);

        node11.leftChild = node21;

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 4);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(0, -1)), node12);
        Assert.assertEquals(coords.get(new XY(-2, -2)), node21);

        node21.rightSibling = node22;

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 5);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(0, -1)), node12);
        Assert.assertEquals(coords.get(new XY(-2, -2)), node21);
        Assert.assertEquals(coords.get(new XY(-1, -2)), node22);

        node12.leftChild = node23;
        //System.out.println(TreePrinter.print(root));

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 6);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(1, -1)), node12); // (0,-1) before translation
        Assert.assertEquals(coords.get(new XY(-2, -2)), node21);
        Assert.assertEquals(coords.get(new XY(-1, -2)), node22);
        Assert.assertEquals(coords.get(new XY(0, -2)), node23); // (-1,-2) before translation

        node23.leftChild = node31;
        //System.out.println(TreePrinter.print(root));

        coords = TreePrinter.buildCoordinates(root);
        Assert.assertEquals(coords.size(), 7);
        Assert.assertEquals(coords.get(new XY(0, 0)), root);
        Assert.assertEquals(coords.get(new XY(-1, -1)), node11);
        Assert.assertEquals(coords.get(new XY(2, -1)), node12); // (1,-1) before translation
        Assert.assertEquals(coords.get(new XY(-2, -2)), node21);
        Assert.assertEquals(coords.get(new XY(-1, -2)), node22);
        Assert.assertEquals(coords.get(new XY(1, -2)), node23); // (0,-2) before translation
        Assert.assertEquals(coords.get(new XY(0, -3)), node31); // (-1,-3) before translation
    }
}
