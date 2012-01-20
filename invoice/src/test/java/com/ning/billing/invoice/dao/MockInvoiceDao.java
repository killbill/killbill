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
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationNotification;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.payment.api.InvoicePayment;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.EventBus.EventBusException;

public class MockInvoiceDao implements InvoiceDao {
    private final EventBus eventBus;
    private final Object monitor = new Object();
    private final Map<String, Invoice> invoices = new LinkedHashMap<String, Invoice>();
    private final Map<UUID, InvoicePayment> invoicePayments = new LinkedHashMap<UUID, InvoicePayment>();

    @Inject
    public MockInvoiceDao(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(Invoice invoice) {
        synchronized (monitor) {
            invoices.put(invoice.getId().toString(), invoice);
        }
        try {
            eventBus.post(new DefaultInvoiceCreationNotification(invoice.getId(), invoice.getAccountId(),
                                                                 invoice.getAmountOutstanding(), invoice.getCurrency(),
                                                                 invoice.getInvoiceDate()));
        }
        catch (EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    private Invoice munge(Invoice invoice) {
        if (invoice == null) {
            return null;
        }

        DateTime lastPaymentDate = null;
        BigDecimal amountPaid = new BigDecimal("0");

        for (InvoicePayment invoicePayment : invoicePayments.values()) {
            if (invoicePayment.getInvoiceId().equals(invoice.getId().toString())) {
                if (lastPaymentDate == null || lastPaymentDate.isBefore(invoicePayment.getPaymentAttemptDate())) {
                    lastPaymentDate = invoicePayment.getPaymentAttemptDate();
                }
                if (invoicePayment.getAmount() != null) {
                    amountPaid.add(invoicePayment.getAmount());
                }
            }
        }
        return new DefaultInvoice(invoice.getId(),
                                  invoice.getAccountId(),
                                  invoice.getInvoiceDate(),
                                  invoice.getTargetDate(),
                                  invoice.getCurrency(),
                                  lastPaymentDate,
                                  amountPaid,
                                  invoice.getItems());
    }

    private List<Invoice> munge(Collection<Invoice> invoices) {
        List<Invoice> result = new ArrayList<Invoice>();
        for (Invoice invoice : invoices) {
            result.add(munge(invoice));
        }
        return result;
    }

    @Override
    public Invoice getById(String id) {
        synchronized (monitor) {
            return munge(invoices.get(id));
        }
    }

    @Override
    public List<Invoice> get() {
        synchronized (monitor) {
            return munge(invoices.values());
        }
    }

    @Override
    public List<Invoice> getInvoicesByAccount(String accountId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                if (accountId.equals(invoice.getAccountId().toString())) {
                    result.add(invoice);
                }
            }
        }
        return munge(result);
    }

    @Override
    public List<Invoice> getInvoicesBySubscription(String subscriptionId) {
        List<Invoice> result = new ArrayList<Invoice>();

        synchronized (monitor) {
            for (Invoice invoice : invoices.values()) {
                for (InvoiceItem item : invoice.getItems()) {
                    if (subscriptionId.equals(item.getSubscriptionId().toString())) {
                        result.add(invoice);
                        break;
                    }
                }
            }
        }
        return munge(result);
    }

    @Override
    public List<UUID> getInvoicesForPayment(Date targetDate, int numberOfDays) {
        Set<UUID> result = new LinkedHashSet<UUID>();

        synchronized (monitor) {
            for (InvoicePayment invoicePayment : invoicePayments.values()) {
                Invoice invoice = invoices.get(invoicePayment.getInvoiceId());
                if ((invoice != null) &&
                    (((invoicePayment.getPaymentAttemptDate() == null) || !invoicePayment.getPaymentAttemptDate().plusDays(numberOfDays).isAfter(targetDate.getTime())) &&
                    (invoice.getTotalAmount() != null) && invoice.getTotalAmount().doubleValue() >= 0) &&
                    ((invoicePayment.getAmount() == null) || invoicePayment.getAmount().doubleValue() >= invoice.getTotalAmount().doubleValue())) {
                        result.add(invoice.getId());
                }
            }
        }

        return new ArrayList<UUID>(result);
    }

    @Override
    public void test() {
    }

    @Override
    public String getInvoiceIdByPaymentAttemptId(UUID paymentAttemptId) {
        synchronized(monitor) {
            for (InvoicePayment invoicePayment : invoicePayments.values()) {
                if (paymentAttemptId.toString().equals(invoicePayment.getPaymentAttemptId())) {
                    return invoicePayment.getInvoiceId().toString();
                }
            }
        }
        return null;
    }

    @Override
    public InvoicePayment getInvoicePayment(UUID paymentAttemptId) {
        synchronized(monitor) {
            return invoicePayments.get(paymentAttemptId);
        }
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment) {
      synchronized (monitor) {
          invoicePayments.put(invoicePayment.getPaymentAttemptId(), invoicePayment);
      }
    }
}
