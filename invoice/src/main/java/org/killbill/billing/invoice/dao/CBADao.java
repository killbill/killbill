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

package org.killbill.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class CBADao {

    private final InvoiceDaoHelper invoiceDaoHelper;

    @Inject
    public CBADao(final InvoiceDaoHelper invoiceDaoHelper) {
        this.invoiceDaoHelper = invoiceDaoHelper;
    }


    public BigDecimal getAccountCBAFromTransaction(final UUID accountId,
                                                    final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                    final InternalTenantContext context) {
        final List<InvoiceModelDao> invoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        return getAccountCBAFromTransaction(invoices);
    }

    public BigDecimal getAccountCBAFromTransaction(final List<InvoiceModelDao> invoices) {
        BigDecimal cba = BigDecimal.ZERO;
        for (final InvoiceModelDao cur : invoices) {
            cba = cba.add(InvoiceModelDaoHelper.getCBAAmount(cur));
        }
        return cba;
    }

    // We expect a clean up to date invoice, with all the items except the cba, that we will compute in that method
    public InvoiceItemModelDao computeCBAComplexity(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {

        final BigDecimal balance = getInvoiceBalance(invoice);

        // Current balance is negative, we need to generate a credit (positive CBA amount).
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            return new InvoiceItemModelDao(new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(), balance.negate(), invoice.getCurrency()));

        // Current balance is positive, we need to use some of the existing if available (negative CBA amount)
        } else if (balance.compareTo(BigDecimal.ZERO) > 0) {

            final List<InvoiceModelDao> allInvoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
            final BigDecimal accountCBA = getAccountCBAFromTransaction(allInvoices);
            if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            final BigDecimal positiveCreditAmount = accountCBA.compareTo(balance) > 0 ? balance : accountCBA;
            return new InvoiceItemModelDao(new CreditBalanceAdjInvoiceItem(invoice.getId(), invoice.getAccountId(), context.getCreatedDate().toLocalDate(), positiveCreditAmount.negate(), invoice.getCurrency()));
        } else {
            // 0 balance, nothing to do.
            return null;
        }
    }

    private BigDecimal getInvoiceBalance(final InvoiceModelDao invoice) {

        final InvoiceModelDao parentInvoice = invoice.getParentInvoice();
        if ((parentInvoice != null) && (InvoiceModelDaoHelper.getBalance(parentInvoice).compareTo(BigDecimal.ZERO) == 0)) {
            final Iterable<InvoiceItemModelDao> items = Iterables.filter(parentInvoice.getInvoiceItems(), new Predicate<InvoiceItemModelDao>() {
                @Override
                public boolean apply(@Nullable final InvoiceItemModelDao input) {
                    return input.getChildAccountId().equals(invoice.getAccountId());
                }
            });

            final BigDecimal childInvoiceAmountCharged = InvoiceModelDaoHelper.getAmountCharged(invoice);
            BigDecimal parentInvoiceAmountChargedForChild = BigDecimal.ZERO;

            for (InvoiceItemModelDao itemModel : items) {
                parentInvoiceAmountChargedForChild = parentInvoiceAmountChargedForChild.add(itemModel.getAmount());
            }

            return childInvoiceAmountCharged.add(parentInvoiceAmountChargedForChild.negate());

        }

        return InvoiceModelDaoHelper.getBalance(invoice);
    }

    // We let the code below rehydrate the invoice before we can add the CBA item
    public void addCBAComplexityFromTransaction(final UUID invoiceId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {

        final InvoiceSqlDao transInvoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
        final InvoiceModelDao invoice = transInvoiceDao.getById(invoiceId.toString(), context);
        invoiceDaoHelper.populateChildren(invoice, entitySqlDaoWrapperFactory, context);
        addCBAComplexityFromTransaction(invoice, entitySqlDaoWrapperFactory, context);
    }

    // We expect a clean up to date invoice, with all the items except the CBA, that we will compute in that method
    public void addCBAComplexityFromTransaction(final InvoiceModelDao invoice, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        final InvoiceItemModelDao cbaItem = computeCBAComplexity(invoice, entitySqlDaoWrapperFactory, context);
        if (cbaItem != null) {
            final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
            transInvoiceItemDao.create(cbaItem, context);
        }
        List<InvoiceModelDao> invoiceItemModelDaos = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        useExistingCBAFromTransaction(invoiceItemModelDaos, entitySqlDaoWrapperFactory, context);
    }

    public void addCBAComplexityFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {

        List<InvoiceModelDao> invoiceItemModelDaos = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        for (InvoiceModelDao cur : invoiceItemModelDaos) {
            addCBAIfNeeded(entitySqlDaoWrapperFactory, cur, context);
        }
        invoiceItemModelDaos = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(entitySqlDaoWrapperFactory, context);
        useExistingCBAFromTransaction(invoiceItemModelDaos, entitySqlDaoWrapperFactory, context);
    }

    /**
     * Adjust the invoice with a CBA item if the new invoice balance is negative.
     *
     * @param entitySqlDaoWrapperFactory the EntitySqlDaoWrapperFactory from the current transaction
     * @param invoice                    the invoice to adjust
     * @param context                    the call callcontext
     */
    private void addCBAIfNeeded(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
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


    private void useExistingCBAFromTransaction(final List<InvoiceModelDao> invoices, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws InvoiceApiException, EntityPersistenceException {

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
