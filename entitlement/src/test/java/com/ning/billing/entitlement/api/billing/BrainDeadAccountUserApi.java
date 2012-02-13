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

package com.ning.billing.entitlement.api.billing;

import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.Tag;

public class BrainDeadAccountUserApi implements AccountUserApi {


	@Override
	public Account getAccountByKey(String key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Account getAccountById(UUID accountId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Account> getAccounts() {
		throw new UnsupportedOperationException();
	}

	@Override
	public UUID getIdFromKey(String externalKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Account createAccount(AccountData data, List<CustomField> fields,
			List<Tag> tags) throws AccountApiException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateAccount(Account account) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void deleteAccountByKey(String externalKey)
			throws AccountApiException {
		throw new UnsupportedOperationException();
	}

	@Override
	public Account migrateAccount(MigrationAccountData data,
			List<CustomField> fields, List<Tag> tags)
			throws AccountApiException {
		throw new UnsupportedOperationException();
	}

}
