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

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.util.entity.EntityBase;

public class DefaultAccount extends EntityBase implements Account {
    // Default values. When updating an account object, null values are
    // interpreted as "no change". You can use these defaults to reset
    // some fields
    public static final String DEFAULT_STRING_VALUE = "";
    public static final Integer DEFAULT_INTEGER_VALUE = 0;
    public static final Integer DEFAULT_BCD_VALUE = DEFAULT_INTEGER_VALUE;
    public static final Currency DEFAULT_CURRENCY_VALUE = Currency.USD;
    public static final DateTimeZone DEFAULT_TIMEZONE_VALUE = DateTimeZone.UTC;
    private static final Boolean DEFAULT_MIGRATED_VALUE = true;
    private static final Boolean DEFAULT_NOTIFIED_FOR_INVOICES_VALUE = false;

    private final String externalKey;
    private final String email;
    private final String name;
    private final int firstNameLength;
    private final Currency currency;
    private final int billCycleDay;
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
    private final boolean isMigrated;
    private final boolean isNotifiedForInvoices;

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
                          final String name, final int firstNameLength,
                          final Currency currency, final int billCycleDay, final UUID paymentMethodId,
                          final DateTimeZone timeZone, final String locale,
                          final String address1, final String address2, final String companyName,
                          final String city, final String stateOrProvince, final String country,
                          final String postalCode, final String phone,
                          final boolean isMigrated, final boolean isNotifiedForInvoices) {
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
    public Integer getBillCycleDay() {
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
        accountData.setExternalKey(Objects.firstNonNull(externalKey, delegate.getExternalKey()));
        accountData.setEmail(Objects.firstNonNull(email, delegate.getEmail()));
        accountData.setName(Objects.firstNonNull(name, delegate.getName()));
        accountData.setFirstNameLength(Objects.firstNonNull(firstNameLength, delegate.getFirstNameLength()));
        accountData.setCurrency(Objects.firstNonNull(currency, delegate.getCurrency()));
        accountData.setBillCycleDay(Objects.firstNonNull(billCycleDay, delegate.getBillCycleDay()));
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
    public BlockingState getBlockingState() {
        throw new UnsupportedOperationException();
    }
}
