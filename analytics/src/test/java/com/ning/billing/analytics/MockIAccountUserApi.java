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

import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.IAccount;
import com.ning.billing.account.api.IAccountData;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.catalog.api.Currency;

public class MockIAccountUserApi implements IAccountUserApi
{
    private final Account account;

    public MockIAccountUserApi(final String accountKey, final Currency currency)
    {
        account = new Account(UUID.randomUUID()).withKey(accountKey).withCurrency(currency);
    }

    @Override
    public IAccount createAccount(final IAccountData data)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IAccount getAccountByKey(final String key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public IAccount getAccountFromId(final UUID uid)
    {
        return account;
    }

    @Override
    public List<IAccount> getAccounts()
    {
        throw new UnsupportedOperationException();
    }
}
