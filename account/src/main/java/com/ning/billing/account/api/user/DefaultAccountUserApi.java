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

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.tag.TagDefinition;
import com.ning.billing.util.tag.dao.TagDao;

public class DefaultAccountUserApi implements AccountUserApi {
    private final CallContextFactory factory;
    private final AccountDao accountDao;
    private final AccountEmailDao accountEmailDao;
    private final TagDao tagDao;
    private final CustomFieldDao customFieldDao;

    @Inject
    public DefaultAccountUserApi(final CallContextFactory factory, final AccountDao accountDao,
                                 final AccountEmailDao accountEmailDao, final TagDao tagDao,
                                 final CustomFieldDao customFieldDao) {
        this.factory = factory;
        this.accountDao = accountDao;
        this.accountEmailDao = accountEmailDao;
        this.tagDao = tagDao;
        this.customFieldDao = customFieldDao;
    }

    @Override
    public Account createAccount(final AccountData data, @Nullable final List<CustomField> fields,
                                 @Nullable final List<TagDefinition> tagDefinitions, final CallContext context) throws AccountApiException {
        final Account account = new DefaultAccount(data);

        try {
            // TODO: move this into a transaction?
            accountDao.create(account, context);
            if (tagDefinitions != null) {
                tagDao.insertTags(account.getId(), ObjectType.ACCOUNT, tagDefinitions, context);
            }

            if (fields != null) {
                customFieldDao.saveEntities(account.getId(), ObjectType.ACCOUNT, fields, context);
            }
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_CREATION_FAILED);
        }

        return account;
    }

    @Override
    public Account getAccountByKey(final String key) throws AccountApiException {
        final Account account = accountDao.getAccountByKey(key);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }
        return account;
    }

    @Override
    public Account getAccountById(final UUID id) throws AccountApiException {
        final Account account = accountDao.getById(id);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, id);
        }
        return account;
    }

    @Override
    public List<Account> getAccounts() {
        return accountDao.get();
    }

    @Override
    public UUID getIdFromKey(final String externalKey) throws AccountApiException {
        return accountDao.getIdFromKey(externalKey);
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) throws AccountApiException {
        try {
            accountDao.update(account, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_UPDATE_FAILED);
        }
    }

    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        final Account account = new DefaultAccount(accountId, accountData);

        try {
            accountDao.update(account, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, e.getCode(), e.getMessage());
        }
    }

    @Override
    public void updateAccount(final String externalKey, final AccountData accountData, final CallContext context) throws AccountApiException {
        final UUID accountId = getIdFromKey(externalKey);
        if (accountId == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, externalKey);
        }
        updateAccount(accountId, accountData, context);
    }

    @Override
    public Account migrateAccount(final MigrationAccountData data, final List<CustomField> fields,
                                  final List<TagDefinition> tagDefinitions, final CallContext context)
            throws AccountApiException {
        final DateTime createdDate = data.getCreatedDate() == null ? context.getCreatedDate() : data.getCreatedDate();
        final DateTime updatedDate = data.getUpdatedDate() == null ? context.getUpdatedDate() : data.getUpdatedDate();
        final CallContext migrationContext = factory.toMigrationCallContext(context, createdDate, updatedDate);
        final Account account = new DefaultAccount(data);

        try {
            // TODO: move this into a transaction?
            accountDao.create(account, migrationContext);
            tagDao.insertTags(account.getId(), ObjectType.ACCOUNT, tagDefinitions, context);
            customFieldDao.saveEntities(account.getId(), ObjectType.ACCOUNT, fields, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_CREATION_FAILED);
        }

        return account;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId) {
        return accountEmailDao.getEmails(accountId);
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> newEmails, final CallContext context) {
        accountEmailDao.saveEmails(accountId, newEmails, context);
    }
}
