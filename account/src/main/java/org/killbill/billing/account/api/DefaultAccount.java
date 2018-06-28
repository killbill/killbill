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

package org.killbill.billing.account.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.account.AccountDateTimeUtils;

import static org.killbill.billing.account.api.DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL;

public class DefaultAccount extends EntityBase implements Account {

    private final String externalKey;
    private final String email;
    private final String name;
    private final Integer firstNameLength;
    private final Currency currency;
    private final UUID parentAccountId;
    private final Boolean isPaymentDelegatedToParent;
    private final Integer billCycleDayLocal;
    private final UUID paymentMethodId;
    private final DateTime referenceTime;
    private final DateTimeZone timeZone;
    private final String locale;
    private final String address1;
    private final String address2;
    private final String companyName;
    private final String city;
    private final String stateOrProvince;
    private final String country;
    private final String postalCode;
    private final String phone;
    private final String notes;
    private final Boolean isMigrated;

    /**
     * This call is used to update an existing account
     *
     * @param id   UUID id of the existing account to update
     * @param data AccountData new data for the existing account
     */
    public DefaultAccount(final UUID id, final AccountData data) {
        this(id,
             data.getExternalKey(),
             data.getEmail(),
             data.getName(),
             data.getFirstNameLength(),
             data.getCurrency(),
             data.getParentAccountId(),
             data.isPaymentDelegatedToParent(),
             data.getBillCycleDayLocal(),
             data.getPaymentMethodId(),
             data.getReferenceTime(),
             data.getTimeZone(),
             data.getLocale(),
             data.getAddress1(),
             data.getAddress2(),
             data.getCompanyName(),
             data.getCity(),
             data.getStateOrProvince(),
             data.getCountry(),
             data.getPostalCode(),
             data.getPhone(),
             data.getNotes(),
             data.isMigrated());
    }

    // This call is used for testing and update from an existing account
    public DefaultAccount(final UUID id, final String externalKey, final String email,
                          final String name, final Integer firstNameLength, final Currency currency,
                          final UUID parentAccountId, final Boolean isPaymentDelegatedToParent,
                          final Integer billCycleDayLocal, final UUID paymentMethodId,
                          final DateTime referenceTime, final DateTimeZone timeZone, final String locale,
                          final String address1, final String address2, final String companyName,
                          final String city, final String stateOrProvince, final String country,
                          final String postalCode, final String phone, final String notes,
                          final Boolean isMigrated) {
        this(id,
             null,
             null,
             externalKey,
             email,
             name,
             firstNameLength,
             currency,
             parentAccountId,
             isPaymentDelegatedToParent,
             billCycleDayLocal,
             paymentMethodId,
             referenceTime,
             timeZone,
             locale,
             address1,
             address2,
             companyName,
             city,
             stateOrProvince,
             country,
             postalCode,
             phone,
             notes,
             isMigrated);
    }

    public DefaultAccount(final UUID id, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                          final String externalKey, final String email,
                          final String name, final Integer firstNameLength, final Currency currency,
                          final UUID parentAccountId, final Boolean isPaymentDelegatedToParent,
                          final Integer billCycleDayLocal, final UUID paymentMethodId,
                          final DateTime referenceTime, final DateTimeZone timeZone, final String locale,
                          final String address1, final String address2, final String companyName,
                          final String city, final String stateOrProvince, final String country,
                          final String postalCode, final String phone, final String notes,
                          final Boolean isMigrated) {
        super(id, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.parentAccountId = parentAccountId;
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent != null ? isPaymentDelegatedToParent : false;
        this.billCycleDayLocal = billCycleDayLocal == null ? DEFAULT_BILLING_CYCLE_DAY_LOCAL : billCycleDayLocal;
        this.paymentMethodId = paymentMethodId;
        this.referenceTime = referenceTime;
        this.timeZone = timeZone;
        this.locale = locale;
        this.address1 = address1;
        this.address2 = address2;
        this.companyName = companyName;
        this.city = city;
        this.stateOrProvince = stateOrProvince;
        this.postalCode = postalCode;
        this.country = country;
        this.phone = phone;
        this.notes = notes;
        this.isMigrated = isMigrated;
    }

    public DefaultAccount(final AccountModelDao accountModelDao) {
        this(accountModelDao.getId(),
             accountModelDao.getCreatedDate(),
             accountModelDao.getUpdatedDate(),
             accountModelDao.getExternalKey(),
             accountModelDao.getEmail(),
             accountModelDao.getName(),
             accountModelDao.getFirstNameLength(),
             accountModelDao.getCurrency(),
             accountModelDao.getParentAccountId(),
             accountModelDao.getIsPaymentDelegatedToParent(),
             accountModelDao.getBillingCycleDayLocal(),
             accountModelDao.getPaymentMethodId(),
             accountModelDao.getReferenceTime(),
             accountModelDao.getTimeZone(),
             accountModelDao.getLocale(),
             accountModelDao.getAddress1(),
             accountModelDao.getAddress2(),
             accountModelDao.getCompanyName(),
             accountModelDao.getCity(),
             accountModelDao.getStateOrProvince(),
             accountModelDao.getCountry(),
             accountModelDao.getPostalCode(),
             accountModelDao.getPhone(),
             accountModelDao.getNotes(),
             accountModelDao.getMigrated());
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
    public String getEmail() {
        return email;
    }

    @Override
    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public UUID getParentAccountId() {
        return parentAccountId;
    }

    @Override
    public Boolean isPaymentDelegatedToParent() {
        return isPaymentDelegatedToParent;
    }

    @Override
    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    @Override
    public UUID getPaymentMethodId() {
        // Null if non specified
        return paymentMethodId;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public String getLocale() {
        return locale;
    }

    @Override
    public String getAddress1() {
        return address1;
    }

    @Override
    public String getAddress2() {
        return address2;
    }

    @Override
    public String getCompanyName() {
        return companyName;
    }

    @Override
    public String getCity() {
        return city;
    }

    @Override
    public String getStateOrProvince() {
        return stateOrProvince;
    }

    @Override
    public String getPostalCode() {
        return postalCode;
    }

    @Override
    public String getCountry() {
        return country;
    }

    @Override
    public Boolean isMigrated() {
        return isMigrated;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    @Override
    public MutableAccountData toMutableAccountData() {
        return new DefaultMutableAccountData(this);
    }

    /**
     * @param currentAccount existing account data
     * @return merged account data
     */
    @Override
    public Account mergeWithDelegate(final Account currentAccount) {
        final DefaultMutableAccountData accountData = new DefaultMutableAccountData(this);

        validateAccountUpdateInput(currentAccount, false);

        accountData.setExternalKey(currentAccount.getExternalKey());

        accountData.setCurrency(currentAccount.getCurrency());

        if (currentAccount.getBillCycleDayLocal() == DEFAULT_BILLING_CYCLE_DAY_LOCAL && // There is *not* already a BCD set
            billCycleDayLocal != null && // and the value proposed is not null
            billCycleDayLocal != DEFAULT_BILLING_CYCLE_DAY_LOCAL) {  // and the proposed date is not 0
            accountData.setBillCycleDayLocal(billCycleDayLocal);
        } else {
            accountData.setBillCycleDayLocal(currentAccount.getBillCycleDayLocal());
        }

        // Set all updatable fields with the new values if non null, otherwise defaults to the current values
        accountData.setEmail(email != null ? email : currentAccount.getEmail());
        accountData.setName(name != null ? name : currentAccount.getName());
        final Integer firstNameLength = this.firstNameLength != null ? this.firstNameLength : currentAccount.getFirstNameLength();
        if (firstNameLength != null) {
            accountData.setFirstNameLength(firstNameLength);
        }
        accountData.setPaymentMethodId(paymentMethodId != null ? paymentMethodId : currentAccount.getPaymentMethodId());
        accountData.setTimeZone(timeZone != null ? timeZone : currentAccount.getTimeZone());
        accountData.setLocale(locale != null ? locale : currentAccount.getLocale());
        accountData.setAddress1(address1 != null ? address1 : currentAccount.getAddress1());
        accountData.setAddress2(address2 != null ? address2 : currentAccount.getAddress2());
        accountData.setCompanyName(companyName != null ? companyName : currentAccount.getCompanyName());
        accountData.setCity(city != null ? city : currentAccount.getCity());
        accountData.setStateOrProvince(stateOrProvince != null ? stateOrProvince : currentAccount.getStateOrProvince());
        accountData.setCountry(country != null ? country : currentAccount.getCountry());
        accountData.setPostalCode(postalCode != null ? postalCode : currentAccount.getPostalCode());
        accountData.setPhone(phone != null ? phone : currentAccount.getPhone());
        accountData.setNotes(notes != null ? notes : currentAccount.getNotes());
        accountData.setParentAccountId(parentAccountId != null ? parentAccountId : currentAccount.getParentAccountId());
        accountData.setIsPaymentDelegatedToParent(isPaymentDelegatedToParent != null ? isPaymentDelegatedToParent : currentAccount.isPaymentDelegatedToParent());
        final Boolean isMigrated = this.isMigrated != null ? this.isMigrated : currentAccount.isMigrated();
        if (isMigrated != null) {
            accountData.setIsMigrated(isMigrated);
        }

        return new DefaultAccount(currentAccount.getId(), accountData);
    }

    @Override
    public DateTimeZone getFixedOffsetTimeZone() {
        return AccountDateTimeUtils.getFixedOffsetTimeZone(this);
    }

    @Override
    public DateTime getReferenceTime() {
        return referenceTime;
    }

    @Override
    public String toString() {
        return "DefaultAccount [externalKey=" + externalKey +
               ", email=" + email +
               ", name=" + name +
               ", firstNameLength=" + firstNameLength +
               ", phone=" + phone +
               ", currency=" + currency +
               ", parentAccountId=" + parentAccountId +
               ", isPaymentDelegatedToParent=" + isPaymentDelegatedToParent +
               ", billCycleDayLocal=" + billCycleDayLocal +
               ", paymentMethodId=" + paymentMethodId +
               ", referenceTime=" + referenceTime +
               ", timezone=" + timeZone +
               ", locale=" + locale +
               ", address1=" + address1 +
               ", address2=" + address2 +
               ", companyName=" + companyName +
               ", city=" + city +
               ", stateOrProvince=" + stateOrProvince +
               ", postalCode=" + postalCode +
               ", country=" + country +
               ", notes=" + notes +
               "]";
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

        final DefaultAccount that = (DefaultAccount) o;

        if (billCycleDayLocal != null ? !billCycleDayLocal.equals(that.billCycleDayLocal) : that.billCycleDayLocal != null) {
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
        if (isMigrated != null ? !isMigrated.equals(that.isMigrated) : that.isMigrated != null) {
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
        if (referenceTime != null ? referenceTime.compareTo(that.referenceTime) != 0 : that.referenceTime != null) {
            return false;
        }
        if (timeZone != null ? !timeZone.equals(that.timeZone) : that.timeZone != null) {
            return false;
        }
        if (notes != null ? !notes.equals(that.notes) : that.notes != null) {
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
        result = 31 * result + billCycleDayLocal;
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
        result = 31 * result + (isMigrated != null ? isMigrated.hashCode() : 0);
        return result;
    }

    public void validateAccountUpdateInput(final Account currentAccount, boolean ignoreNullInput) {

        //
        // We don't allow update on the following fields:
        //
        // All these conditions are written in the exact same way:
        //
        // There is already a defined value BUT those don't match (either input is null or different) => Not Allowed
        // * ignoreNullInput = false (case where we allow to reset values)
        // * ignoreNullInput = true (case where we DON'T allow to reset values and so if such value is null we ignore the check)
        //
        //
        if ((ignoreNullInput || externalKey != null) &&
            currentAccount.getExternalKey() != null &&
            !currentAccount.getExternalKey().equals(externalKey)) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account external key yet: new=%s, current=%s",
                                                             externalKey, currentAccount.getExternalKey()));
        }

        if ((ignoreNullInput || currency != null) &&
            currentAccount.getCurrency() != null &&
            !currentAccount.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account currency yet: new=%s, current=%s",
                                                             currency, currentAccount.getCurrency()));
        }

        if ((ignoreNullInput || (billCycleDayLocal != null && billCycleDayLocal != DEFAULT_BILLING_CYCLE_DAY_LOCAL)) &&
            currentAccount.getBillCycleDayLocal() != DEFAULT_BILLING_CYCLE_DAY_LOCAL && // There is already a BCD set
            !currentAccount.getBillCycleDayLocal().equals(billCycleDayLocal)) { // and it does not match we we have
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account BCD yet: new=%s, current=%s", billCycleDayLocal, currentAccount.getBillCycleDayLocal()));
        }

        if ((ignoreNullInput || timeZone != null) &&
            currentAccount.getTimeZone() != null &&
            !currentAccount.getTimeZone().equals(timeZone)) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account timeZone yet: new=%s, current=%s",
                                                             timeZone, currentAccount.getTimeZone()));
        }

        if (referenceTime != null && currentAccount.getReferenceTime().withMillisOfDay(0).compareTo(referenceTime.withMillisOfDay(0)) != 0) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account referenceTime yet: new=%s, current=%s",
                                                             referenceTime, currentAccount.getReferenceTime()));
        }

    }

}
