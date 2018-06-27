/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.account.dao;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.dao.TableName;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntityModelDaoBase;
import org.killbill.billing.util.entity.dao.TimeZoneAwareEntity;

import com.google.common.base.MoreObjects;

import static org.killbill.billing.account.api.DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL;

public class AccountModelDao extends EntityModelDaoBase implements TimeZoneAwareEntity, EntityModelDao<Account> {

    private String externalKey;
    private String email;
    private String name;
    private Integer firstNameLength;
    private Currency currency;
    private UUID parentAccountId;
    private Boolean isPaymentDelegatedToParent;
    private int billingCycleDayLocal;
    private UUID paymentMethodId;
    private DateTime referenceTime;
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
    private String notes;
    private Boolean migrated;

    public AccountModelDao() { /* For the DAO mapper */ }

    public AccountModelDao(final UUID id, final DateTime createdDate, final DateTime updatedDate, final String externalKey,
                           final String email, final String name, final Integer firstNameLength, final Currency currency,
                           final UUID parentAccountId, final Boolean isPaymentDelegatedToParent,
                           final int billingCycleDayLocal, final UUID paymentMethodId, final DateTime referenceTime, final DateTimeZone timeZone,
                           final String locale, final String address1, final String address2, final String companyName,
                           final String city, final String stateOrProvince, final String country, final String postalCode,
                           final String phone, final String notes, final Boolean migrated) {
        super(id, createdDate, updatedDate);
        this.externalKey = MoreObjects.firstNonNull(externalKey, id.toString());
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.parentAccountId = parentAccountId;
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
        this.billingCycleDayLocal = billingCycleDayLocal;
        this.paymentMethodId = paymentMethodId;
        this.referenceTime = referenceTime;
        this.timeZone = MoreObjects.firstNonNull(timeZone, DateTimeZone.UTC);
        this.locale = locale;
        this.address1 = address1;
        this.address2 = address2;
        this.companyName = companyName;
        this.city = city;
        this.stateOrProvince = stateOrProvince;
        this.country = country;
        this.postalCode = postalCode;
        this.phone = phone;
        this.notes = notes;
        this.migrated = migrated;
    }

    private AccountModelDao(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate, final AccountData account) {
        this(id,
             createdDate,
             updatedDate,
             account.getExternalKey(),
             account.getEmail(),
             account.getName(),
             account.getFirstNameLength(),
             account.getCurrency(),
             account.getParentAccountId(),
             account.isPaymentDelegatedToParent(),
             MoreObjects.firstNonNull(account.getBillCycleDayLocal(), DEFAULT_BILLING_CYCLE_DAY_LOCAL),
             account.getPaymentMethodId(),
             account.getReferenceTime() != null ? account.getReferenceTime() : createdDate,
             account.getTimeZone(),
             account.getLocale(),
             account.getAddress1(),
             account.getAddress2(),
             account.getCompanyName(),
             account.getCity(),
             account.getStateOrProvince(),
             account.getCountry(),
             account.getPostalCode(),
             account.getPhone(),
             account.getNotes(),
             account.isMigrated());
    }


    public AccountModelDao(final UUID accountId, final AccountData account) {
        this(accountId, null, null, account);
    }


    public AccountModelDao(final AccountData account) {
        this(UUIDs.randomUUID(), null, null, account);
    }


    @Override
    public void setRecordId(final Long recordId) {
        super.setRecordId(recordId);
        // Invoked by the jDBI mapper when retrieving the record: while there is no account_record_id column,
        // populate the field manually for EntitySqlDaoWrapperInvocationHandler#populateCaches to populate the
        // ACCOUNT_RECORD_ID cache
        setAccountRecordId(recordId);
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    public void setFirstNameLength(final Integer firstNameLength) {
        this.firstNameLength = firstNameLength;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    public UUID getParentAccountId() {
        return parentAccountId;
    }

    public void setParentAccountId(final UUID parentAccountId) {
        this.parentAccountId = parentAccountId;
    }

    public Boolean getIsPaymentDelegatedToParent() {
        return isPaymentDelegatedToParent;
    }

    public void setIsPaymentDelegatedToParent(final Boolean paymentDelegatedToParent) {
        this.isPaymentDelegatedToParent = paymentDelegatedToParent;
    }

    public Integer getBillingCycleDayLocal() {
        return billingCycleDayLocal;
    }

    public void setBillingCycleDayLocal(final Integer billingCycleDayLocal) {
        this.billingCycleDayLocal = MoreObjects.firstNonNull(billingCycleDayLocal, DEFAULT_BILLING_CYCLE_DAY_LOCAL);
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    @Override
    public DateTime getReferenceTime() {
        return referenceTime;
    }

    public void setReferenceTime(final DateTime referenceTime) {
        this.referenceTime = referenceTime;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(final DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(final String locale) {
        this.locale = locale;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(final String address1) {
        this.address1 = address1;
    }

    public String getAddress2() {
        return address2;
    }

    public void setAddress2(final String address2) {
        this.address2 = address2;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(final String city) {
        this.city = city;
    }

    public String getStateOrProvince() {
        return stateOrProvince;
    }

    public void setStateOrProvince(final String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(final String country) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(final String phone) {
        this.phone = phone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(final String notes) {
        this.notes = notes;
    }

    public Boolean getMigrated() {
        return migrated;
    }

    public void setMigrated(final Boolean migrated) {
        this.migrated = migrated;
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
        sb.append(", parentAccountId=").append(parentAccountId);
        sb.append(", isPaymentDelegatedToParent=").append(isPaymentDelegatedToParent);
        sb.append(", billingCycleDayLocal=").append(billingCycleDayLocal);
        sb.append(", paymentMethodId=").append(paymentMethodId);
        sb.append(", referenceTime=").append(referenceTime);
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
        sb.append(", notes='").append(notes).append('\'');
        sb.append(", migrated=").append(migrated);
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
        if (parentAccountId != null ? !parentAccountId.equals(that.parentAccountId) : that.parentAccountId != null) {
            return false;
        }
        if (isPaymentDelegatedToParent != null ? !isPaymentDelegatedToParent.equals(that.isPaymentDelegatedToParent) : that.isPaymentDelegatedToParent != null) {
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
        if (notes != null ? !notes.equals(that.notes) : that.notes != null) {
            return false;
        }
        if (postalCode != null ? !postalCode.equals(that.postalCode) : that.postalCode != null) {
            return false;
        }
        if (stateOrProvince != null ? !stateOrProvince.equals(that.stateOrProvince) : that.stateOrProvince != null) {
            return false;
        }
        if (referenceTime != null ? referenceTime.compareTo(that.referenceTime) != 0 : that.referenceTime != null) {
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
        result = 31 * result + (parentAccountId != null ? parentAccountId.hashCode() : 0);
        result = 31 * result + (isPaymentDelegatedToParent != null ? isPaymentDelegatedToParent.hashCode() : 0);
        result = 31 * result + billingCycleDayLocal;
        result = 31 * result + (paymentMethodId != null ? paymentMethodId.hashCode() : 0);
        result = 31 * result + (referenceTime != null ? referenceTime.hashCode() : 0);
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
        result = 31 * result + (notes != null ? notes.hashCode() : 0);
        result = 31 * result + (migrated != null ? migrated.hashCode() : 0);
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
