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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;

public class BusinessInvoice {
    private final UUID invoiceId;
    private final DateTime createdDate;

    private DateTime updatedDate;
    private String accountKey;
    private DateTime invoiceDate;
    private DateTime targetDate;
    private Currency currency;
    private BigDecimal balance;
    private BigDecimal amountPaid;
    private BigDecimal amountCharged;
    private BigDecimal amountCredited;

    public BusinessInvoice(final String accountKey, final BigDecimal amountCharged, final BigDecimal amountCredited,
                           final BigDecimal amountPaid, final BigDecimal balance, final DateTime createdDate,
                           final Currency currency, final DateTime invoiceDate, final UUID invoiceId,
                           final DateTime targetDate, final DateTime updatedDate) {
        this.accountKey = accountKey;
        this.amountCharged = amountCharged;
        this.amountCredited = amountCredited;
        this.amountPaid = amountPaid;
        this.balance = balance;
        this.createdDate = createdDate;
        this.currency = currency;
        this.invoiceDate = invoiceDate;
        this.invoiceId = invoiceId;
        this.targetDate = targetDate;
        this.updatedDate = updatedDate;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(final String accountKey) {
        this.accountKey = accountKey;
    }

    public BigDecimal getAmountCharged() {
        return amountCharged;
    }

    public void setAmountCharged(final BigDecimal amountCharged) {
        this.amountCharged = amountCharged;
    }

    public BigDecimal getAmountCredited() {
        return amountCredited;
    }

    public void setAmountCredited(final BigDecimal amountCredited) {
        this.amountCredited = amountCredited;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(final BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(final BigDecimal balance) {
        this.balance = balance;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public DateTime getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(final DateTime invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public DateTime getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(final DateTime targetDate) {
        this.targetDate = targetDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessInvoice");
        sb.append("{accountKey='").append(accountKey).append('\'');
        sb.append(", invoiceId=").append(invoiceId);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
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

        final BusinessInvoice that = (BusinessInvoice) o;

        if (accountKey != null ? !accountKey.equals(that.accountKey) : that.accountKey != null) {
            return false;
        }
        if (amountCharged != null ? !amountCharged.equals(that.amountCharged) : that.amountCharged != null) {
            return false;
        }
        if (amountCredited != null ? !amountCredited.equals(that.amountCredited) : that.amountCredited != null) {
            return false;
        }
        if (amountPaid != null ? !amountPaid.equals(that.amountPaid) : that.amountPaid != null) {
            return false;
        }
        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (currency != that.currency) {
            return false;
        }
        if (invoiceDate != null ? !invoiceDate.equals(that.invoiceDate) : that.invoiceDate != null) {
            return false;
        }
        if (invoiceId != null ? !invoiceId.equals(that.invoiceId) : that.invoiceId != null) {
            return false;
        }
        if (targetDate != null ? !targetDate.equals(that.targetDate) : that.targetDate != null) {
            return false;
        }
        if (updatedDate != null ? !updatedDate.equals(that.updatedDate) : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = invoiceId != null ? invoiceId.hashCode() : 0;
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
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
