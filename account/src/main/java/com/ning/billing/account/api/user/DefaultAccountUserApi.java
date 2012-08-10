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

import javax.annotation.Nullable;

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
import com.ning.billing.util.entity.EntityPersistenceException;

public class DefaultAccountUserApi implements AccountUserApi {
    private final CallContextFactory factory;
    private final AccountDao accountDao;
    private final AccountEmailDao accountEmailDao;

    @Inject
    public DefaultAccountUserApi(final CallContextFactory factory, final AccountDao accountDao, final AccountEmailDao accountEmailDao) {
        this.factory = factory;
        this.accountDao = accountDao;
        this.accountEmailDao = accountEmailDao;
    }

    @Override
    public Account createAccount(final AccountData data, final CallContext context) throws AccountApiException {
        final Account account = new DefaultAccount(data);

        try {
            accountDao.create(account, context);
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
    public Account migrateAccount(final MigrationAccountData data, final CallContext context)
            throws AccountApiException {
        final DateTime createdDate = data.getCreatedDate() == null ? context.getCreatedDate() : data.getCreatedDate();
        final DateTime updatedDate = data.getUpdatedDate() == null ? context.getUpdatedDate() : data.getUpdatedDate();
        final CallContext migrationContext = factory.toMigrationCallContext(context, createdDate, updatedDate);
        final Account account = new DefaultAccount(data);

        try {
            accountDao.create(account, migrationContext);
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

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        accountEmailDao.addEmail(accountId, email, context);
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        accountEmailDao.removeEmail(accountId, email, context);
    }

    @Override
    public void removePaymentMethod(final UUID accountId, final CallContext context) throws AccountApiException {
        updatePaymentMethod(accountId, null, context);
    }


    @Override
    public void updatePaymentMethod(final UUID accountId, @Nullable final UUID paymentMethodId, final CallContext context) throws AccountApiException {
        try {
            accountDao.updatePaymentMethod(accountId, paymentMethodId, context);
        }  catch (EntityPersistenceException e) {
            throw new AccountApiException(e, e.getCode(), e.getMessage());
        }
    }
}
