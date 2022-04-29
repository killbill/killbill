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

/**
 * Add "Unit" in the end of class name to indicate that this is unit/fast test.
 */
public class TestCBADaoUnit extends InvoiceTestSuiteNoDB {

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
    void testGetInvoiceBalance() {
        final UUID accountId = UUID.randomUUID();
        final BigDecimal doesntMatter = BigDecimal.ZERO; // current invoice item amount calculation mocked by 'childInvoiceAmountCharged'
        InvoiceModelDao parent = createInvoiceWithItems(accountId, null, new BigDecimal("10"), new BigDecimal("20"));
        InvoiceModelDao toTest = createInvoiceWithItems(accountId, parent, doesntMatter);

        CBADao dao = getCBADao(new BigDecimal("100"));
        Assert.assertEquals(dao.getInvoiceBalance(toTest).compareTo(new BigDecimal("70")), 0);

        parent = createInvoiceWithItems(accountId, null,
                                        new BigDecimal("3000"), new BigDecimal("12000"), new BigDecimal("5000"),
                                        new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("17000"),
                                        new BigDecimal("9000"), new BigDecimal("9000"), new BigDecimal("2000"),
                                        new BigDecimal("5000"), new BigDecimal("10000"), new BigDecimal("5000"));
        toTest = createInvoiceWithItems(accountId, parent, doesntMatter);

        dao = getCBADao(new BigDecimal("100000"));
        Assert.assertEquals(dao.getInvoiceBalance(toTest).compareTo(new BigDecimal("20000")), 0);
    }
}
