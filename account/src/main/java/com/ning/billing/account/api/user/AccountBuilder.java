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

package com.ning.billing.account.api.user;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.catalog.api.Currency;

public class AccountBuilder {
    private final UUID id;
    private String externalKey;
    private String email;
    private String name;
    private int firstNameLength;
    private Currency currency;
    private int billingCycleDay;
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
    private boolean migrated;
    private boolean isNotifiedForInvoices;
    private String createdBy;
    private DateTime createdDate;
    private String updatedBy;
    private DateTime updatedDate;

    public AccountBuilder() {
        this(UUID.randomUUID());
    }

    public AccountBuilder(final UUID id) {
        this.id = id;
    }

    public AccountBuilder externalKey(final String externalKey) {
        this.externalKey = externalKey;
        return this;
    }

    public AccountBuilder email(final String email) {
        this.email = email;
        return this;
    }

    public AccountBuilder name(final String name) {
        this.name = name;
        return this;
    }

    public AccountBuilder firstNameLength(final int firstNameLength) {
        this.firstNameLength = firstNameLength;
        return this;
    }

    public AccountBuilder billingCycleDay(final int billingCycleDay) {
        this.billingCycleDay = billingCycleDay;
        return this;
    }

    public AccountBuilder currency(final Currency currency) {
        this.currency = currency;
        return this;
    }

    public AccountBuilder paymentProviderName(final String paymentProviderName) {
        this.paymentProviderName = paymentProviderName;
        return this;
    }

    public AccountBuilder timeZone(final DateTimeZone timeZone) {
        this.timeZone = timeZone;
        return this;
    }

    public AccountBuilder locale(final String locale) {
        this.locale = locale;
        return this;
    }

    public AccountBuilder address1(final String address1) {
        this.address1 = address1;
        return this;
    }

    public AccountBuilder address2(final String address2) {
        this.address2 = address2;
        return this;
    }

    public AccountBuilder companyName(final String companyName) {
        this.companyName = companyName;
        return this;
    }

    public AccountBuilder city(final String city) {
        this.city = city;
        return this;
    }

    public AccountBuilder stateOrProvince(final String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
        return this;
    }

    public AccountBuilder postalCode(final String postalCode) {
        this.postalCode = postalCode;
        return this;
    }

    public AccountBuilder country(final String country) {
        this.country = country;
        return this;
    }

    public AccountBuilder phone(final String phone) {
        this.phone = phone;
        return this;
    }

    public AccountBuilder migrated(final boolean migrated) {
        this.migrated = migrated;
        return this;
    }

    public AccountBuilder isNotifiedForInvoices(final boolean isNotifiedForInvoices) {
        this.isNotifiedForInvoices = isNotifiedForInvoices;
        return this;
    }

    public AccountBuilder createdBy(final String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public AccountBuilder createdDate(final DateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public AccountBuilder updatedBy(final String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public AccountBuilder updatedDate(final DateTime updatedDate) {
        this.updatedDate = updatedDate;
        return this;
    }

    public DefaultAccount build() {
        return new DefaultAccount(id, externalKey, email, name, firstNameLength,
                                  currency, billingCycleDay, paymentProviderName,
                                  timeZone, locale,
                                  address1, address2, companyName, city, stateOrProvince, country,
                                  postalCode, phone, migrated, isNotifiedForInvoices,
                                  createdBy, createdDate, updatedBy, updatedDate);
    }
}
