/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.catalog.api.Currency;

public class DefaultMutableAccountData implements MutableAccountData {

    // 0 has a special meaning in Junction
    public static final int DEFAULT_BILLING_CYCLE_DAY_LOCAL = 0;

    private String externalKey;
    private String email;
    private String name;
    private Integer firstNameLength;
    private Currency currency;
    private UUID parentAccountId;
    private Boolean isPaymentDelegatedToParent;
    private int billCycleDayLocal;
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
    private Boolean isMigrated;

    public DefaultMutableAccountData(final String externalKey, final String email, final String name,
                                     final int firstNameLength, final Currency currency,
                                     final UUID parentAccountId, final Boolean isPaymentDelegatedToParent,
                                     final int billCycleDayLocal, final UUID paymentMethodId, final DateTime referenceTime,
                                     final DateTimeZone timeZone, final String locale, final String address1, final String address2,
                                     final String companyName, final String city, final String stateOrProvince,
                                     final String country, final String postalCode, final String phone,
                                     final String notes, final boolean isMigrated) {
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.parentAccountId = parentAccountId;
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
        this.billCycleDayLocal = billCycleDayLocal;
        this.paymentMethodId = paymentMethodId;
        this.referenceTime = referenceTime;
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
        this.notes = notes;
        this.isMigrated = isMigrated;
    }

    public DefaultMutableAccountData(final AccountData accountData) {
        this.externalKey = accountData.getExternalKey();
        this.email = accountData.getEmail();
        this.name = accountData.getName();
        this.firstNameLength = accountData.getFirstNameLength();
        this.currency = accountData.getCurrency();
        this.parentAccountId = accountData.getParentAccountId();
        this.isPaymentDelegatedToParent = accountData.isPaymentDelegatedToParent();
        this.billCycleDayLocal = accountData.getBillCycleDayLocal() == null ? DEFAULT_BILLING_CYCLE_DAY_LOCAL : accountData.getBillCycleDayLocal();
        this.paymentMethodId = accountData.getPaymentMethodId();
        this.referenceTime = accountData.getReferenceTime();
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
        this.notes = accountData.getNotes();
        this.isMigrated = accountData.isMigrated();
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public void setExternalKey(final String externalKey) {
        this.externalKey = externalKey;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(final String email) {
        this.email = email;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public Integer getFirstNameLength() {
        return firstNameLength;
    }

    @Override
    public void setFirstNameLength(final int firstNameLength) {
        this.firstNameLength = firstNameLength;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public void setCurrency(final Currency currency) {
        this.currency = currency;
    }

    @Override
    public Integer getBillCycleDayLocal() {
        return billCycleDayLocal;
    }

    @Override
    public void setBillCycleDayLocal(final int billCycleDayLocal) {
        this.billCycleDayLocal = billCycleDayLocal;
    }

    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    @Override
    public DateTime getReferenceTime() {
        return referenceTime;
    }


    @Override
    public void setPaymentMethodId(final UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    @Override
    public void setReferenceTime(final DateTime dateTime) {
        this.referenceTime = referenceTime;
    }

    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public void setTimeZone(final DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    public String getLocale() {
        return locale;
    }

    @Override
    public void setLocale(final String locale) {
        this.locale = locale;
    }

    @Override
    public String getAddress1() {
        return address1;
    }

    @Override
    public void setAddress1(final String address1) {
        this.address1 = address1;
    }

    @Override
    public String getAddress2() {
        return address2;
    }

    @Override
    public void setAddress2(final String address2) {
        this.address2 = address2;
    }

    @Override
    public String getCompanyName() {
        return companyName;
    }

    @Override
    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    @Override
    public String getCity() {
        return city;
    }

    @Override
    public void setCity(final String city) {
        this.city = city;
    }

    @Override
    public String getStateOrProvince() {
        return stateOrProvince;
    }

    @Override
    public void setStateOrProvince(final String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    @Override
    public String getCountry() {
        return country;
    }

    @Override
    public void setCountry(final String country) {
        this.country = country;
    }

    @Override
    public String getPostalCode() {
        return postalCode;
    }

    @Override
    public void setPostalCode(final String postalCode) {
        this.postalCode = postalCode;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public void setPhone(final String phone) {
        this.phone = phone;
    }

    @Override
    public String getNotes() {
        return notes;
    }

    @Override
    public void setNotes(final String notes) {
        this.notes = notes;
    }

    @Override
    public Boolean isMigrated() {
        return isMigrated;
    }

    @Override
    public void setIsMigrated(final boolean isMigrated) {
        this.isMigrated = isMigrated;
    }

    @Override
    public UUID getParentAccountId() {
        return parentAccountId;
    }

    @Override
    public void setParentAccountId(final UUID parentAccountId) {
        this.parentAccountId = parentAccountId;
    }

    @Override
    public void setIsPaymentDelegatedToParent(final boolean isPaymentDelegatedToParent) {
        this.isPaymentDelegatedToParent = isPaymentDelegatedToParent;
    }

    @Override
    public Boolean isPaymentDelegatedToParent() {
        return isPaymentDelegatedToParent;
    }
}
