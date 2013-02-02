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

package com.ning.billing.account.api;

import java.util.UUID;

import org.joda.time.DateTimeZone;

import com.ning.billing.catalog.api.Currency;

/**
 * The interface {@code MutableAccountData} is used to set the  {@code AccountData} fields individually and pass them as the whole
 * to the {@code AccountUserApi}.
 *
 */
public interface MutableAccountData extends AccountData {

    /**
     * Sets the account external Key
     *
     * @param externalKey
     */
    public void setExternalKey(String externalKey);

    /**
     * Sets the default account email
     *
     * @param email
     */
    public void setEmail(String email);

    /**
     * Sets the account name
     *
     * @param name
     */
    public void setName(String name);

    /**
     * Sets the length for the first name-- if applicable
     *
     * @param firstNameLength
     */
    public void setFirstNameLength(int firstNameLength);

    /**
     * Sets the account currency
     *
     * @param currency
     */
    public void setCurrency(Currency currency);

    /**
     * Sets the account billCycleDay
     *
     * @param billCycleDay
     */
    public void setBillCycleDay(BillCycleDay billCycleDay);

    /**
     * Sets the UUID of the default paymentMethod
     *
     * @param paymentMethodId
     */
    public void setPaymentMethodId(UUID paymentMethodId);

    /**
     * Sets the account timezone
     *
     * @param timeZone
     */
    public void setTimeZone(DateTimeZone timeZone);

    /**
     * Sets the account locale
     *
     * @param locale
     */
    public void setLocale(String locale);

    /**
     * Sets the account address (first line)
     * @param address1
     */
    public void setAddress1(String address1);

    /**
     * Sets the account address (second line)
     * @param address2
     */
    public void setAddress2(String address2);

    /**
     * Sets the account company name
     *
     * @param companyName
     */
    public void setCompanyName(String companyName);

    /**
     * Sets the account city
     *
     * @param city
     */
    public void setCity(String city);

    /**
     * Sets the account state or province
     *
     * @param stateOrProvince
     */
    public void setStateOrProvince(String stateOrProvince);

    /**
     * Sets the account country
     *
     * @param country
     */
    public void setCountry(String country);

    /**
     * Sets the account postalCode
     *
     * @param postalCode
     */
    public void setPostalCode(String postalCode);

    /**
     * Sets the account phone number
     *
     * @param phone
     */
    public void setPhone(String phone);

    /**
     * Sets whether the account has been migrated or not
     *
     * @param isMigrated
     */
    public void setIsMigrated(boolean isMigrated);

    /**
     * Sets whether or not the account should receive notification on future invoices
     * @param isNotifiedForInvoices
     */
    public void setIsNotifiedForInvoices(boolean isNotifiedForInvoices);

}
