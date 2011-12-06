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

import com.ning.billing.catalog.api.Currency;

import java.util.UUID;

public class AccountDefault extends CustomizableEntityBase implements Account {
    public final static String OBJECT_TYPE = "Account";

    private final String externalKey;
    private final String email;
    private final String name;
    private final int firstNameLength;
    private final String phone;
    private final Currency currency;
    private final int billCycleDay;

    public AccountDefault(AccountData data) {
        this(UUID.randomUUID(), data.getExternalKey(), data.getEmail(), data.getName(),
                data.getFirstNameLength(), data.getPhone(), data.getCurrency(), data.getBillCycleDay());
    }

    public AccountDefault(UUID id, String externalKey, String email, String name, int firstNameLength,
                          String phone, Currency currency, int billCycleDay) {
        super(id);
        this.externalKey = externalKey;
        this.email = email;
        this.name = name;
        this.firstNameLength = firstNameLength;
        this.phone = phone;
        this.currency = currency;
        this.billCycleDay = billCycleDay;
    }

    @Override
    public String getObjectName() {
        return OBJECT_TYPE;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public int getFirstNameLength() {
        return firstNameLength;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }
}
