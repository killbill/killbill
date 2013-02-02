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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;

import org.joda.time.LocalDate;

import com.ning.billing.analytics.model.BusinessAccountModelDao;
import com.ning.billing.util.entity.EntityBase;

public class DefaultBusinessAccount extends EntityBase implements BusinessAccount {

    private final String externalKey;
    private final String name;
    private final String currency;
    private final BigDecimal balance;
    private final LocalDate lastInvoiceDate;
    private final BigDecimal totalInvoiceBalance;
    private final String lastPaymentStatus;
    private final String defaultPaymentMethodType;
    private final String defaultCreditCardType;
    private final String defaultBillingAddressCountry;

    public DefaultBusinessAccount(final BusinessAccountModelDao businessAccountModelDao) {
        this.externalKey = businessAccountModelDao.getKey();
        this.name = businessAccountModelDao.getName();
        this.currency = businessAccountModelDao.getCurrency();
        this.balance = businessAccountModelDao.getBalance();
        this.lastInvoiceDate = businessAccountModelDao.getLastInvoiceDate();
        this.totalInvoiceBalance = businessAccountModelDao.getTotalInvoiceBalance();
        this.lastPaymentStatus = businessAccountModelDao.getLastPaymentStatus();
        this.defaultPaymentMethodType = businessAccountModelDao.getPaymentMethod();
        this.defaultCreditCardType = businessAccountModelDao.getCreditCardType();
        this.defaultBillingAddressCountry = businessAccountModelDao.getBillingAddressCountry();
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getCurrency() {
        return currency;
    }

    @Override
    public BigDecimal getBalance() {
        return balance;
    }

    @Override
    public LocalDate getLastInvoiceDate() {
        return lastInvoiceDate;
    }

    @Override
    public BigDecimal getTotalInvoiceBalance() {
        return totalInvoiceBalance;
    }

    @Override
    public String getLastPaymentStatus() {
        return lastPaymentStatus;
    }

    @Override
    public String getDefaultPaymentMethodType() {
        return defaultPaymentMethodType;
    }

    @Override
    public String getDefaultCreditCardType() {
        return defaultCreditCardType;
    }

    @Override
    public String getDefaultBillingAddressCountry() {
        return defaultBillingAddressCountry;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("DefaultBusinessAccount");
        sb.append("{externalKey='").append(externalKey).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", balance=").append(balance);
        sb.append(", lastInvoiceDate=").append(lastInvoiceDate);
        sb.append(", totalInvoiceBalance=").append(totalInvoiceBalance);
        sb.append(", lastPaymentStatus='").append(lastPaymentStatus).append('\'');
        sb.append(", defaultPaymentMethodType='").append(defaultPaymentMethodType).append('\'');
        sb.append(", defaultCreditCardType='").append(defaultCreditCardType).append('\'');
        sb.append(", defaultBillingAddressCountry='").append(defaultBillingAddressCountry).append('\'');
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

        final DefaultBusinessAccount that = (DefaultBusinessAccount) o;

        if (balance != null ? !balance.equals(that.balance) : that.balance != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (defaultBillingAddressCountry != null ? !defaultBillingAddressCountry.equals(that.defaultBillingAddressCountry) : that.defaultBillingAddressCountry != null) {
            return false;
        }
        if (defaultCreditCardType != null ? !defaultCreditCardType.equals(that.defaultCreditCardType) : that.defaultCreditCardType != null) {
            return false;
        }
        if (defaultPaymentMethodType != null ? !defaultPaymentMethodType.equals(that.defaultPaymentMethodType) : that.defaultPaymentMethodType != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (lastInvoiceDate != null ? !lastInvoiceDate.equals(that.lastInvoiceDate) : that.lastInvoiceDate != null) {
            return false;
        }
        if (lastPaymentStatus != null ? !lastPaymentStatus.equals(that.lastPaymentStatus) : that.lastPaymentStatus != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (totalInvoiceBalance != null ? !totalInvoiceBalance.equals(that.totalInvoiceBalance) : that.totalInvoiceBalance != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = externalKey != null ? externalKey.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (lastInvoiceDate != null ? lastInvoiceDate.hashCode() : 0);
        result = 31 * result + (totalInvoiceBalance != null ? totalInvoiceBalance.hashCode() : 0);
        result = 31 * result + (lastPaymentStatus != null ? lastPaymentStatus.hashCode() : 0);
        result = 31 * result + (defaultPaymentMethodType != null ? defaultPaymentMethodType.hashCode() : 0);
        result = 31 * result + (defaultCreditCardType != null ? defaultCreditCardType.hashCode() : 0);
        result = 31 * result + (defaultBillingAddressCountry != null ? defaultBillingAddressCountry.hashCode() : 0);
        return result;
    }
}
