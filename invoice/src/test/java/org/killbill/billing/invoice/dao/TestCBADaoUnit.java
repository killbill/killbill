/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.killbill.billing.invoice.InvoiceTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCBADaoUnit extends InvoiceTestSuiteNoDB {

    /**
     * Used inside of test {@link CBADao#getInvoiceBalance(InvoiceModelDao)} tests, where the amount of invoice item
     * doesn't matter because it's mocked by {@code childInvoiceAmountCharged}. See {@link #getCBADao(BigDecimal)}.
     */
    private static final BigDecimal DOESNT_MATTER = BigDecimal.ZERO;

    /**
     * @param accountId needed to be the same when testing {@link CBADao#getInvoiceBalance(InvoiceModelDao)}
     * @param parent to calculate
     * @param itemAmounts Will create {@link org.killbill.billing.invoice.api.InvoiceItem} as much as itemAmounts value
     */
    private InvoiceModelDao createInvoiceWithItems(final UUID accountId, final InvoiceModelDao parent, final BigDecimal... itemAmounts) {
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao();
        invoiceModelDao.setAccountId(accountId);
        invoiceModelDao.addParentInvoice(parent);

        final List<InvoiceItemModelDao> items = Arrays.stream(itemAmounts)
                .map(amount -> {
                    final InvoiceItemModelDao item = new InvoiceItemModelDao();
                    item.setAmount(amount);
                    item.setChildAccountId(accountId);
                    return item;
                })
                .collect(Collectors.toUnmodifiableList());

        invoiceModelDao.setInvoiceItems(items);

        return invoiceModelDao;
    }

    private CBADao getCBADao(final BigDecimal childInvoiceAmountCharged) {
        final CBADao dao = new CBADao(super.invoiceDaoHelper);
        final CBADao spied = Mockito.spy(dao);

        Mockito.doReturn(true).when(spied).isParentExistAndRawBalanceIsZero(Mockito.any());
        Mockito.doReturn(childInvoiceAmountCharged).when(spied).getChildInvoiceAmountCharged(Mockito.any());

        return spied;
    }

    @Test(groups = "fast")
    public void testGetInvoiceBalance() {
        final UUID accountId = UUID.randomUUID();
        InvoiceModelDao parent = createInvoiceWithItems(accountId, null, new BigDecimal("10"), new BigDecimal("20"));
        InvoiceModelDao toTest = createInvoiceWithItems(accountId, parent, DOESNT_MATTER);

        CBADao dao = getCBADao(new BigDecimal("100"));
        Assert.assertEquals(dao.getInvoiceBalance(toTest).compareTo(new BigDecimal("70")), 0); // 100 - 30 = 70

        // total items amount here: 80.000
        parent = createInvoiceWithItems(accountId, null,
                                        new BigDecimal("3000"), new BigDecimal("12000"), new BigDecimal("5000"),
                                        new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("17000"),
                                        new BigDecimal("9000"), new BigDecimal("9000"), new BigDecimal("2000"),
                                        new BigDecimal("5000"), new BigDecimal("10000"), new BigDecimal("5000"));
        toTest = createInvoiceWithItems(accountId, parent, DOESNT_MATTER);

        dao = getCBADao(new BigDecimal("100000"));
        Assert.assertEquals(dao.getInvoiceBalance(toTest).compareTo(new BigDecimal("20000")), 0);
    }

    @Test(groups = "fast")
    public void testGetInvoiceBalanceWithDifferentChildAccountId() {
        final UUID accountId = UUID.randomUUID();
        final UUID differentChildAccountId = UUID.randomUUID();

        // total items amount here: 600 ....
        final InvoiceModelDao parent = createInvoiceWithItems(accountId, null,
                                                              new BigDecimal("30"), new BigDecimal("120"), new BigDecimal("50"),
                                                              new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("170"),
                                                              new BigDecimal("90"), new BigDecimal("90"), new BigDecimal("20"));
        // .... But some of item's childAccountId modified to test that we only calculate
        // invoice parent's item.childAccountId that equals with invoice.accountId . (See CBADao around line 103)
        // So actual total item amount = 600 - 40 = 560. (40 comes from the fact that we have 2 item with amount=20)
        parent.getInvoiceItems().stream()
              .filter(item -> item.getAmount().compareTo(new BigDecimal("20")) == 0)
              .forEach(item -> item.setChildAccountId(differentChildAccountId));

        final InvoiceModelDao toTest = createInvoiceWithItems(accountId, parent, DOESNT_MATTER);

        final CBADao dao = getCBADao(new BigDecimal("1000"));
        final BigDecimal actualBalance = dao.getInvoiceBalance(toTest);
        System.out.println("actualBalance = " + actualBalance);
        Assert.assertEquals(actualBalance.compareTo(new BigDecimal("440")), 0);
    }
}
