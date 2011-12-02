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

package com.ning.billing.analytics;

import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountData;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.catalog.api.Currency;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.List;
import java.util.UUID;

public class MockIAccountUserApi implements IAccountUserApi
{
    private final MockAccount account;

    public MockIAccountUserApi(final String accountKey, final Currency currency)
    {
        account = new MockAccount(UUID.randomUUID(), accountKey, currency);
    }

    @Override
    public IAccount createAccount(final IAccountData data)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void saveAccount(IAccount account) {
        throw new NotImplementedException();
    }

    @Override
    public IAccount getAccountByKey(final String key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IAccount getAccountById(final UUID uid)
    {
        return account;
    }

    @Override
    public List<IAccount> getAccounts()
    {
        throw new UnsupportedOperationException();
    }
}
