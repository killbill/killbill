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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.callcontext.InternalCallContext;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.util.entity.DefaultPagination;
import com.ning.billing.util.entity.Pagination;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.inject.Inject;

public class MockInvoiceDao extends MockEntityDaoBase<InvoiceModelDao, Invoice, InvoiceApiException> implements InvoiceDao {

    private final PersistentBus eventBus;
    private final Object monitor = new Object();
    private final Map<UUID, InvoiceModelDao> invoices = new LinkedHashMap<UUID, InvoiceModelDao>();
    private final Map<UUID, InvoiceItemModelDao> items = new LinkedHashMap<UUID, InvoiceItemModelDao>();
    private final Map<UUID, InvoicePaymentModelDao> payments = new LinkedHashMap<UUID, InvoicePaymentModelDao>();
    private final BiMap<UUID, Long> accountRecordIds = HashBiMap.create();

    @Inject
    public MockInvoiceDao(final PersistentBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void createInvoice(final InvoiceModelDao invoice, final List<InvoiceItemModelDao> invoiceItems,
                              final List<InvoicePaymentModelDao> invoicePayments, final boolean isRealInvoice, final Map<UUID, DateTime> callbackDateTimePerSubscriptions, final InternalCallContext context) {
        synchronized (monitor) {
            invoices.put(invoice.getId(), invoice);
            for (final InvoiceItemModelDao invoiceItemModelDao : invoiceItems) {
                items.put(invoiceItemModelDao.getId(), invoiceItemModelDao);
            }
            for (final InvoicePaymentModelDao paymentModelDao : invoicePayments) {
                payments.put(paymentModelDao.getId(), paymentModelDao);
            }
            accountRecordIds.put(invoice.getAccountId(), context.getAccountRecordId());
        }
        try {
            eventBus.post(new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                          InvoiceModelDaoHelper.getBalance(invoice), invoice.getCurrency(),
                                                          context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()));
        } catch (PersistentBus.EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public InvoiceModelDao getById(final UUID id, final InternalTenantContext context) {
        synchronized (monitor) {
            return invoices.get(id);
        }
    }

    @Override
    public InvoiceModelDao getByNumber(final Integer number, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (invoice.getInvoiceNumber().equals(number)) {
                    return invoice;
                }
            }
        }

        return null;
    }

    @Override
    public Pagination<InvoiceModelDao> getAll(final InternalTenantContext context) {
        synchronized (monitor) {
            return new DefaultPagination<InvoiceModelDao>((long) invoices.values().size(), invoices.values().iterator());
        }
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.isMigrated()) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public List<InvoiceModelDao> getInvoicesByAccount(final LocalDate fromDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> invoicesForAccount = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : getAll(context)) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.getTargetDate().isBefore(fromDate) && !invoice.isMigrated()) {
                    invoicesForAccount.add(invoice);
                }
            }
        }

        return invoicesForAccount;
    }

    @Override
    public List<InvoiceModelDao> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            for (final InvoiceModelDao invoice : invoices.values()) {
                for (final InvoiceItemModelDao item : invoice.getInvoiceItems()) {
                    if (subscriptionId.equals(item.getSubscriptionId()) && !invoice.isMigrated()) {
                        result.add(invoice);
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Pagination<InvoiceModelDao> searchInvoices(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        final List<InvoiceModelDao> results = new LinkedList<InvoiceModelDao>();
        for (final InvoiceModelDao invoice : getAll(context)) {
            if (invoice.getId().toString().equals(searchKey) ||
                invoice.getAccountId().toString().equals(searchKey) ||
                invoice.getInvoiceNumber().toString().equals(searchKey) ||
                invoice.getCurrency().toString().equals(searchKey)) {
                results.add(invoice);
            }
        }

        return DefaultPagination.<InvoiceModelDao>build(offset, limit, results);
    }

    @Override
    public void test(final InternalTenantContext context) {
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final InvoicePaymentModelDao payment : payments.values()) {
                if (paymentId.equals(payment.getPaymentId())) {
                    return payment.getInvoiceId();
                }
            }
        }
        return null;
    }

    @Override
    public List<InvoicePaymentModelDao> getInvoicePayments(final UUID paymentId, final InternalTenantContext context) {
        final List<InvoicePaymentModelDao> result = new LinkedList<InvoicePaymentModelDao>();
        synchronized (monitor) {
            for (final InvoicePaymentModelDao payment : payments.values()) {
                if (paymentId.equals(payment.getPaymentId())) {
                    result.add(payment);
                }
            }
        }
        return result;
    }

    @Override
    public void notifyOfPayment(final InvoicePaymentModelDao invoicePayment, final InternalCallContext context) {
        synchronized (monitor) {
            payments.put(invoicePayment.getId(), invoicePayment);
        }
    }

    @Override
    public void consumeExstingCBAOnAccountWithUnpaidInvoices(final UUID accountId, final InternalCallContext context) {
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        BigDecimal balance = BigDecimal.ZERO;

        for (final InvoiceModelDao invoice : getAll(context)) {
            if (accountId.equals(invoice.getAccountId())) {
                balance = balance.add(InvoiceModelDaoHelper.getBalance(invoice));
            }
        }

        return balance;
    }

    @Override
    public List<InvoiceModelDao> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        final List<InvoiceModelDao> unpaidInvoices = new ArrayList<InvoiceModelDao>();

        for (final InvoiceModelDao invoice : getAll(context)) {
            if (accountId.equals(invoice.getAccountId()) && (InvoiceModelDaoHelper.getBalance(invoice).compareTo(BigDecimal.ZERO) > 0) && !invoice.isMigrated()) {
                unpaidInvoices.add(invoice);
            }
        }

        return unpaidInvoices;
    }

    @Override
    public List<InvoiceModelDao> getAllInvoicesByAccount(final InternalTenantContext context) {
        final List<InvoiceModelDao> result = new ArrayList<InvoiceModelDao>();

        synchronized (monitor) {
            final UUID accountId = accountRecordIds.inverse().get(context.getAccountRecordId());
            for (final InvoiceModelDao invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId())) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public InvoicePaymentModelDao postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getRemainingAmountPaid(final UUID invoicePaymentId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getAccountIdFromInvoicePaymentId(final UUID invoicePaymentId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePaymentModelDao> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePaymentModelDao getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao insertExternalCharge(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId,
                                                    @Nullable final String description, final BigDecimal amount, final LocalDate effectiveDate,
                                                    final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao insertCredit(final UUID accountId, final UUID invoiceId, final BigDecimal amount, final LocalDate effectiveDate,
                                            final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItemModelDao insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                           final LocalDate effectiveDate, @Nullable final BigDecimal amount,
                                                           @Nullable final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public InvoicePaymentModelDao createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted,
                                               final Map<UUID, BigDecimal> invoiceItemIdsWithAmounts, final UUID paymentCookieId,
                                               final InternalCallContext context)
            throws InvoiceApiException {
        return null;
    }

    @Override
    public void deleteCBA(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId, final InternalCallContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }
}
