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

import com.ning.billing.account.dao.IAccountDao;
import com.ning.billing.account.glue.InjectorMagic;
import com.ning.billing.catalog.api.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Account extends CustomizableEntityBase implements IAccount {
    private static IAccountDao dao;

    private String externalKey;
    private String email;
    private String name;
    private String phone;
    private Currency currency;
    private int billCycleDay;

    private IAccount originalData;

    public Account() {
        this(UUID.randomUUID());
    }

    public Account(UUID id) {
        super(id);
        dao = InjectorMagic.getAccountDao();
    }

    public Account(IAccountData data) {
        this();
        this.externalKey = data.getExternalKey();
        this.email = data.getEmail();
        this.name = data.getName();
        this.phone = data.getPhone();
        this.currency = data.getCurrency();
        this.billCycleDay = data.getBillCycleDay();
    }

    @Override
    public String getObjectName() {
        return "Account";
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    public Account externalKey(String externalKey) {
        this.externalKey = externalKey;
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    public Account name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String getEmail() {
        return email;
    }

    public Account email(String email) {
        this.email = email;
        return this;
    }

    @Override
    public String getPhone() {
        return phone;
    }

    public Account phone(String phone) {
        this.phone = phone;
        return this;
    }

    @Override
    public int getBillCycleDay() {
        return billCycleDay;
    }

    public Account billCycleDay(int billCycleDay) {
        this.billCycleDay = billCycleDay;
        return this;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    public Account currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public static Account create() {
        return new Account();
    }

    public static Account create(UUID id) {
        return new Account(id);
    }

    public static Account loadAccount(UUID id) {
        Account account = (Account) dao.getAccountById(id);
        if (account != null) {
            account.loadCustomFields();
        }
        return account;
    }

    public static Account loadAccount(String key) {
        Account account = (Account) dao.getAccountByKey(key);
        if (account != null) {
            account.loadCustomFields();
        }
        return account;
    }

    @Override
    protected void saveObject() {
        dao.saveAccount(this);
    }

    @Override
    protected void updateObject() {
        dao.updateAccount(this);
    }

    @Override
    protected void loadObject() {
        this.originalData = dao.getAccountById(id);
        this.externalKey = originalData.getExternalKey();
        this.email = originalData.getEmail();
        this.name = originalData.getName();
        this.phone = originalData.getPhone();
        this.currency = originalData.getCurrency();
        this.billCycleDay = originalData.getBillCycleDay();
    }

    private List<ChangedField> getChangedFields() {
        List<ChangedField> changedFields = new ArrayList<ChangedField>();

        if (!this.externalKey.equals(originalData.getExternalKey())) {
            changedFields.add(new ChangedField("externalKey", this.externalKey, originalData.getExternalKey()));
        }
        if (!this.email.equals(originalData.getEmail())) {
            changedFields.add(new ChangedField("email", this.email, originalData.getEmail()));
        }
        if (!this.name.equals(originalData.getName())) {
            changedFields.add(new ChangedField("name", this.name, originalData.getName()));
        }
        if (!this.phone.equals(originalData.getPhone())) {
            changedFields.add(new ChangedField("phone", this.phone, originalData.getPhone()));
        }
        if (!this.currency.equals(originalData.getCurrency())) {
            changedFields.add(new ChangedField("currency", this.currency.toString(), originalData.getCurrency().toString()));
        }
        if (this.billCycleDay != originalData.getBillCycleDay()) {
            changedFields.add(new ChangedField("billCycleDay", Integer.toString(this.billCycleDay),
                                                               Integer.toString(originalData.getBillCycleDay())));
        }

        return changedFields;
    }
}
