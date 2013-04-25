/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import com.ning.billing.invoice.model.InvoiceItemList;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.collect.Ordering;

public class CBADao {

    private final InvoiceDaoHelper invoiceDaoHelper;

    public CBADao() {
        this.invoiceDaoHelper = new InvoiceDaoHelper();
    }


    public BigDecimal getAccountCBAFromTransaction(final UUID accountId,
                                                    final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                                    final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        return getAccountCBAFromTransaction(invoices);
    }

    public BigDecimal getAccountCBAFromTransaction(final List<InvoiceModelDao> invoices) {
        BigDecimal cba = BigDecimal.ZERO;
        for (final InvoiceModelDao cur : invoices) {
            final InvoiceItemList invoiceItems = new InvoiceItemList(cur.getInvoiceItems());
            cba = cba.add(invoiceItems.getCBAAmount());
        }
        return cba;
    }

    public void doCBAComplexity(final UUID accountId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {

        List<InvoiceModelDao> invoiceItemModelDaos = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        for (InvoiceModelDao cur : invoiceItemModelDaos) {
            addCBAIfNeeded(entitySqlDaoWrapperFactory, cur, context);
        }
        invoiceItemModelDaos = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(accountId, entitySqlDaoWrapperFactory, context);
        useExistingCBAFromTransaction(invoiceItemModelDaos, entitySqlDaoWrapperFactory, context);
    }

    /**
     * Adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param entitySqlDaoWrapperFactory the EntitySqlDaoWrapperFactory from the current transaction
     * @param invoice                    the invoice to adjust
     * @param context                    the call context
     */
    private void addCBAIfNeeded(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory,
                                final InvoiceModelDao invoice,
                                final InternalCallContext context) throws EntityPersistenceException {

        // If invoice balance becomes negative we add some CBA item
        final BigDecimal balance = InvoiceModelDaoHelper.getBalance(invoice);
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(), balance.negate(), invoice.getCurrency()));
            transInvoiceItemDao.create(cbaAdjItem, context);
        }
    }


    private void useExistingCBAFromTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) throws InvoiceApiException, EntityPersistenceException {

        final BigDecimal accountCBA = getAccountCBAFromTransaction(invoices);
        if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        final List<InvoiceModelDao> unpaidInvoices = invoiceDaoHelper.getUnpaidInvoicesByAccountFromTransaction(invoices, null);
        // We order the same os BillingStateCalculator-- should really share the comparator
        final List<InvoiceModelDao> orderedUnpaidInvoices = Ordering.from(new Comparator<InvoiceModelDao>() {
            @Override
            public int compare(final InvoiceModelDao i1, final InvoiceModelDao i2) {
                return i1.getInvoiceDate().compareTo(i2.getInvoiceDate());
            }
        }).immutableSortedCopy(unpaidInvoices);

        BigDecimal remainingAccountCBA = accountCBA;
        for (InvoiceModelDao cur : orderedUnpaidInvoices) {
            final BigDecimal curInvoiceBalance = InvoiceModelDaoHelper.getBalance(cur);
            final BigDecimal cbaToApplyOnInvoice = remainingAccountCBA.compareTo(curInvoiceBalance) <= 0 ? remainingAccountCBA : curInvoiceBalance;
            remainingAccountCBA = remainingAccountCBA.subtract(cbaToApplyOnInvoice);

            final InvoiceItemModelDao cbaAdjItem = new InvoiceItemModelDao(new CreditBalanceAdjInvoiceItem(cur.getId(), cur.getAccountId(), context.getCreatedDate().toLocalDate(), cbaToApplyOnInvoice.negate(), cur.getCurrency()));

            final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            transInvoiceItemDao.create(cbaAdjItem, context);

            if (remainingAccountCBA.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
    }

}
