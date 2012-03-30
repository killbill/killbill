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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.tag.TagStore;

public class MutableAccountData implements AccountData {
    private String externalKey;
    private String email;
    private String name;
    private int firstNameLength;
    private Currency currency;
    private int billCycleDay;
    private String paymentProviderName;
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
    
    public MutableAccountData(String externalKey, String email, String name,
            int firstNameLength, Currency currency, int billCycleDay,
            String paymentProviderName, TagStore tags, DateTimeZone timeZone,
            String locale, String address1, String address2,
            String companyName, String city, String stateOrProvince,
            String country, String postalCode, String phone,
            DateTime createdDate, DateTime updatedDate) {
        super();
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.currency = currency;
        this.billCycleDay = billCycleDay;
        this.paymentProviderName = paymentProviderName;
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
    }
    
    public MutableAccountData(AccountData accountData) {
        super();
        this.externalKey = accountData.getExternalKey();
        this.email = accountData.getEmail();
        this.name = accountData.getName();
        this.firstNameLength = accountData.getFirstNameLength();
        this.currency = accountData.getCurrency();
        this.billCycleDay = accountData.getBillCycleDay();
        this.paymentProviderName = accountData.getPaymentProviderName();
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
    public int getFirstNameLength() {
        return firstNameLength;
    }
    public Currency getCurrency() {
        return currency;
    }
    public int getBillCycleDay() {
        return billCycleDay;
    }
    public String getPaymentProviderName() {
        return paymentProviderName;
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
    
    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setFirstNameLength(int firstNameLength) {
        this.firstNameLength = firstNameLength;
    }
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
    public void setBillCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
    }
    public void setPaymentProviderName(String paymentProviderName) {
        this.paymentProviderName = paymentProviderName;
    }
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }
    public void setLocale(String locale) {
        this.locale = locale;
    }
    public void setAddress1(String address1) {
        this.address1 = address1;
    }
    public void setAddress2(String address2) {
        this.address2 = address2;
    }
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public void setStateOrProvince(String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }


}
