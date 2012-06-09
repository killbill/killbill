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

public interface MutableAccountData extends AccountData {
    public void setExternalKey(String externalKey);

    public void setEmail(String email);

    public void setName(String name);

    public void setFirstNameLength(int firstNameLength);

    public void setCurrency(Currency currency);

    public void setBillCycleDay(int billCycleDay);

    public void setPaymentMethodId(UUID paymentMethodId);

    public void setTimeZone(DateTimeZone timeZone);

    public void setLocale(String locale);

    public void setAddress1(String address1);

    public void setAddress2(String address2);

    public void setCompanyName(String companyName);

    public void setCity(String city);

    public void setStateOrProvince(String stateOrProvince);

    public void setCountry(String country);

    public void setPostalCode(String postalCode);

    public void setPhone(String phone);

    public void setIsMigrated(boolean isMigrated);

    public void setIsNotifiedForInvoices(boolean isNotifiedForInvoices);

}