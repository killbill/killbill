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

import org.apache.commons.lang.NotImplementedException;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;

public class MockIAccountUserApi implements AccountUserApi
{
    private final AccountData account;
    private final UUID id;

    public MockIAccountUserApi(final String accountKey, final Currency currency)
    {
        this.id = UUID.randomUUID();
        account = new MockAccount(id, accountKey, currency);
    }

    @Override
    public Account createAccount(final AccountData data, final List<CustomField> fields, final List<Tag> tags, final CallContext context)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Account getAccountByKey(final String key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Account getAccountById(final UUID uid) {
        return new DefaultAccount(account);
    }

    @Override
    public List<Account> getAccounts()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getIdFromKey(String externalKey) {
        return id;
    }

	@Override
	public Account migrateAccount(MigrationAccountData data,
			List<CustomField> fields, List<Tag> tags, final CallContext context)
			throws AccountApiException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateAccount(String key, AccountData accountData, final CallContext context)
			throws AccountApiException {
		throw new UnsupportedOperationException();
	}

    @Override
    public void updateAccount(UUID accountId, AccountData accountData, CallContext context)
            throws AccountApiException {
        throw new NotImplementedException();
    }
}
