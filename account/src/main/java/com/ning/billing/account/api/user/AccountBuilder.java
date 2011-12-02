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

import com.ning.billing.account.api.Account;
import com.ning.billing.catalog.api.Currency;

import java.util.UUID;

public class AccountBuilder {
    private UUID id;
    private String externalKey;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Currency currency;
    private int billCycleDay;

    public AccountBuilder() {
        this(UUID.randomUUID());
    }

    public AccountBuilder(UUID id) {
        this.id = id;
    }

    public AccountBuilder externalKey(String externalKey) {
        this.externalKey = externalKey;
        return this;
    }

    public AccountBuilder email(String email) {
        this.email = email;
        return this;
    }

    public AccountBuilder firstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public AccountBuilder lastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public AccountBuilder phone(String phone) {
        this.phone = phone;
        return this;
    }

    public AccountBuilder billCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
        return this;
    }

    public AccountBuilder currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public Account build() {
        return new Account(id, externalKey, email, firstName, lastName, phone, currency, billCycleDay);
    }
}
