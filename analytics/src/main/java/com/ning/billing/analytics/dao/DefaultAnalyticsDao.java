/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.dao;

import javax.inject.Inject;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.analytics.model.BusinessAccount;
import com.ning.billing.analytics.model.BusinessInvoice;
import com.ning.billing.analytics.model.BusinessInvoiceItem;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;

public class DefaultAnalyticsDao implements AnalyticsDao {
    private final BusinessAccountSqlDao accountSqlDao;
    private final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao;
    private final BusinessInvoiceSqlDao invoiceSqlDao;
    private final BusinessInvoiceItemSqlDao invoiceItemSqlDao;

    @Inject
    public DefaultAnalyticsDao(final BusinessAccountSqlDao accountSqlDao,
                               final BusinessSubscriptionTransitionSqlDao subscriptionTransitionSqlDao,
                               final BusinessInvoiceSqlDao invoiceSqlDao,
                               final BusinessInvoiceItemSqlDao invoiceItemSqlDao) {
        this.accountSqlDao = accountSqlDao;
        this.subscriptionTransitionSqlDao = subscriptionTransitionSqlDao;
        this.invoiceSqlDao = invoiceSqlDao;
        this.invoiceItemSqlDao = invoiceItemSqlDao;
    }

    @Override
    public BusinessAccount getAccountByKey(final String accountKey) {
        return accountSqlDao.getAccount(accountKey);
    }

    @Override
    public List<BusinessSubscriptionTransition> getTransitionsByKey(final String externalKey) {
        return subscriptionTransitionSqlDao.getTransitions(externalKey);
    }

    @Override
    public List<BusinessInvoice> getInvoicesByKey(final String accountKey) {
        return invoiceSqlDao.getInvoicesForAccount(accountKey);
    }

    @Override
    public List<BusinessInvoiceItem> getInvoiceItemsForInvoice(final String invoiceId) {
        return invoiceItemSqlDao.getInvoiceItemsForInvoice(invoiceId);
    }

    @Override
    public void createInvoice(final String accountKey, final BusinessInvoice invoice, final Iterable<BusinessInvoiceItem> invoiceItems) {
        invoiceSqlDao.inTransaction(new Transaction<Void, BusinessInvoiceSqlDao>() {
            @Override
            public Void inTransaction(final BusinessInvoiceSqlDao transactional, final TransactionStatus status) throws Exception {
                // Create the invoice
                transactional.createInvoice(invoice);

                // Add associated invoice items
                final BusinessInvoiceItemSqlDao invoiceItemSqlDao = transactional.become(BusinessInvoiceItemSqlDao.class);
                for (final BusinessInvoiceItem invoiceItem : invoiceItems) {
                    invoiceItemSqlDao.createInvoiceItem(invoiceItem);
                }

                // Update BAC
                final BusinessAccountSqlDao accountSqlDao = transactional.become(BusinessAccountSqlDao.class);
                final BusinessAccount account = accountSqlDao.getAccount(accountKey);
                if (account == null) {
                    throw new IllegalStateException("Account does not exist for key " + accountKey);
                }
                account.setBalance(account.getBalance().add(invoice.getBalance()));
                account.setLastInvoiceDate(invoice.getInvoiceDate());
                account.setTotalInvoiceBalance(account.getTotalInvoiceBalance().add(invoice.getBalance()));
                account.setUpdatedDt(new DateTime(DateTimeZone.UTC));
                accountSqlDao.saveAccount(account);

                return null;
            }
        });
    }
}
