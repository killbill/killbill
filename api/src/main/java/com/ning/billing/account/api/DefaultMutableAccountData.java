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
    private int billCycleDay;
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
    
    public DefaultMutableAccountData(String externalKey, String email, String name,
            int firstNameLength, Currency currency, int billCycleDay,
            UUID paymentMethodId, TagStore tags, DateTimeZone timeZone,
            String locale, String address1, String address2,
            String companyName, String city, String stateOrProvince,
            String country, String postalCode, String phone,
            boolean isMigrated, boolean isNotifiedForInvoices) {
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
    
    public DefaultMutableAccountData(AccountData accountData) {
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

    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getExternalKey()
     */
    @Override
    public String getExternalKey() {
        return externalKey;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getEmail()
     */
    @Override
    public String getEmail() {
        return email;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getName()
     */
    @Override
    public String getName() {
        return name;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getFirstNameLength()
     */
    @Override
    public int getFirstNameLength() {
        return firstNameLength;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getCurrency()
     */
    @Override
    public Currency getCurrency() {
        return currency;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getBillCycleDay()
     */
    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getPaymentProviderName()
     */
    @Override
    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getTimeZone()
     */
    @Override
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getLocale()
     */
    @Override
    public String getLocale() {
        return locale;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getAddress1()
     */
    @Override
    public String getAddress1() {
        return address1;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getAddress2()
     */
    @Override
    public String getAddress2() {
        return address2;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getCompanyName()
     */
    @Override
    public String getCompanyName() {
        return companyName;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getCity()
     */
    @Override
    public String getCity() {
        return city;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getStateOrProvince()
     */
    @Override
    public String getStateOrProvince() {
        return stateOrProvince;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getCountry()
     */
    @Override
    public String getCountry() {
        return country;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getPostalCode()
     */
    @Override
    public String getPostalCode() {
        return postalCode;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getPhone()
     */
    @Override
    public String getPhone() {
        return phone;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#isMigrated()
     */
    @Override
    public boolean isMigrated() {
        return isMigrated;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#getSendInvoiceEmails()
     */
    @Override
    public boolean isNotifiedForInvoices() {
        return isNotifiedForInvoices;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setExternalKey(java.lang.String)
     */
    @Override
    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setEmail(java.lang.String)
     */
    @Override
    public void setEmail(String email) {
        this.email = email;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setName(java.lang.String)
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setFirstNameLength(int)
     */
    @Override
    public void setFirstNameLength(int firstNameLength) {
        this.firstNameLength = firstNameLength;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setCurrency(com.ning.billing.catalog.api.Currency)
     */
    @Override
    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setBillCycleDay(int)
     */
    @Override
    public void setBillCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setPaymentProviderName(java.lang.String)
     */
    @Override
    public void setPaymentMethodId(UUID paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setTimeZone(org.joda.time.DateTimeZone)
     */
    @Override
    public void setTimeZone(DateTimeZone timeZone) {
        this.timeZone = timeZone;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setLocale(java.lang.String)
     */
    @Override
    public void setLocale(String locale) {
        this.locale = locale;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setAddress1(java.lang.String)
     */
    @Override
    public void setAddress1(String address1) {
        this.address1 = address1;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setAddress2(java.lang.String)
     */
    @Override
    public void setAddress2(String address2) {
        this.address2 = address2;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setCompanyName(java.lang.String)
     */
    @Override
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setCity(java.lang.String)
     */
    @Override
    public void setCity(String city) {
        this.city = city;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setStateOrProvince(java.lang.String)
     */
    @Override
    public void setStateOrProvince(String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setCountry(java.lang.String)
     */
    @Override
    public void setCountry(String country) {
        this.country = country;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setPostalCode(java.lang.String)
     */
    @Override
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    /* (non-Javadoc)
     * @see com.ning.billing.account.api.MutableAccountData#setPhone(java.lang.String)
     */
    @Override
    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public void setIsMigrated(boolean isMigrated) {
        this.isMigrated = isMigrated;
    }

    @Override
    public void setIsNotifiedForInvoices(boolean isNotifiedForInvoices) {
        this.isNotifiedForInvoices = isNotifiedForInvoices;
    }


}
