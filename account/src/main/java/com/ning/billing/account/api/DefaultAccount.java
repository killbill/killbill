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

package com.ning.billing.account.api;

import java.util.UUID;

import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.entity.EntityBase;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

public class DefaultAccount extends EntityBase implements Account {

    // Default values. When updating an account object, null values are
    // interpreted as "no change". You can use these defaults to reset
    // some fields
    public static final String DEFAULT_STRING_VALUE = "";
    public static final Integer DEFAULT_INTEGER_VALUE = 0;
    public static final BillCycleDay DEFAULT_BCD_VALUE = new DefaultBillCycleDay(DEFAULT_INTEGER_VALUE);
    public static final Currency DEFAULT_CURRENCY_VALUE = Currency.USD;
    public static final DateTimeZone DEFAULT_TIMEZONE_VALUE = DateTimeZone.UTC;
    private static final Boolean DEFAULT_MIGRATED_VALUE = true;
    private static final Boolean DEFAULT_NOTIFIED_FOR_INVOICES_VALUE = false;

    private final String externalKey;
    private final String email;
    private final String name;
    private final Integer firstNameLength;
    private final Currency currency;
    private final BillCycleDay billCycleDay;
    private final UUID paymentMethodId;
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
    private final Boolean isMigrated;
    private final Boolean isNotifiedForInvoices;

    public DefaultAccount(final AccountData data) {
        this(UUID.randomUUID(), data);
    }

    /**
     * This call is used to update an existing account
     *
     * @param id   UUID id of the existing account to update
     * @param data AccountData new data for the existing account
     */
    public DefaultAccount(final UUID id, final AccountData data) {
        this(id, data.getExternalKey(), data.getEmail(), data.getName(), data.getFirstNameLength(),
             data.getCurrency(), data.getBillCycleDay(), data.getPaymentMethodId(),
             data.getTimeZone(), data.getLocale(),
             data.getAddress1(), data.getAddress2(), data.getCompanyName(),
             data.getCity(), data.getStateOrProvince(), data.getCountry(),
             data.getPostalCode(), data.getPhone(), data.isMigrated(), data.isNotifiedForInvoices());
    }

    /*
    * This call is used for testing and update from an existing account
    */
    public DefaultAccount(final UUID id, final String externalKey, final String email,
                          final String name, final Integer firstNameLength,
                          final Currency currency, final BillCycleDay billCycleDay, final UUID paymentMethodId,
                          final DateTimeZone timeZone, final String locale,
                          final String address1, final String address2, final String companyName,
                          final String city, final String stateOrProvince, final String country,
                          final String postalCode, final String phone,
                          final Boolean isMigrated, final Boolean isNotifiedForInvoices) {
        super(id);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.billCycleDay = billCycleDay;
        this.paymentMethodId = paymentMethodId;
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
        this.isMigrated = isMigrated;
        this.isNotifiedForInvoices = isNotifiedForInvoices;
    }

    @Override
    public String getExternalKey() {
        return Objects.firstNonNull(externalKey, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getName() {
        return Objects.firstNonNull(name, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getEmail() {
        return Objects.firstNonNull(email, DEFAULT_STRING_VALUE);
    }

    @Override
    public Integer getFirstNameLength() {
        return Objects.firstNonNull(firstNameLength, DEFAULT_INTEGER_VALUE);
    }

    @Override
    public Currency getCurrency() {
        return Objects.firstNonNull(currency, DEFAULT_CURRENCY_VALUE);
    }

    @Override
    public BillCycleDay getBillCycleDay() {
        return Objects.firstNonNull(billCycleDay, DEFAULT_BCD_VALUE);
    }

    @Override
    public UUID getPaymentMethodId() {
        // Null if non specified
        return paymentMethodId;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return Objects.firstNonNull(timeZone, DEFAULT_TIMEZONE_VALUE);
    }

    @Override
    public String getLocale() {
        return Objects.firstNonNull(locale, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getAddress1() {
        return Objects.firstNonNull(address1, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getAddress2() {
        return Objects.firstNonNull(address2, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getCompanyName() {
        return Objects.firstNonNull(companyName, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getCity() {
        return Objects.firstNonNull(city, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getStateOrProvince() {
        return Objects.firstNonNull(stateOrProvince, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getPostalCode() {
        return Objects.firstNonNull(postalCode, DEFAULT_STRING_VALUE);
    }

    @Override
    public String getCountry() {
        return Objects.firstNonNull(country, DEFAULT_STRING_VALUE);
    }

    @Override
    public Boolean isMigrated() {
        return Objects.firstNonNull(this.isMigrated, DEFAULT_MIGRATED_VALUE);
    }

    @Override
    public Boolean isNotifiedForInvoices() {
        return Objects.firstNonNull(isNotifiedForInvoices, DEFAULT_NOTIFIED_FOR_INVOICES_VALUE);
    }

    @Override
    public String getPhone() {
        return Objects.firstNonNull(phone, DEFAULT_STRING_VALUE);
    }

    @Override
    public MutableAccountData toMutableAccountData() {
        return new DefaultMutableAccountData(this);
    }

    @Override
    public Account mergeWithDelegate(final Account delegate) {
        final DefaultMutableAccountData accountData = new DefaultMutableAccountData(this);

        if (externalKey != null ? !externalKey.equals(delegate.getExternalKey()) : delegate.getExternalKey() != null) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account external key yet: this=%s, delegate=%s",
                                                             externalKey, delegate.getExternalKey()));
        }

        if (currency != null ? !currency.equals(delegate.getCurrency()) : delegate.getCurrency() != null) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account currency yet: this=%s, delegate=%s",
                                                             currency, delegate.getCurrency()));
        }

        // We can't just use .equals here as the BillCycleDay class might not have implemented it
        if (billCycleDay != null ? !(billCycleDay.getDayOfMonthUTC() == delegate.getBillCycleDay().getDayOfMonthUTC() &&
                                     billCycleDay.getDayOfMonthLocal() == delegate.getBillCycleDay().getDayOfMonthLocal()) : delegate.getBillCycleDay() != null) {
            throw new IllegalArgumentException(String.format("Killbill doesn't support updating the account BCD yet: this=%s, delegate=%s",
                                                             billCycleDay, delegate.getBillCycleDay()));
        }

        accountData.setEmail(Objects.firstNonNull(email, delegate.getEmail()));
        accountData.setName(Objects.firstNonNull(name, delegate.getName()));
        accountData.setFirstNameLength(Objects.firstNonNull(firstNameLength, delegate.getFirstNameLength()));
        accountData.setPaymentMethodId(Optional.<UUID>fromNullable(paymentMethodId)
                                               .or(Optional.<UUID>fromNullable(delegate.getPaymentMethodId())).orNull());
        accountData.setTimeZone(Objects.firstNonNull(timeZone, delegate.getTimeZone()));
        accountData.setLocale(Objects.firstNonNull(locale, delegate.getLocale()));
        accountData.setAddress1(Objects.firstNonNull(address1, delegate.getAddress1()));
        accountData.setAddress2(Objects.firstNonNull(address2, delegate.getAddress2()));
        accountData.setCompanyName(Objects.firstNonNull(companyName, delegate.getCompanyName()));
        accountData.setCity(Objects.firstNonNull(city, delegate.getCity()));
        accountData.setStateOrProvince(Objects.firstNonNull(stateOrProvince, delegate.getStateOrProvince()));
        accountData.setCountry(Objects.firstNonNull(country, delegate.getCountry()));
        accountData.setPostalCode(Objects.firstNonNull(postalCode, delegate.getPostalCode()));
        accountData.setPhone(Objects.firstNonNull(phone, delegate.getPhone()));
        accountData.setIsMigrated(Objects.firstNonNull(isMigrated, delegate.isMigrated()));
        accountData.setIsNotifiedForInvoices(Objects.firstNonNull(isNotifiedForInvoices, delegate.isNotifiedForInvoices()));

        return new DefaultAccount(delegate.getId(), accountData);
    }

    @Override
    public String toString() {
        return "DefaultAccount [externalKey=" + externalKey +
               ", email=" + email +
               ", name=" + name +
               ", firstNameLength=" + firstNameLength +
               ", phone=" + phone +
               ", currency=" + currency +
               ", billCycleDay=" + billCycleDay +
               ", paymentMethodId=" + paymentMethodId +
               ", timezone=" + timeZone +
               ", locale=" + locale +
               ", address1=" + address1 +
               ", address2=" + address2 +
               ", companyName=" + companyName +
               ", city=" + city +
               ", stateOrProvince=" + stateOrProvince +
               ", postalCode=" + postalCode +
               ", country=" + country +
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

        final DefaultAccount that = (DefaultAccount) o;

        if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
            return false;
        }
        if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
            return false;
        }
        if (billCycleDay != null ? !billCycleDay.equals(that.billCycleDay) : that.billCycleDay != null) {
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
        if (isMigrated != null ? !isMigrated.equals(that.isMigrated) : that.isMigrated != null) {
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
        int result = externalKey != null ? externalKey.hashCode() : 0;
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (firstNameLength != null ? firstNameLength.hashCode() : 0);
        result = 31 * result + (currency != null ? currency.hashCode() : 0);
        result = 31 * result + (billCycleDay != null ? billCycleDay.hashCode() : 0);
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
        result = 31 * result + (isMigrated != null ? isMigrated.hashCode() : 0);
        result = 31 * result + (isNotifiedForInvoices != null ? isNotifiedForInvoices.hashCode() : 0);
        return result;
    }

    @Override
    public BlockingState getBlockingState() {
        throw new UnsupportedOperationException();
    }
}
