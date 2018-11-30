/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceStatus;
import org.killbill.billing.invoice.model.CreditBalanceAdjInvoiceItem;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.billing.util.tag.Tag;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class CBADao {

    private final InvoiceDaoHelper invoiceDaoHelper;

    @Inject
    public CBADao(final InvoiceDaoHelper invoiceDaoHelper) {
        this.invoiceDaoHelper = invoiceDaoHelper;
    }

    // PERF: Compute the CBA directly in the database (faster than re-constructing all invoices)
    public BigDecimal getAccountCBAFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalTenantContext context) {
        final InvoiceItemSqlDao invoiceItemSqlDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        return invoiceItemSqlDao.getAccountCBA(context);
    }

    // We expect a clean up to date invoice, with all the items except the cba, that we will compute in that method
    public InvoiceItemModelDao computeCBAComplexity(final InvoiceModelDao invoice,
                                                    @Nullable final BigDecimal accountCBAOrNull,
                                                    @Nullable final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                    final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        final BigDecimal balance = getInvoiceBalance(invoice);

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            // Current balance is negative, we need to generate a credit (positive CBA amount)
            return buildCBAItem(invoice, balance, context);
        } else if (balance.compareTo(BigDecimal.ZERO) > 0 && invoice.getStatus() == InvoiceStatus.COMMITTED && !invoice.isWrittenOff()) {
            // Current balance is positive and the invoice is COMMITTED, we need to use some of the existing if available (negative CBA amount)
            // PERF: in some codepaths, the CBA maybe have already been computed
            BigDecimal accountCBA = accountCBAOrNull;
            if (accountCBAOrNull == null) {
                accountCBA = getAccountCBAFromTransaction(entitySqlDaoWrapperFactory, context);
            }

            if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            final BigDecimal positiveCreditAmount = accountCBA.compareTo(balance) > 0 ? balance : accountCBA;
            return buildCBAItem(invoice, positiveCreditAmount, context);
        } else {
            // 0 balance, nothing to do.
            return null;
        }
    }

    private BigDecimal getInvoiceBalance(final InvoiceModelDao invoice) {

        final InvoiceModelDao parentInvoice = invoice.getParentInvoice();
        if ((parentInvoice != null) && (InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(parentInvoice).compareTo(BigDecimal.ZERO) == 0)) {
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

        return InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice);
    }

    // We let the code below rehydrate the invoice before we can add the CBA item
    // PERF: when possible, prefer the method below to avoid re-fetching the invoice
    public void doCBAComplexityFromTransaction(final UUID invoiceId,
                                               final List<Tag> invoicesTags,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        final InvoiceSqlDao transInvoiceDao = entitySqlDaoWrapperFactory.become(InvoiceSqlDao.class);
        final InvoiceModelDao invoice = transInvoiceDao.getById(invoiceId.toString(), context);
        invoiceDaoHelper.populateChildren(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);

        doCBAComplexityFromTransaction(invoice, invoicesTags, entitySqlDaoWrapperFactory, context);
    }

    public void doCBAComplexityFromTransaction(final List<Tag> invoicesTags,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        doCBAComplexityFromTransaction((InvoiceModelDao) null, invoicesTags, entitySqlDaoWrapperFactory, context);
    }

    // Note! We expect an *up-to-date* invoice, with all the items and payments except the CBA, that we will compute in that method
    public void doCBAComplexityFromTransaction(@Nullable final InvoiceModelDao invoice,
                                               final List<Tag> invoicesTags,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        // PERF: It is expensive to retrieve and construct all invoice objects. To check if there is effectively something to use, compute the CBA by the database first
        BigDecimal remainingAccountCBA = getAccountCBAFromTransaction(entitySqlDaoWrapperFactory, context);

        if (invoice != null) {
            // Generate or use CBA for that specific invoice
            remainingAccountCBA = computeCBAComplexityAndCreateCBAItem(remainingAccountCBA, invoice, entitySqlDaoWrapperFactory, context);
        }

        useExistingCBAFromTransaction(remainingAccountCBA, invoicesTags, entitySqlDaoWrapperFactory, context);
    }

    // Distribute account CBA across all COMMITTED unpaid invoices
    private void useExistingCBAFromTransaction(final BigDecimal accountCBA,
                                               final List<Tag> invoicesTags,
                                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                               final InternalCallContext context) throws InvoiceApiException, EntityPersistenceException {
        if (accountCBA.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // PERF: Computing the invoice balance is difficult to do in the DB, so we effectively need to retrieve all invoices on the account and filter the unpaid ones in memory.
        // This should be infrequent though because of the account CBA check above.
        final List<InvoiceModelDao> allInvoices = invoiceDaoHelper.getAllInvoicesByAccountFromTransaction(false, invoicesTags, entitySqlDaoWrapperFactory, context);
        final List<InvoiceModelDao> unpaidInvoices = invoiceDaoHelper.getUnpaidInvoicesByAccountFromTransaction(allInvoices, null);
        // We order the same os BillingStateCalculator-- should really share the comparator
        final List<InvoiceModelDao> orderedUnpaidInvoices = Ordering.from(new Comparator<InvoiceModelDao>() {
            @Override
            public int compare(final InvoiceModelDao i1, final InvoiceModelDao i2) {
                return i1.getInvoiceDate().compareTo(i2.getInvoiceDate());
            }
        }).immutableSortedCopy(unpaidInvoices);

        BigDecimal remainingAccountCBA = accountCBA;
        for (final InvoiceModelDao unpaidInvoice : orderedUnpaidInvoices) {
            remainingAccountCBA = computeCBAComplexityAndCreateCBAItem(remainingAccountCBA, unpaidInvoice, entitySqlDaoWrapperFactory, context);
            if (remainingAccountCBA.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
    }

    // Return the updated account CBA
    private BigDecimal computeCBAComplexityAndCreateCBAItem(final BigDecimal accountCBA,
                                                            final InvoiceModelDao invoice,
                                                            final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                                                            final InternalCallContext context) throws EntityPersistenceException, InvoiceApiException {
        final InvoiceItemModelDao cbaItem = computeCBAComplexity(invoice, accountCBA, entitySqlDaoWrapperFactory, context);
        if (cbaItem != null) {
            createCBAItem(invoice, cbaItem, entitySqlDaoWrapperFactory, context);
            return accountCBA.add(cbaItem.getAmount());
        } else {
            return accountCBA;
        }
    }

    private void createCBAItem(final InvoiceModelDao invoiceModelDao,
                               final InvoiceItemModelDao cbaItem,
                               final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory,
                               final InternalCallContext context) throws EntityPersistenceException {
        final InvoiceItemSqlDao transInvoiceItemDao = entitySqlDaoWrapperFactory.become(InvoiceItemSqlDao.class);
        transInvoiceItemDao.create(cbaItem, context);

        // Refresh the in-memory item
        invoiceModelDao.addInvoiceItem(cbaItem);
    }

    private InvoiceItemModelDao buildCBAItem(final InvoiceModelDao invoice,
                                             final BigDecimal amount,
                                             final InternalCallContext context) {
        return new InvoiceItemModelDao(new CreditBalanceAdjInvoiceItem(invoice.getId(),
                                                                       invoice.getAccountId(),
                                                                       context.getCreatedDate().toLocalDate(),
                                                                       amount.negate(),
                                                                       invoice.getCurrency()));
    }
}
