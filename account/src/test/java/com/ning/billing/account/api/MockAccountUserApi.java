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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockAccountUserApi implements IAccountUserApi {
    private final CopyOnWriteArrayList<IAccount> accounts = new CopyOnWriteArrayList<IAccount>();

    @Override
    public IAccount createAccount(IAccountData data) {
        IAccount result = new Account().withKey(data.getKey())
                                       .withName(data.getName())
                                       .withEmail(data.getEmail())
                                       .withPhone(data.getPhone())
                                       .withBillCycleDay(data.getBillCycleDay())
                                       .withCurrency(data.getCurrency());
        accounts.add(result);
        return result;
    }

    @Override
    public IAccount getAccountByKey(String key) {
        for (IAccount account : accounts) {
            if (key.equals(account.getKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public IAccount getAccountFromId(UUID uid) {
        for (IAccount account : accounts) {
            if (uid.equals(account.getId())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public List<IAccount> getAccounts() {
        return new ArrayList<IAccount>(accounts);
    }

}
