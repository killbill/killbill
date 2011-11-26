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

import com.ning.billing.catalog.api.Currency;

public class Account implements IAccount {
    public static final String OBJECT_TYPE = "Account";
    private final IFieldStore fields;

    private final UUID id;
    private String key;
    private String email;
    private String name;
    private String phone;
    private Currency currency;
    private int billCycleDay;

    public Account() {
        this(UUID.randomUUID());
    }

    public Account(UUID id) {
        this.id = id;
        fields = new FieldStore();
    }

   @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getKey() {
        return key;
    }

    public Account withKey(String key) {
        this.key = key;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    public Account withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public Account withEmail(String email) {
        this.email = email;
        return this;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    public Account withPhone(String phone) {
        this.phone = phone;
        return this;
    }

    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }

    public Account withBillCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
        return this;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    public Account withCurrency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public static Account create() {
        return new Account();
    }

    public static Account create(UUID id) {
        return new Account(id);
    }

    @Override
    public IFieldStore getFields() {
        return fields;
    }

    @Override
    public String getFieldValue(String fieldName) {
        return fields.getValue(fieldName);
    }

    @Override
    public void setFieldValue(String fieldName, String fieldValue) {
        fields.setValue(fieldName, fieldValue);
    }
}
