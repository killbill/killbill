/*
 * Copyright 2010-2011 Ning, Inc.
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

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcsapi.bus.Bus;

import com.google.inject.Inject;

public class MockInvoiceDao implements InvoiceDao {

    private final Bus eventBus;
    private final Object monitor = new Object();
    private final Map<UUID, Invoice> invoices = new LinkedHashMap<UUID, Invoice>();

    @Inject
    public MockInvoiceDao(final Bus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(final Invoice invoice, final int billCycleDay, final boolean isRealInvoice, final InternalCallContext context) {
        synchronized (monitor) {
            invoices.put(invoice.getId(), invoice);
        }
        try {
            eventBus.post(new DefaultInvoiceCreationEvent(invoice.getId(), invoice.getAccountId(),
                                                          invoice.getBalance(), invoice.getCurrency(),
                                                          null, 1L, 1L), context);
        } catch (Bus.EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Invoice getById(final UUID id, final InternalTenantContext context) {
        synchronized (monitor) {
            return invoices.get(id);
        }
    }

    @Override
    public Invoice getByNumber(final Integer number, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                if (invoice.getInvoiceNumber().equals(number)) {
                    return invoice;
                }
            }
        }

        return null;
    }

    @Override
    public List<Invoice> get(final InternalTenantContext context) {
        synchronized (monitor) {
            return new ArrayList<Invoice>(invoices.values());
        }
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        final List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.isMigrationInvoice()) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final LocalDate fromDate, final InternalTenantContext context) {
        final List<Invoice> invoicesForAccount = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (final Invoice invoice : get(context)) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.getTargetDate().isBefore(fromDate) && !invoice.isMigrationInvoice()) {
                    invoicesForAccount.add(invoice);
                }
            }
        }

        return invoicesForAccount;
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                for (final InvoiceItem item : invoice.getInvoiceItems()) {
                    if (subscriptionId.equals(item.getSubscriptionId()) && !invoice.isMigrationInvoice()) {
                        result.add(invoice);
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void test(final InternalTenantContext context) {
    }

    @Override
    public UUID getInvoiceIdByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                for (final InvoicePayment payment : invoice.getPayments()) {
                    if (paymentId.equals(payment.getPaymentId())) {
                        return invoice.getId();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public List<InvoicePayment> getInvoicePayments(final UUID paymentId, final InternalTenantContext context) {
        final List<InvoicePayment> result = new LinkedList<InvoicePayment>();
        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                for (final InvoicePayment payment : invoice.getPayments()) {
                    if (paymentId.equals(payment.getPaymentId())) {
                        result.add(payment);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void notifyOfPayment(final InvoicePayment invoicePayment, final InternalCallContext context) {
        synchronized (monitor) {
            final Invoice invoice = invoices.get(invoicePayment.getInvoiceId());
            if (invoice != null) {
                invoice.addPayment(invoicePayment);
            }
        }
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        BigDecimal balance = BigDecimal.ZERO;

        for (final Invoice invoice : get(context)) {
            if (accountId.equals(invoice.getAccountId())) {
                balance = balance.add(invoice.getBalance());
            }
        }

        return balance;
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        final List<Invoice> unpaidInvoices = new ArrayList<Invoice>();

        for (final Invoice invoice : get(context)) {
            if (accountId.equals(invoice.getAccountId()) && (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) && !invoice.isMigrationInvoice()) {
                unpaidInvoices.add(invoice);
            }
        }

        return unpaidInvoices;
    }

    @Override
    public List<Invoice> getAllInvoicesByAccount(final UUID accountId, final InternalTenantContext context) {
        final List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (final Invoice invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId())) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public InvoicePayment postChargeback(final UUID invoicePaymentId, final BigDecimal amount, final InternalCallContext context) throws InvoiceApiException {
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
    public List<InvoicePayment> getChargebacksByAccountId(final UUID accountId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<InvoicePayment> getChargebacksByPaymentId(final UUID paymentId, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoicePayment getChargebackById(final UUID chargebackId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItem getExternalChargeById(final UUID externalChargeId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItem insertExternalCharge(final UUID accountId, @Nullable final UUID invoiceId, @Nullable final UUID bundleId,
                                            @Nullable final String description, final BigDecimal amount, final LocalDate effectiveDate,
                                            final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItem getCreditById(final UUID creditId, final InternalTenantContext context) throws InvoiceApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItem insertCredit(final UUID accountId, final UUID invoiceId, final BigDecimal amount, final LocalDate effectiveDate,
                                    final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InvoiceItem insertInvoiceItemAdjustment(final UUID accountId, final UUID invoiceId, final UUID invoiceItemId,
                                                   final LocalDate effectiveDate, @Nullable final BigDecimal amount,
                                                   @Nullable final Currency currency, final InternalCallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getAccountCBA(final UUID accountId, final InternalTenantContext context) {
        return null;
    }

    @Override
    public InvoicePayment createRefund(final UUID paymentId, final BigDecimal amount, final boolean isInvoiceAdjusted,
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
