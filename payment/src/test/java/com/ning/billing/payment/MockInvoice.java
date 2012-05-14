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

package com.ning.billing.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.ning.billing.util.dao.ObjectType;
import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.ExtendedEntityBase;

public class MockInvoice extends ExtendedEntityBase implements Invoice {
    private final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
    private final List<InvoicePayment> payments = new ArrayList<InvoicePayment>();
    private final UUID accountId;
    private final Integer invoiceNumber;
    private final DateTime invoiceDate;
    private final DateTime targetDate;
    private final Currency currency;
    private final boolean migrationInvoice;

    // used to create a new invoice
    public MockInvoice(UUID accountId, DateTime invoiceDate, DateTime targetDate, Currency currency) {
        this(UUID.randomUUID(), accountId, null, invoiceDate, targetDate, currency, false);
    }

    // used to hydrate invoice from persistence layer
    public MockInvoice(UUID invoiceId, UUID accountId, @Nullable Integer invoiceNumber, DateTime invoiceDate,
                          DateTime targetDate, Currency currency, boolean isMigrationInvoice) {
        super(invoiceId);
        this.accountId = accountId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.targetDate = targetDate;
        this.currency = currency;
        this.migrationInvoice = isMigrationInvoice;
    }

    @Override
    public boolean addInvoiceItem(final InvoiceItem item) {
        return invoiceItems.add(item);
    }

    @Override
    public boolean addInvoiceItems(final List<InvoiceItem> items) {
        return this.invoiceItems.addAll(items);
    }

    @Override
    public List<InvoiceItem> getInvoiceItems() {
        return invoiceItems;
    }

    @Override
    public <T extends InvoiceItem> List<InvoiceItem> getInvoiceItems(Class<T> clazz) {
        List<InvoiceItem> results = new ArrayList<InvoiceItem>();
        for (InvoiceItem item : invoiceItems) {
            if ( clazz.isInstance(item) ) {
                results.add(item);
            }
        }
        return results;
    }

    @Override
    public int getNumberOfItems() {
        return invoiceItems.size();
    }

    @Override
    public boolean addPayment(final InvoicePayment payment) {
        return payments.add(payment);
    }

    @Override
    public boolean addPayments(final List<InvoicePayment> payments) {
        return this.payments.addAll(payments);
    }

    @Override
    public List<InvoicePayment> getPayments() {
        return payments;
    }

    @Override
    public int getNumberOfPayments() {
        return payments.size();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    /**
     * null until retrieved from the database
     * @return the invoice number
     */
    @Override
    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    @Override
    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    @Override
    public DateTime getTargetDate() {
        return targetDate;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }
    
    @Override
    public boolean isMigrationInvoice() {
		return migrationInvoice;
	}

	@Override
    public DateTime getLastPaymentAttempt() {
        DateTime lastPaymentAttempt = null;

        for (final InvoicePayment paymentAttempt : payments) {
            DateTime paymentAttemptDate = paymentAttempt.getPaymentAttemptDate();
            if (lastPaymentAttempt == null) {
                lastPaymentAttempt = paymentAttemptDate;
            }

            if (lastPaymentAttempt.isBefore(paymentAttemptDate)) {
                lastPaymentAttempt = paymentAttemptDate;
            }
        }

        return lastPaymentAttempt;
    }

    @Override
    public BigDecimal getAmountPaid() {
        BigDecimal amountPaid = BigDecimal.ZERO;
        for (final InvoicePayment payment : payments) {
            if (payment.getAmount() != null) {
                amountPaid = amountPaid.add(payment.getAmount());
            }
        }
        return amountPaid;
    }

    @Override
    public BigDecimal getTotalAmount() {
        BigDecimal result = BigDecimal.ZERO;
    
        for(InvoiceItem i : invoiceItems) {
            result = result.add(i.getAmount());
        }
        return result;
    }

    @Override
    public BigDecimal getBalance() {
        return getTotalAmount().subtract(getAmountPaid());
    }

    @Override
    public boolean isDueForPayment(final DateTime targetDate, final int numberOfDays) {
        if (getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        DateTime lastPaymentAttempt = getLastPaymentAttempt();
        if (lastPaymentAttempt == null) {
            return true;
        }

        return !lastPaymentAttempt.plusDays(numberOfDays).isAfter(targetDate);
    }

    @Override
    public String toString() {
        return "DefaultInvoice [items=" + invoiceItems + ", payments=" + payments + ", id=" + id + ", accountId=" + accountId + ", invoiceDate=" + invoiceDate + ", targetDate=" + targetDate + ", currency=" + currency + ", amountPaid=" + getAmountPaid() + ", lastPaymentAttempt=" + getLastPaymentAttempt() + "]";
    }

    @Override
    public ObjectType getObjectType() {
        return ObjectType.RECURRING_INVOICE_ITEM;
    }

    @Override
    public void saveFieldValue(String fieldName, String fieldValue, CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveFields(List<CustomField> fields, CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearPersistedFields(CallContext context) {
        throw new UnsupportedOperationException();
    }
}

