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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.util.audit.AuditLog;

public class BusinessAccountModelDao extends BusinessModelDaoBase {

    private static final String ACCOUNTS_TABLE_NAME = "bac";

    private String email;
    private Integer firstNameLength;
    private String currency;
    private Integer billingCycleDayLocal;
    private UUID paymentMethodId;
    private String timeZone;
    private String locale;
    private String address1;
    private String address2;
    private String companyName;
    private String city;
    private String stateOrProvince;
    private String country;
    private String postalCode;
    private String phone;
    private Boolean migrated;
    private Boolean notifiedForInvoices;
    private DateTime updatedDate;
    private BigDecimal balance;
    private LocalDate lastInvoiceDate;
    private DateTime lastPaymentDate;
    private String lastPaymentStatus;

    public BusinessAccountModelDao() { /* When reading from the database */ }

    public BusinessAccountModelDao(final String email,
                                   final Integer firstNameLength,
                                   final String currency,
                                   final Integer billingCycleDayLocal,
                                   final UUID paymentMethodId,
                                   final String timeZone,
                                   final String locale,
                                   final String address1,
                                   final String address2,
                                   final String companyName,
                                   final String city,
                                   final String stateOrProvince,
                                   final String country,
                                   final String postalCode,
                                   final String phone,
                                   final Boolean isMigrated,
                                   final Boolean notifiedForInvoices,
                                   final DateTime updatedDate,
                                   final BigDecimal balance,
                                   final LocalDate lastInvoiceDate,
                                   final DateTime lastPaymentDate,
                                   final String lastPaymentStatus,
                                   final DateTime createdDate,
                                   final String createdBy,
                                   final String createdReasonCode,
                                   final String createdComments,
                                   final UUID accountId,
                                   final String accountName,
                                   final String accountExternalKey,
                                   final Long accountRecordId,
                                   final Long tenantRecordId,
                                   @Nullable final ReportGroup reportGroup) {
        super(createdDate,
              createdBy,
              createdReasonCode,
              createdComments,
              accountId,
              accountName,
              accountExternalKey,
              accountRecordId,
              tenantRecordId,
              reportGroup);
        this.email = email;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.billingCycleDayLocal = billingCycleDayLocal;
        this.paymentMethodId = paymentMethodId;
        this.timeZone = timeZone;
        this.locale = locale;
        this.address1 = address1;
        this.address2 = address2;
        this.companyName = companyName;
        this.city = city;
        this.stateOrProvince = stateOrProvince;
        this.country = country;
        this.postalCode = postalCode;
        this.phone = phone;
        this.migrated = isMigrated;
        this.notifiedForInvoices = notifiedForInvoices;
        this.updatedDate = updatedDate;
        this.balance = balance;
        this.lastInvoiceDate = lastInvoiceDate;
        this.lastPaymentDate = lastPaymentDate;
        this.lastPaymentStatus = lastPaymentStatus;
    }

    public BusinessAccountModelDao(final Account account,
                                   final Long accountRecordId,
                                   final BigDecimal balance,
                                   @Nullable final Invoice lastInvoice,
                                   @Nullable final Payment lastPayment,
                                   final AuditLog creationAuditLog,
                                   final Long tenantRecordId,
                                   @Nullable final ReportGroup reportGroup) {
        this(account.getEmail(),
             account.getFirstNameLength(),
             account.getCurrency() == null ? null : account.getCurrency().toString(),
             account.getBillCycleDayLocal(),
             account.getPaymentMethodId(),
             account.getTimeZone() == null ? null : account.getTimeZone().toString(),
             account.getLocale(),
             account.getAddress1(),
             account.getAddress2(),
             account.getCompanyName(),
             account.getCity(),
             account.getStateOrProvince(),
             account.getCountry(),
             account.getPostalCode(),
             account.getPhone(),
             account.isMigrated(),
             account.isNotifiedForInvoices(),
             account.getUpdatedDate(),
             balance,
             lastInvoice == null ? null : lastInvoice.getInvoiceDate(),
             lastPayment == null ? null : lastPayment.getEffectiveDate(),
             lastPayment == null ? null : lastPayment.getPaymentStatus().toString(),
             account.getCreatedDate(),
             creationAuditLog.getUserName(),
             creationAuditLog.getReasonCode(),
             creationAuditLog.getComment(),
             account.getId(),
             account.getName(),
             account.getExternalKey(),
             accountRecordId,
             tenantRecordId,
             reportGroup);
    }

    @Override
    public String getTableName() {
        return ACCOUNTS_TABLE_NAME;
    }

    public String getEmail() {
        return email;
    }

    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getBillingCycleDayLocal() {
        return billingCycleDayLocal;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getLocale() {
        return locale;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCity() {
        return city;
    }

    public String getStateOrProvince() {
        return stateOrProvince;
    }

    public String getCountry() {
        return country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public Boolean getMigrated() {
        return migrated;
    }

    public Boolean getNotifiedForInvoices() {
        return notifiedForInvoices;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDate getLastInvoiceDate() {
        return lastInvoiceDate;
    }

    public DateTime getLastPaymentDate() {
        return lastPaymentDate;
    }

    public String getLastPaymentStatus() {
        return lastPaymentStatus;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("BusinessAccountModelDao");
        sb.append("{email='").append(email).append('\'');
        sb.append(", firstNameLength=").append(firstNameLength);
        sb.append(", currency='").append(currency).append('\'');
        sb.append(", billingCycleDayLocal=").append(billingCycleDayLocal);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", timeZone='").append(timeZone).append('\'');
        sb.append(", locale='").append(locale).append('\'');
        sb.append(", address1='").append(address1).append('\'');
        sb.append(", address2='").append(address2).append('\'');
        sb.append(", companyName='").append(companyName).append('\'');
        sb.append(", city='").append(city).append('\'');
        sb.append(", stateOrProvince='").append(stateOrProvince).append('\'');
        sb.append(", country='").append(country).append('\'');
        sb.append(", postalCode='").append(postalCode).append('\'');
        sb.append(", phone='").append(phone).append('\'');
        sb.append(", migrated=").append(migrated);
        sb.append(", notifiedForInvoices=").append(notifiedForInvoices);
        sb.append(", updatedDate=").append(updatedDate);
        sb.append(", balance=").append(balance);
        sb.append(", lastInvoiceDate=").append(lastInvoiceDate);
        sb.append(", lastPaymentDate=").append(lastPaymentDate);
        sb.append(", lastPaymentStatus='").append(lastPaymentStatus).append('\'');
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

        final BusinessAccountModelDao that = (BusinessAccountModelDao) o;

        if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
            return false;
        }
        if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
            return false;
        }
        if (balance != null ? balance.compareTo(that.balance) != 0 : that.balance != null) {
            return false;
        }
        if (billingCycleDayLocal != null ? !billingCycleDayLocal.equals(that.billingCycleDayLocal) : that.billingCycleDayLocal != null) {
            return false;
        }
        if (city != null ? !city.equals(that.city) : that.city != null) {
            return false;
        }
        if (companyName != null ? !companyName.equals(that.companyName) : that.companyName != null) {
            return false;
        }
        if (country != null ? !country.equals(that.country) : that.country != null) {
            return false;
        }
        if (currency != null ? !currency.equals(that.currency) : that.currency != null) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (firstNameLength != null ? !firstNameLength.equals(that.firstNameLength) : that.firstNameLength != null) {
            return false;
        }
        if (lastInvoiceDate != null ? !lastInvoiceDate.equals(that.lastInvoiceDate) : that.lastInvoiceDate != null) {
            return false;
        }
        if (lastPaymentDate != null ? !lastPaymentDate.equals(that.lastPaymentDate) : that.lastPaymentDate != null) {
            return false;
        }
        if (lastPaymentStatus != null ? !lastPaymentStatus.equals(that.lastPaymentStatus) : that.lastPaymentStatus != null) {
            return false;
        }
        if (locale != null ? !locale.equals(that.locale) : that.locale != null) {
            return false;
        }
        if (migrated != null ? !migrated.equals(that.migrated) : that.migrated != null) {
            return false;
        }
        if (notifiedForInvoices != null ? !notifiedForInvoices.equals(that.notifiedForInvoices) : that.notifiedForInvoices != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (phone != null ? !phone.equals(that.phone) : that.phone != null) {
            return false;
        }
        if (postalCode != null ? !postalCode.equals(that.postalCode) : that.postalCode != null) {
            return false;
        }
        if (stateOrProvince != null ? !stateOrProvince.equals(that.stateOrProvince) : that.stateOrProvince != null) {
            return false;
        }
        if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
            return false;
        }
        if (updatedDate != null ? (updatedDate.compareTo(that.updatedDate) != 0) : that.updatedDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (firstNameLength != null ? firstNameLength.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (billingCycleDayLocal != null ? billingCycleDayLocal.hashCode() : 0);
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (timeZone != null ? timeZone.hashCode() : 0);
        result = 31 * result + (locale != null ? locale.hashCode() : 0);
        result = 31 * result + (address1 != null ? address1.hashCode() : 0);
        result = 31 * result + (address2 != null ? address2.hashCode() : 0);
        result = 31 * result + (companyName != null ? companyName.hashCode() : 0);
        result = 31 * result + (city != null ? city.hashCode() : 0);
        result = 31 * result + (stateOrProvince != null ? stateOrProvince.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        result = 31 * result + (postalCode != null ? postalCode.hashCode() : 0);
        result = 31 * result + (phone != null ? phone.hashCode() : 0);
        result = 31 * result + (migrated != null ? migrated.hashCode() : 0);
        result = 31 * result + (notifiedForInvoices != null ? notifiedForInvoices.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        result = 31 * result + (balance != null ? balance.hashCode() : 0);
        result = 31 * result + (lastInvoiceDate != null ? lastInvoiceDate.hashCode() : 0);
        result = 31 * result + (lastPaymentDate != null ? lastPaymentDate.hashCode() : 0);
        result = 31 * result + (lastPaymentStatus != null ? lastPaymentStatus.hashCode() : 0);
        return result;
    }
}
