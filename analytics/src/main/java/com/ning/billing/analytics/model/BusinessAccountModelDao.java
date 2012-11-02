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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.analytics.utils.Rounder;
import com.ning.billing.util.entity.EntityBase;

import com.google.common.base.Objects;

public class BusinessAccountModelDao extends EntityBase {

    private final UUID accountId;
    private String key;
    private String name;
    private BigDecimal balance;
    private LocalDate lastInvoiceDate;
    private BigDecimal totalInvoiceBalance;
    private String lastPaymentStatus;
    private String paymentMethod;
    private String creditCardType;
    private String billingAddressCountry;
    private String currency;

    public BusinessAccountModelDao(final UUID accountId, final String key, final String name, final BigDecimal balance,
                                   final LocalDate lastInvoiceDate, final BigDecimal totalInvoiceBalance, final String lastPaymentStatus,
                                   final String paymentMethod, final String creditCardType, final String billingAddressCountry,
                                   final String currency, final DateTime createdDt, final DateTime updatedDt) {
        super(accountId, createdDt, updatedDt);
        this.accountId = accountId;
        this.key = key;
        this.balance = balance;
        this.billingAddressCountry = billingAddressCountry;
        this.creditCardType = creditCardType;
        this.lastInvoiceDate = lastInvoiceDate;
        this.lastPaymentStatus = lastPaymentStatus;
        this.name = name;
        this.paymentMethod = paymentMethod;
        this.totalInvoiceBalance = totalInvoiceBalance;
        this.currency = currency;
    }

    public BusinessAccountModelDao(final Account account) {
        super(account.getId(), account.getCreatedDate(), account.getUpdatedDate());
        this.accountId = account.getId();
        this.name = account.getName();
        this.key = account.getExternalKey();
        if (account.getCurrency() != null) {
            this.currency = account.getCurrency().toString();
        }
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public BigDecimal getBalance() {
        return Objects.firstNonNull(balance, BigDecimal.ZERO);
    }

    public Double getRoundedBalance() {
        return Rounder.round(balance);
    }

    public void setBalance(final BigDecimal balance) {
        this.balance = balance;
    }

    public String getBillingAddressCountry() {
        return billingAddressCountry;
    }

    public void setBillingAddressCountry(final String billingAddressCountry) {
        this.billingAddressCountry = billingAddressCountry;
    }

    public String getCreditCardType() {
        return creditCardType;
    }

    public void setCreditCardType(final String creditCardType) {
        this.creditCardType = creditCardType;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public LocalDate getLastInvoiceDate() {
        return lastInvoiceDate;
    }

    public void setLastInvoiceDate(final LocalDate lastInvoiceDate) {
        this.lastInvoiceDate = lastInvoiceDate;
    }

    public String getLastPaymentStatus() {
        return lastPaymentStatus;
    }

    public void setLastPaymentStatus(final String lastPaymentStatus) {
        this.lastPaymentStatus = lastPaymentStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(final String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotalInvoiceBalance() {
        return Objects.firstNonNull(totalInvoiceBalance, BigDecimal.ZERO);
    }

    public Double getRoundedTotalInvoiceBalance() {
        return Rounder.round(totalInvoiceBalance);
    }

    public void setTotalInvoiceBalance(final BigDecimal totalInvoiceBalance) {
        this.totalInvoiceBalance = totalInvoiceBalance;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessAccountModelDao");
        sb.append("{balance=").append(balance);
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", accountId='").append(accountId).append('\'');
        sb.append(", key='").append(key).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", lastInvoiceDate=").append(lastInvoiceDate);
        sb.append(", totalInvoiceBalance=").append(totalInvoiceBalance);
        sb.append(", lastPaymentStatus='").append(lastPaymentStatus).append('\'');
        sb.append(", paymentMethod='").append(paymentMethod).append('\'');
        sb.append(", creditCardType='").append(creditCardType).append('\'');
        sb.append(", billingAddressCountry='").append(billingAddressCountry).append('\'');
        sb.append(", currency='").append(currency).append('\'');
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

        final BusinessAccountModelDao that = (BusinessAccountModelDao) o;

        if (balance == null ? that.balance != null : balance.compareTo(that.balance) != 0) {
            return false;
        }
        if (billingAddressCountry != null ? !billingAddressCountry.equals(that.billingAddressCountry) : that.billingAddressCountry != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (creditCardType != null ? !creditCardType.equals(that.creditCardType) : that.creditCardType != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (key != null ? !key.equals(that.key) : that.key != null) {
            return false;
        }
        if (lastInvoiceDate != null ? lastInvoiceDate.compareTo(that.lastInvoiceDate) != 0 : that.lastInvoiceDate != null) {
            return false;
        }
        if (lastPaymentStatus != null ? !lastPaymentStatus.equals(that.lastPaymentStatus) : that.lastPaymentStatus != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (paymentMethod != null ? !paymentMethod.equals(that.paymentMethod) : that.paymentMethod != null) {
            return false;
        }
        if (totalInvoiceBalance == null ? that.totalInvoiceBalance != null : totalInvoiceBalance.compareTo(that.totalInvoiceBalance) != 0) {
            return false;
        }
        if (updatedDate != null ? updatedDate.compareTo(that.updatedDate) != 0 : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = createdDate != null ? createdDate.hashCode() : 0;
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (lastInvoiceDate != null ? lastInvoiceDate.hashCode() : 0);
        result = 31 * result + (totalInvoiceBalance != null ? totalInvoiceBalance.hashCode() : 0);
        result = 31 * result + (lastPaymentStatus != null ? lastPaymentStatus.hashCode() : 0);
        result = 31 * result + (paymentMethod != null ? paymentMethod.hashCode() : 0);
        result = 31 * result + (creditCardType != null ? creditCardType.hashCode() : 0);
        result = 31 * result + (billingAddressCountry != null ? billingAddressCountry.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        return result;
    }
}
