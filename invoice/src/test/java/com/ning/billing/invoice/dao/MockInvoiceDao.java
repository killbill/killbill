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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.tag.ControlTagType;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;

public class MockInvoiceDao implements InvoiceDao {
    private final Bus eventBus;
    private final Object monitor = new Object();
    private final Map<UUID, Invoice> invoices = new LinkedHashMap<UUID, Invoice>();

    @Inject
    public MockInvoiceDao(Bus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(final Invoice invoice, final CallContext context) {
        synchronized (monitor) {
            invoices.put(invoice.getId(), invoice);
        }
        try {
            eventBus.post(new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                 invoice.getBalance(), invoice.getCurrency(),
                                                                 invoice.getInvoiceDate()));
        }
        catch (Bus.EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Invoice getById(UUID id) {
        synchronized (monitor) {
            return invoices.get(id);
        }
    }

    @Override
    public List<Invoice> get() {
        synchronized (monitor) {
            return new ArrayList<Invoice>(invoices.values());
        }
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.isMigrationInvoice()) {
                    result.add(invoice);
                }
            }
        }
        return result;
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId, DateTime fromDate) {
        List<Invoice> invoicesForAccount = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : get()) {
                if (accountId.equals(invoice.getAccountId()) && !invoice.getTargetDate().isBefore(fromDate) && !invoice.isMigrationInvoice()) {
                    invoicesForAccount.add(invoice);
                }
            }
        }

        return invoicesForAccount;
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(UUID subscriptionId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                for (InvoiceItem item : invoice.getInvoiceItems()) {
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
    public void test() {
    }

    @Override
    public UUID getInvoiceIdByPaymentAttemptId(UUID paymentAttemptId) {
        synchronized(monitor) {
            for (Invoice invoice : invoices.values()) {
                for (InvoicePayment payment : invoice.getPayments()) {
                    if (paymentAttemptId.equals(payment.getPaymentAttemptId())) {
                        return invoice.getId();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        synchronized(monitor) {
            for (Invoice invoice : invoices.values()) {
                for (InvoicePayment payment : invoice.getPayments()) {
                    if (paymentAttemptId.equals(payment.getPaymentAttemptId())) {
                        return payment;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment, CallContext context) {
        synchronized (monitor) {
            Invoice invoice = invoices.get(invoicePayment.getInvoiceId());
            if (invoice != null) {
                invoice.addPayment(invoicePayment);
            }
        }
    }

    @Override
    public BigDecimal getAccountBalance(UUID accountId) {
        BigDecimal balance = BigDecimal.ZERO;

        for (Invoice invoice : get()) {
            if (accountId.equals(invoice.getAccountId())) {
                balance = balance.add(invoice.getBalance());
            }
        }

        return balance;
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(UUID accountId, DateTime upToDate) {
        List<Invoice> unpaidInvoices = new ArrayList<Invoice>();

        for (Invoice invoice : get()) {
            if (accountId.equals(invoice.getAccountId()) && (invoice.getBalance().compareTo(BigDecimal.ZERO) > 0) && !invoice.isMigrationInvoice()) {
                unpaidInvoices.add(invoice);
            }
        }

        return unpaidInvoices;
    }

	@Override
	public List<Invoice> getAllInvoicesByAccount(UUID accountId) {
		  List<Invoice> result = new ArrayList<Invoice>();

	        synchronized (monitor) {
	            for (Invoice invoice : invoices.values()) {
	                if (accountId.equals(invoice.getAccountId())) {
	                    result.add(invoice);
	                }
	            }
	        }
	        return result;
	}

    @Override
    public void addControlTag(ControlTagType controlTagType, UUID objectId, CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeControlTag(ControlTagType controlTagType, UUID objectId, CallContext context) {
        throw new UnsupportedOperationException();
    }
}
