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

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultAccountEmail;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailModelDao;
import com.ning.billing.account.dao.AccountModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallContextFactory;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultAccountUserApi implements AccountUserApi {

    private final CallContextFactory callContextFactory;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountDao accountDao;

    @Inject
    public DefaultAccountUserApi(final CallContextFactory callContextFactory, final InternalCallContextFactory internalCallContextFactory,
                                 final AccountDao accountDao) {
        this.callContextFactory = callContextFactory;
        this.internalCallContextFactory = internalCallContextFactory;
        this.accountDao = accountDao;
    }

    @Override
    public Account createAccount(final AccountData data, final CallContext context) throws AccountApiException {
        // Not transactional, but there is a db constraint on that column
        if (getIdFromKey(data.getExternalKey(), context) != null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, data.getExternalKey());
        }

        final AccountModelDao account = new AccountModelDao(data);
        accountDao.create(account, internalCallContextFactory.createInternalCallContext(account.getId(), context));

        return new DefaultAccount(account);
    }

    @Override
    public Account getAccountByKey(final String key, final TenantContext context) throws AccountApiException {
        final AccountModelDao account = accountDao.getAccountByKey(key, internalCallContextFactory.createInternalTenantContext(context));
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }

        return new DefaultAccount(account);
    }

    @Override
    public Account getAccountById(final UUID id, final TenantContext context) throws AccountApiException {
        final AccountModelDao account = accountDao.getById(id, internalCallContextFactory.createInternalTenantContext(context));
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, id);
        }

        return new DefaultAccount(account);
    }

    @Override
    public List<Account> getAccounts(final TenantContext context) {
        final List<AccountModelDao> accountModelDaos = accountDao.get(internalCallContextFactory.createInternalTenantContext(context));
        return ImmutableList.<Account>copyOf(Collections2.transform(accountModelDaos, new Function<AccountModelDao, Account>() {
            @Override
            public Account apply(@Nullable final AccountModelDao input) {
                return new DefaultAccount(input);
            }
        }));
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final TenantContext context) throws AccountApiException {
        return accountDao.getIdFromKey(externalKey, internalCallContextFactory.createInternalTenantContext(context));
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) throws AccountApiException {
        updateAccount(account.getId(), account, context);
    }

    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context) throws AccountApiException {
        final Account currentAccount = getAccountById(accountId, context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }

        updateAccount(currentAccount, accountData, context);
    }

    @Override
    public void updateAccount(final String externalKey, final AccountData accountData, final CallContext context) throws AccountApiException {
        final Account currentAccount = getAccountByKey(externalKey, context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, externalKey);
        }

        updateAccount(currentAccount, accountData, context);
    }

    private void updateAccount(final Account currentAccount, final AccountData accountData, final CallContext context) throws AccountApiException {
        // Set unspecified (null) fields to their current values
        final Account updatedAccount = new DefaultAccount(currentAccount.getId(), accountData);
        final AccountModelDao accountToUpdate = new AccountModelDao(currentAccount.getId(), updatedAccount.mergeWithDelegate(currentAccount));

        accountDao.update(accountToUpdate, internalCallContextFactory.createInternalCallContext(accountToUpdate.getId(), context));
    }

    @Override
    public Account migrateAccount(final MigrationAccountData data, final CallContext context) throws AccountApiException {
        // Create a special (migration) context
        final DateTime createdDate = data.getCreatedDate() == null ? context.getCreatedDate() : data.getCreatedDate();
        final DateTime updatedDate = data.getUpdatedDate() == null ? context.getUpdatedDate() : data.getUpdatedDate();
        final CallContext migrationContext = callContextFactory.toMigrationCallContext(context, createdDate, updatedDate);

        // Create the account
        final Account account = createAccount(data, migrationContext);

        // Add associated contact emails
        // In Killbill, we never return null for empty lists, but MigrationAccountData is implemented outside of Killbill
        if (data.getAdditionalContactEmails() != null) {
            for (final String cur : data.getAdditionalContactEmails()) {
                addEmail(account.getId(), new DefaultAccountEmail(account.getId(), cur), migrationContext);
            }
        }

        return account;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final TenantContext context) {
        return ImmutableList.<AccountEmail>copyOf(Collections2.transform(accountDao.getEmailsByAccountId(accountId, internalCallContextFactory.createInternalTenantContext(context)),
                                                                         new Function<AccountEmailModelDao, AccountEmail>() {
                                                                             @Override
                                                                             public AccountEmail apply(final AccountEmailModelDao input) {
                                                                                 return new DefaultAccountEmail(input);
                                                                             }
                                                                         }));
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final CallContext context) throws AccountApiException {
        accountDao.addEmail(new AccountEmailModelDao(email), internalCallContextFactory.createInternalCallContext(accountId, context));
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        accountDao.removeEmail(new AccountEmailModelDao(email, false), internalCallContextFactory.createInternalCallContext(accountId, context));
    }
}
