/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.account.api.user;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultAccountEmail;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.dao.AccountEmailModelDao;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationHelper.SourcePaginationBuilder;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

import static org.killbill.billing.util.entity.dao.DefaultPaginationHelper.getEntityPaginationNoException;

public class DefaultAccountUserApi extends DefaultAccountApiBase implements AccountUserApi {

    private final ImmutableAccountInternalApi immutableAccountInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountDao accountDao;

    @Inject
    public DefaultAccountUserApi(final ImmutableAccountInternalApi immutableAccountInternalApi,
                                 final AccountDao accountDao,
                                 final NonEntityDao nonEntityDao,
                                 final CacheControllerDispatcher cacheControllerDispatcher,
                                 final InternalCallContextFactory internalCallContextFactory) {
        super(accountDao, nonEntityDao, cacheControllerDispatcher);
        this.immutableAccountInternalApi = immutableAccountInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.accountDao = accountDao;
    }


    @Override
    public Account getAccountByKey(final String key, final TenantContext context) throws AccountApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context);
        return getAccountByKey(key, internalTenantContext);
    }

    @Override
    public Account getAccountById(final UUID id, final TenantContext context) throws AccountApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(id, context);
        return getAccountById(id, internalTenantContext);
    }


    @Override
    public Account createAccount(final AccountData data, final CallContext context) throws AccountApiException {
        // Not transactional, but there is a db constraint on that column
        if (data.getExternalKey() != null && getIdFromKey(data.getExternalKey(), context) != null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_ALREADY_EXISTS, data.getExternalKey());
        }

        final InternalCallContext internalContext = internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(context);

        if (data.getParentAccountId() != null) {
            // verify that parent account exists if parentAccountId is not null
            final ImmutableAccountData immutableAccountData = immutableAccountInternalApi.getImmutableAccountDataById(data.getParentAccountId(), internalContext);
            if (immutableAccountData == null) {
                throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, data.getParentAccountId());
            }
        }

        final AccountModelDao account = new AccountModelDao(data);
        if (null != account.getExternalKey() && account.getExternalKey().length() > 255) {
            throw new AccountApiException(ErrorCode.EXTERNAL_KEY_LIMIT_EXCEEDED);
        }

        accountDao.create(account, internalCallContextFactory.createInternalCallContextWithoutAccountRecordId(context));

        return new DefaultAccount(account);
    }


    @Override
    public Pagination<Account> searchAccounts(final String searchKey, final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<AccountModelDao, AccountApiException>() {
                                                  @Override
                                                  public Pagination<AccountModelDao> build() {
                                                      return accountDao.searchAccounts(searchKey, offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              new Function<AccountModelDao, Account>() {
                                                  @Override
                                                  public Account apply(final AccountModelDao accountModelDao) {
                                                      return new DefaultAccount(accountModelDao);
                                                  }
                                              }
                                             );
    }

    @Override
    public Pagination<Account> getAccounts(final Long offset, final Long limit, final TenantContext context) {
        return getEntityPaginationNoException(limit,
                                              new SourcePaginationBuilder<AccountModelDao, AccountApiException>() {
                                                  @Override
                                                  public Pagination<AccountModelDao> build() {
                                                      return accountDao.get(offset, limit, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
                                                  }
                                              },
                                              new Function<AccountModelDao, Account>() {
                                                  @Override
                                                  public Account apply(final AccountModelDao accountModelDao) {
                                                      return new DefaultAccount(accountModelDao);
                                                  }
                                              }
                                             );
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final TenantContext context) throws AccountApiException {
        return accountDao.getIdFromKey(externalKey, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context));
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) throws AccountApiException {

        // Convert to DefaultAccount to make sure we can safely call validateAccountUpdateInput
        final DefaultAccount input = new DefaultAccount(account.getId(), account);

        final Account currentAccount = getAccountById(input.getId(), context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, input.getId());
        }

        input.validateAccountUpdateInput(currentAccount, true);

        final AccountModelDao updatedAccountModelDao = new AccountModelDao(currentAccount.getId(),  input);

        accountDao.update(updatedAccountModelDao, internalCallContextFactory.createInternalCallContext(updatedAccountModelDao.getId(), context));
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
        final Account updatedAccount = new DefaultAccount(currentAccount.getId(), accountData);

        // Set unspecified (null) fields to their current values
        final Account mergedAccount = updatedAccount.mergeWithDelegate(currentAccount);

        final AccountModelDao updatedAccountModelDao = new AccountModelDao(currentAccount.getId(), mergedAccount);

        accountDao.update(updatedAccountModelDao, internalCallContextFactory.createInternalCallContext(updatedAccountModelDao.getId(), context));
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final TenantContext context) {
        return ImmutableList.<AccountEmail>copyOf(Collections2.transform(accountDao.getEmailsByAccountId(accountId, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)),
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

    @Override
    public List<Account> getChildrenAccounts(final UUID parentAccountId, final TenantContext context) throws AccountApiException {
        return ImmutableList.<Account>copyOf(Collections2.transform(accountDao.getAccountsByParentId(parentAccountId, internalCallContextFactory.createInternalTenantContextWithoutAccountRecordId(context)),
                                                                         new Function<AccountModelDao, Account>() {
                                                                             @Override
                                                                             public Account apply(final AccountModelDao input) {
                                                                                 return new DefaultAccount(input);
                                                                             }
                                                                         }));
    }

    @Override
    public List<AuditLogWithHistory> getAuditLogsWithHistoryForId(final UUID accountId, final AuditLevel auditLevel, final TenantContext tenantContext) throws AccountApiException {
        return accountDao.getAuditLogsWithHistoryForId(accountId, auditLevel, internalCallContextFactory.createInternalTenantContext(accountId, tenantContext));
    }

    @Override
    public List<AuditLogWithHistory> getEmailAuditLogsWithHistoryForId(final UUID accountEmailId, final AuditLevel auditLevel, final TenantContext tenantContext) throws AccountApiException {
        return accountDao.getEmailAuditLogsWithHistoryForId(accountEmailId, auditLevel, internalCallContextFactory.createInternalTenantContext(tenantContext.getAccountId(), tenantContext));
    }
}
