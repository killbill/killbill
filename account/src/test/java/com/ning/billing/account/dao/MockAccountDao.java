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

package com.ning.billing.account.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.account.api.Account;

public class MockAccountDao implements AccountDao {
    private final Map<String, Account> accounts = new ConcurrentHashMap<String, Account>();

    @Override
    public void create(Account entity) {
        accounts.put(entity.getId().toString(), entity);
    }

    @Override
    public Account getById(String id) {
        return accounts.get(id);
    }

    @Override
    public List<Account> get() {
        return new ArrayList<Account>(accounts.values());
    }

    @Override
    public void test() {
    }

    @Override
    public Account getAccountByKey(String key) {
        for (Account account : accounts.values()) {
            if (key.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public UUID getIdFromKey(String externalKey) {
        Account account = getAccountByKey(externalKey);
        return account == null ? null : account.getId();
    }

    @Override
    public void update(Account entity) {
        accounts.put(entity.getId().toString(), entity);
    }

}
