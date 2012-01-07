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

import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.catalog.api.Currency;

import java.math.BigDecimal;
import java.util.UUID;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class AccountBuilder {
    private UUID id;
    private String externalKey;
    private String email;
    private String name;
    private int firstNameLength;
    private String phone;
    private Currency currency;
    private int billingCycleDay;
    private String paymentProviderName;
    private BigDecimal balance = BigDecimal.ZERO;
    private DateTimeZone timeZone;
    private String locale;
    private DateTime nextBillingDate;

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

    public AccountBuilder phone(String phone) {
        this.phone = phone;
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

    public AccountBuilder balance(final BigDecimal balance) {
        this.balance = balance;
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

    public AccountBuilder nextBillingDate(final DateTime nextBillingDate) {
        this.nextBillingDate = nextBillingDate;
        return this;
    }

    public DefaultAccount build() {
        return new DefaultAccount(id, externalKey, email, name, firstNameLength,
                                  phone, currency, billingCycleDay, paymentProviderName,
                                  balance, timeZone, locale, nextBillingDate);
    }
}
