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

public class Account extends CustomizableEntityBase implements IAccount {
    public final static String OBJECT_TYPE = "Account";

    private String externalKey;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Currency currency;
    private int billCycleDay;

    public Account(IAccountData data) {
        this(UUID.randomUUID(), data.getExternalKey(), data.getEmail(), data.getFirstName(), data.getLastName(),
                data.getPhone(), data.getCurrency(), data.getBillCycleDay());
    }

    public Account(UUID id, String externalKey, String email, String firstName, String lastName,
                   String phone, Currency currency, int billCycleDay) {
        super(id);
        this.externalKey = externalKey;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
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

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }

    public void setBillCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
    }
}
