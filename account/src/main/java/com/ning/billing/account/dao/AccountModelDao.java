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

package com.ning.billing.account.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class AccountModelDao extends EntityBase implements EntityModelDao<Account> {

    private String externalKey;
    private String email;
    private String name;
    private Integer firstNameLength;
    private Currency currency;
    private int billingCycleDayLocal;
    private int billingCycleDayUtc;
    private UUID paymentMethodId;
    private DateTimeZone timeZone;
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
    private Boolean isNotifiedForInvoices;

    public AccountModelDao() { /* For the DAO mapper */ }

    public AccountModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String externalKey,
                           final String email, final String name, final Integer firstNameLength, final Currency currency,
                           final int billingCycleDayLocal, final int billingCycleDayUtc, final UUID paymentMethodId, final DateTimeZone timeZone,
                           final String locale, final String address1, final String address2, final String companyName,
                           final String city, final String stateOrProvince, final String country, final String postalCode,
                           final String phone, final Boolean migrated, final Boolean notifiedForInvoices) {
        super(id, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.billingCycleDayLocal = billingCycleDayLocal;
        this.billingCycleDayUtc = billingCycleDayUtc;
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
        this.migrated = migrated;
        this.isNotifiedForInvoices = notifiedForInvoices;
    }

    public AccountModelDao(final UUID id, @Nullable final DateTime createdDate, final DateTime updatedDate, final AccountData account) {
        this(id, createdDate, updatedDate, account.getExternalKey(),
             account.getEmail(), account.getName(), account.getFirstNameLength(), account.getCurrency(), account.getBillCycleDay() == null ? 0 : account.getBillCycleDay().getDayOfMonthLocal(),
             account.getBillCycleDay() == null ? 0 : account.getBillCycleDay().getDayOfMonthUTC(), account.getPaymentMethodId(), account.getTimeZone(), account.getLocale(), account.getAddress1(), account.getAddress2(),
             account.getCompanyName(), account.getCity(), account.getStateOrProvince(), account.getCountry(), account.getPostalCode(),
             account.getPhone(), account.isMigrated(), account.isNotifiedForInvoices());
    }

    public AccountModelDao(final UUID id, final AccountData account) {
        this(id, null, null, account);
    }

    public AccountModelDao(final AccountData account) {
        this(UUID.randomUUID(), account);
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    public Currency getCurrency() {
        return currency;
    }

    public int getBillingCycleDayLocal() {
        return billingCycleDayLocal;
    }

    public int getBillingCycleDayUtc() {
        return billingCycleDayUtc;
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public DateTimeZone getTimeZone() {
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

    // TODO Required for making the BindBeanFactory with Introspector work
    // see Introspector line 571; they look at public method.
    public Boolean getIsNotifiedForInvoices() {
        return isNotifiedForInvoices;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AccountModelDao");
        sb.append("{externalKey='").append(externalKey).append('\'');
        sb.append(", email='").append(email).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", firstNameLength=").append(firstNameLength);
        sb.append(", currency=").append(currency);
        sb.append(", billingCycleDayLocal=").append(billingCycleDayLocal);
        sb.append(", billingCycleDayUTC=").append(billingCycleDayUtc);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", timeZone=").append(timeZone);
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
        sb.append(", isNotifiedForInvoices=").append(isNotifiedForInvoices);
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

        final AccountModelDao that = (AccountModelDao) o;

        if (billingCycleDayLocal != that.billingCycleDayLocal) {
            return false;
        }
        if (billingCycleDayUtc != that.billingCycleDayUtc) {
            return false;
        }
        if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
            return false;
        }
        if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
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
        if (currency != that.currency) {
            return false;
        }
        if (email != null ? !email.equals(that.email) : that.email != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (firstNameLength != null ? !firstNameLength.equals(that.firstNameLength) : that.firstNameLength != null) {
            return false;
        }
        if (migrated != null ? !migrated.equals(that.migrated) : that.migrated != null) {
            return false;
        }
        if (isNotifiedForInvoices != null ? !isNotifiedForInvoices.equals(that.isNotifiedForInvoices) : that.isNotifiedForInvoices != null) {
            return false;
        }
        if (locale != null ? !locale.equals(that.locale) : that.locale != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
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

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (firstNameLength != null ? firstNameLength.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + billingCycleDayLocal;
        result = 31 * result + billingCycleDayUtc;
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
        result = 31 * result + (isNotifiedForInvoices != null ? isNotifiedForInvoices.hashCode() : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.ACCOUNT;
    }

    @Override
    public TableName getHistoryTableName() {
        return TableName.ACCOUNT_HISTORY;
    }
}
