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
import com.ning.billing.util.tag.TagStore;

public class DefaultMutableAccountData implements MutableAccountData {

    private String externalKey;
    private String email;
    private String name;
    private int firstNameLength;
    private Currency currency;
    private BillCycleDay billCycleDay;
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
    private boolean isMigrated;
    private boolean isNotifiedForInvoices;

    public DefaultMutableAccountData(final String externalKey, final String email, final String name,
                                     final int firstNameLength, final Currency currency, final BillCycleDay billCycleDay,
                                     final UUID paymentMethodId, final TagStore tags, final DateTimeZone timeZone,
                                     final String locale, final String address1, final String address2,
                                     final String companyName, final String city, final String stateOrProvince,
                                     final String country, final String postalCode, final String phone,
                                     final boolean isMigrated, final boolean isNotifiedForInvoices) {
        super();
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
        this.country = country;
        this.postalCode = postalCode;
        this.phone = phone;
        this.isMigrated = isMigrated;
        this.isNotifiedForInvoices = isNotifiedForInvoices;
    }

    public DefaultMutableAccountData(final AccountData accountData) {
        super();
        this.externalKey = accountData.getExternalKey();
        this.email = accountData.getEmail();
        this.name = accountData.getName();
        this.firstNameLength = accountData.getFirstNameLength();
        this.currency = accountData.getCurrency();
        this.billCycleDay = accountData.getBillCycleDay();
        this.paymentMethodId = accountData.getPaymentMethodId();
        this.timeZone = accountData.getTimeZone();
        this.locale = accountData.getLocale();
        this.address1 = accountData.getAddress1();
        this.address2 = accountData.getAddress2();
        this.companyName = accountData.getCompanyName();
        this.city = accountData.getCity();
        this.stateOrProvince = accountData.getStateOrProvince();
        this.country = accountData.getCountry();
        this.postalCode = accountData.getPostalCode();
        this.phone = accountData.getPhone();
        this.isMigrated = accountData.isMigrated();
        this.isNotifiedForInvoices = accountData.isNotifiedForInvoices();
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public String getName() {
        return name;
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
    public BillCycleDay getBillCycleDay() {
        return billCycleDay;
    }

    @Override
    public UUID getPaymentMethodId() {
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
    public String getCountry() {
        return country;
    }

    @Override
    public String getPostalCode() {
        return postalCode;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public Boolean isMigrated() {
        return isMigrated;
    }

    @Override
    public Boolean isNotifiedForInvoices() {
        return isNotifiedForInvoices;
    }

    @Override
    public void setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
    }

    @Override
    public void setEmail(final String email) {
        this.email = email;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public void setFirstNameLength(final int firstNameLength) {
        this.firstNameLength = firstNameLength;
    }

    @Override
    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    @Override
    public void setBillCycleDay(final BillCycleDay billCycleDay) {
        this.billCycleDay = billCycleDay;
    }

    @Override
    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    @Override
    public void setTimeZone(final DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    public void setLocale(final String locale) {
        this.locale = locale;
    }

    @Override
    public void setAddress1(final String address1) {
        this.address1 = address1;
    }

    @Override
    public void setAddress2(final String address2) {
        this.address2 = address2;
    }

    @Override
    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    @Override
    public void setCity(final String city) {
        this.city = city;
    }

    @Override
    public void setStateOrProvince(final String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    @Override
    public void setCountry(final String country) {
        this.country = country;
    }

    @Override
    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }

    @Override
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    @Override
    public void setIsMigrated(final boolean isMigrated) {
        this.isMigrated = isMigrated;
    }

    @Override
    public void setIsNotifiedForInvoices(final boolean isNotifiedForInvoices) {
        this.isNotifiedForInvoices = isNotifiedForInvoices;
    }
}
