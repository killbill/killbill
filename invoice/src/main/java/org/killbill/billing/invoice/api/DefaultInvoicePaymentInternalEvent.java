/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.events.BusEventBase;
import org.killbill.billing.events.InvoicePaymentInternalEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class DefaultInvoicePaymentInternalEvent extends BusEventBase implements InvoicePaymentInternalEvent {

    private final UUID accountId;
    private final UUID paymentId;
    private final UUID paymentAttemptId;
    private final InvoicePaymentType type;
    private final UUID invoiceId;
    private final DateTime paymentDate;
    private final BigDecimal amount;
    private final Currency currency;
    private final UUID linkedInvoicePaymentId;
    private final String paymentCookieId;
    private final Currency processedCurrency;

    @JsonCreator
    public DefaultInvoicePaymentInternalEvent(@JsonProperty("accountId") final UUID accountId,
                                              @JsonProperty("paymentId") final UUID paymentId,
                                              @JsonProperty("paymentAttemptId") final UUID paymentAttemptId,
                                              @JsonProperty("type") final InvoicePaymentType type,
                                              @JsonProperty("invoiceId") final UUID invoiceId,
                                              @JsonProperty("paymentDate") final DateTime paymentDate,
                                              @JsonProperty("amount") final BigDecimal amount,
                                              @JsonProperty("currency") final Currency currency,
                                              @JsonProperty("linkedInvoicePaymentId") final UUID linkedInvoicePaymentId,
                                              @JsonProperty("paymentCookieId") final String paymentCookieId,
                                              @JsonProperty("processedCurrency") final Currency processedCurrency,
                                              @JsonProperty("searchKey1") final Long searchKey1,
                                              @JsonProperty("searchKey2") final Long searchKey2,
                                              @JsonProperty("userToken") final UUID userToken) {
        super(searchKey1, searchKey2, userToken);
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.paymentAttemptId = paymentAttemptId;
        this.type = type;
        this.invoiceId = invoiceId;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.currency = currency;
        this.linkedInvoicePaymentId = linkedInvoicePaymentId;
        this.paymentCookieId = paymentCookieId;
        this.processedCurrency = processedCurrency;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public UUID getPaymentAttemptId() {
        return paymentAttemptId;
    }

    public InvoicePaymentType getType() {
        return type;
    }

    @Override
    public UUID getInvoiceId() {
        return invoiceId;
    }

    @Override
    public DateTime getPaymentDate() {
        return paymentDate;
    }

    @Override
    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public UUID getLinkedInvoicePaymentId() {
        return linkedInvoicePaymentId;
    }

    @Override
    public String getPaymentCookieId() {
        return paymentCookieId;
    }

    @Override
    public Currency getProcessedCurrency() {
        return processedCurrency;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getInvoicePaymentInternalEventClass().toString());
        sb.append(" {accountId=").append(accountId);
        sb.append(", paymentId=").append(paymentId);
        sb.append(", type=").append(type);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", paymentDate=").append(paymentDate);
        sb.append(", amount=").append(amount);
        sb.append(", currency=").append(currency);
        sb.append(", linkedInvoicePaymentId=").append(linkedInvoicePaymentId);
        sb.append(", paymentCookieId='").append(paymentCookieId).append('\'');
        sb.append(", processedCurrency=").append(processedCurrency);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final DefaultInvoicePaymentInternalEvent that = (DefaultInvoicePaymentInternalEvent) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (paymentId != null ? !paymentId.equals(that.paymentId) : that.paymentId != null) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (paymentDate != null ? paymentDate.compareTo(that.paymentDate) != 0 : that.paymentDate != null) {
            return false;
        }
        if (amount != null ? amount.compareTo(that.amount) != 0 : that.amount != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (linkedInvoicePaymentId != null ? !linkedInvoicePaymentId.equals(that.linkedInvoicePaymentId) : that.linkedInvoicePaymentId != null) {
            return false;
        }
        if (paymentCookieId != null ? !paymentCookieId.equals(that.paymentCookieId) : that.paymentCookieId != null) {
            return false;
        }
        return processedCurrency == that.processedCurrency;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (paymentId != null ? paymentId.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (invoiceId != null ? invoiceId.hashCode() : 0);
        result = 31 * result + (paymentDate != null ? paymentDate.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (linkedInvoicePaymentId != null ? linkedInvoicePaymentId.hashCode() : 0);
        result = 31 * result + (paymentCookieId != null ? paymentCookieId.hashCode() : 0);
        result = 31 * result + (processedCurrency != null ? processedCurrency.hashCode() : 0);
        return result;
    }

    protected abstract Class getInvoicePaymentInternalEventClass();
}
