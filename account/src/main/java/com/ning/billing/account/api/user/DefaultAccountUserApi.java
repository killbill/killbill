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

import java.util.List;
import java.util.UUID;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;

public class DefaultAccountUserApi implements com.ning.billing.account.api.AccountUserApi {
    private final AccountDao dao;
	private Clock clock;

    @Inject
    public DefaultAccountUserApi(final AccountDao dao, final Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public Account createAccount(final AccountData data, final List<CustomField> fields, List<Tag> tags) throws AccountApiException {
        Account account = new DefaultAccount(data, clock.getUTCNow());
        account.addFields(fields);
        account.addTags(tags);

        dao.create(account);
        return account;
    }

    @Override
    public Account getAccountByKey(final String key) {
        return dao.getAccountByKey(key);
    }

    @Override
    public Account getAccountById(final UUID id) {
        return dao.getById(id.toString());
    }

    @Override
    public List<Account> getAccounts() {
        return dao.get();
    }

    @Override
    public UUID getIdFromKey(final String externalKey) throws AccountApiException {
        return dao.getIdFromKey(externalKey);
    }

    @Override
    public void updateAccount(final Account account) throws AccountApiException {
        dao.update(account);
    }

	@Override
	public void deleteAccountByKey(String externalKey) throws AccountApiException {
		dao.deleteByKey(externalKey);
	}

	@Override
	public Account migrateAccount(MigrationAccountData data,
			List<CustomField> fields, List<Tag> tags)
			throws AccountApiException {
		
		Account account = new DefaultAccount(data);
        account.addFields(fields);
        account.addTags(tags);

        dao.create(account);
        return account;
	}
}
