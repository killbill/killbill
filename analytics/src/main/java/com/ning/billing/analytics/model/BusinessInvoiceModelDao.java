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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.util.entity.EntityBase;

public class BusinessInvoiceModelDao extends EntityBase {

    private final UUID invoiceId;
    private final Integer invoiceNumber;
    private final UUID accountId;
    private final String accountKey;
    private final LocalDate invoiceDate;
    private final LocalDate targetDate;
    private final Currency currency;
    private final BigDecimal balance;
    private final BigDecimal amountPaid;
    private final BigDecimal amountCharged;
    private final BigDecimal amountCredited;

    public BusinessInvoiceModelDao(final UUID accountId, final String accountKey, final BigDecimal amountCharged, final BigDecimal amountCredited,
                                   final BigDecimal amountPaid, final BigDecimal balance, final DateTime createdDate,
                                   final Currency currency, final LocalDate invoiceDate, final UUID invoiceId, final Integer invoiceNumber,
                                   final LocalDate targetDate, final DateTime updatedDate) {
        super(invoiceId, createdDate, updatedDate);
        this.accountId = accountId;
        this.accountKey = accountKey;
        this.amountCharged = amountCharged;
        this.amountCredited = amountCredited;
        this.amountPaid = amountPaid;
        this.balance = balance;
        this.currency = currency;
        this.invoiceDate = invoiceDate;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.targetDate = targetDate;
    }

    public BusinessInvoiceModelDao(final String accountKey, final Invoice invoice) {
        this(invoice.getAccountId(), accountKey, invoice.getChargedAmount(), invoice.getCreditedAmount(), invoice.getPaidAmount(), invoice.getBalance(),
             invoice.getCreatedDate(), invoice.getCurrency(), invoice.getInvoiceDate(), invoice.getId(), invoice.getInvoiceNumber(), invoice.getTargetDate(),
             invoice.getUpdatedDate());
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public Integer getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoiceModelDao");
        sb.append("{accountId=").append(accountId);
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", invoiceNumber=").append(invoiceNumber);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", accountKey='").append(accountKey).append('\'');
        sb.append(", invoiceDate=").append(invoiceDate);
        sb.append(", targetDate=").append(targetDate);
        sb.append(", currency=").append(currency);
        sb.append(", balance=").append(balance);
        sb.append(", amountPaid=").append(amountPaid);
        sb.append(", amountCharged=").append(amountCharged);
        sb.append(", amountCredited=").append(amountCredited);
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

        final BusinessInvoiceModelDao that = (BusinessInvoiceModelDao) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (amountCharged != null ? Rounder.round(amountCharged) != Rounder.round(that.amountCharged) : that.amountCharged != null) {
            return false;
        }
        if (amountCredited != null ? Rounder.round(amountCredited) != Rounder.round(that.amountCredited) : that.amountCredited != null) {
            return false;
        }
        if (amountPaid != null ? Rounder.round(amountPaid) != Rounder.round(that.amountPaid) : that.amountPaid != null) {
            return false;
        }
        if (balance != null ? Rounder.round(balance) != Rounder.round(that.balance) : that.balance != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceDate != null ? invoiceDate.compareTo(that.invoiceDate) != 0 : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (invoiceNumber != null ? !invoiceNumber.equals(that.invoiceNumber) : that.invoiceNumber != null) {
            return false;
        }
        if (targetDate != null ? targetDate.compareTo(that.targetDate) != 0 : that.targetDate != null) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (invoiceNumber != null ? invoiceNumber.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (accountKey != null ? accountKey.hashCode() : 0);
        result = 31 * result + (invoiceDate != null ? invoiceDate.hashCode() : 0);
        result = 31 * result + (targetDate != null ? targetDate.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (amountPaid != null ? amountPaid.hashCode() : 0);
        result = 31 * result + (amountCharged != null ? amountCharged.hashCode() : 0);
        result = 31 * result + (amountCredited != null ? amountCredited.hashCode() : 0);
        return result;
    }
}
