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

import com.google.inject.Inject;
import com.ning.billing.account.dao.IAccountDao;

import java.util.List;
import java.util.UUID;

public class AccountUserApi implements IAccountUserApi {

    private final IAccountDao dao;

    @Inject
    public AccountUserApi(IAccountDao dao) {
        this.dao = dao;
    }

    @Override
    public IAccount createAccount(IAccountData data) {
        return dao.createAccount(data);
    }

    @Override
    public IAccount getAccountByKey(String key) {
        return dao.getAccountByKey(key);
    }

    @Override
    public IAccount getAccountById(UUID uid) {
        return dao.getAccountById(uid);
    }

    @Override
    public List<IAccount> getAccounts() {
        return dao.getAccounts();
    }

}
