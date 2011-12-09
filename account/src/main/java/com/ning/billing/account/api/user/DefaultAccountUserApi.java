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

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.dao.AccountDao;

import java.util.List;
import java.util.UUID;

public class DefaultAccountUserApi implements com.ning.billing.account.api.AccountUserApi {
    private final AccountDao dao;

    @Inject
    public DefaultAccountUserApi(AccountDao dao) {
        this.dao = dao;
    }

    @Override
    public Account createAccount(AccountData data) {
        Account account = new DefaultAccount(data);
        dao.save(account);
        return account;
    }

    @Override
    public Account getAccountByKey(String key) {
        return dao.getAccountByKey(key);
    }

    @Override
    public Account getAccountById(UUID id) {
        return dao.getById(id.toString());
    }

    @Override
    public List<Account> getAccounts() {
        return dao.get();
    }

    @Override
    public void saveAccount(Account account) {
        dao.save(account);
    }
}
