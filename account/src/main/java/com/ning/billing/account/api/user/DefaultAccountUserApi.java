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
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.Tag;
import org.joda.time.DateTime;

public class DefaultAccountUserApi implements com.ning.billing.account.api.AccountUserApi {
    private final CallContextFactory factory;
    private final AccountDao dao;

    @Inject
    public DefaultAccountUserApi(final CallContextFactory factory, final AccountDao dao) {
        this.factory = factory;
        this.dao = dao;
    }

    @Override
    public Account createAccount(final AccountData data, final List<CustomField> fields,
                                 final List<Tag> tags, final CallContext context) throws AccountApiException {
        Account account = new DefaultAccount(data);
        account.setFields(fields);
        account.addTags(tags);

        try {
            dao.create(account, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_CREATION_FAILED);
        }

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
    public void updateAccount(final Account account, final CallContext context) throws AccountApiException {
        try {
            dao.update(account, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_UPDATE_FAILED);
        }
    }
    
    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        Account account = new DefaultAccount(accountId, accountData);

        try {
            dao.update(account, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public void updateAccount(final String externalKey, final AccountData accountData, final CallContext context) throws AccountApiException {
    	UUID accountId = getIdFromKey(externalKey);
    	if (accountId == null) {
    		throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, externalKey);
    	}
    	updateAccount(accountId, accountData, context);
     }

	@Override
	public Account migrateAccount(final MigrationAccountData data, final List<CustomField> fields,
                                  final List<Tag> tags, final CallContext context)
            throws AccountApiException {
        DateTime createdDate = data.getCreatedDate() == null ? context.getCreatedDate() : data.getCreatedDate();
        DateTime updatedDate = data.getUpdatedDate() == null ? context.getUpdatedDate() : data.getUpdatedDate();
        CallContext migrationContext = factory.toMigrationCallContext(context, createdDate, updatedDate);
		Account account = new DefaultAccount(data);
        account.setFields(fields);
        account.addTags(tags);

        try {
            dao.create(account, migrationContext);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_CREATION_FAILED);
        }

        return account;
	}

    @Override
    public List<AccountEmail> getEmails(final UUID accountId) {
        return dao.getEmails(accountId);
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> newEmails, final CallContext context) {
        dao.saveEmails(accountId, newEmails, context);
    }
}
