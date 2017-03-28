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

package org.killbill.billing.account.api.svcs;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultAccountEmail;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.account.api.user.DefaultAccountApiBase;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.dao.AccountEmailModelDao;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.AccountBCDCacheLoader;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.dao.NonEntityDao;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultAccountInternalApi extends DefaultAccountApiBase implements AccountInternalApi {

    private final ImmutableAccountInternalApi immutableAccountInternalApi;
    private final AccountDao accountDao;
    private final CacheController<UUID, Integer> bcdCacheController;

    @Inject
    public DefaultAccountInternalApi(final ImmutableAccountInternalApi immutableAccountInternalApi,
                                     final AccountDao accountDao,
                                     final NonEntityDao nonEntityDao,
                                     final CacheControllerDispatcher cacheControllerDispatcher) {
        super(accountDao, nonEntityDao, cacheControllerDispatcher);
        this.immutableAccountInternalApi = immutableAccountInternalApi;
        this.accountDao = accountDao;
        this.bcdCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_BCD);
    }

    @Override
    public Account getAccountById(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        return super.getAccountById(accountId, context);
    }

    @Override
    public Account getAccountByKey(final String key, final InternalTenantContext context) throws AccountApiException {
        return super.getAccountByKey(key, context);
    }

    @Override
    public Account getAccountByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        return super.getAccountByRecordId(recordId, context);
    }

    @Override
    public void updateBCD(final String externalKey, final int bcd,
                          final InternalCallContext context) throws AccountApiException {
        final Account currentAccount = getAccountByKey(externalKey, context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, externalKey);
        }
        if (currentAccount.getBillCycleDayLocal() != DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL) {
            throw new AccountApiException(ErrorCode.ACCOUNT_UPDATE_FAILED);
        }

        final MutableAccountData mutableAccountData = currentAccount.toMutableAccountData();
        mutableAccountData.setBillCycleDayLocal(bcd);
        final AccountModelDao accountToUpdate = new AccountModelDao(currentAccount.getId(), mutableAccountData);
        bcdCacheController.remove(currentAccount.getId());
        bcdCacheController.putIfAbsent(currentAccount.getId(), new Integer(bcd));
        accountDao.update(accountToUpdate, context);
    }

    @Override
    public int getBCD(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        final CacheLoaderArgument arg = createBCDCacheLoaderArgument(context);
        final Integer result = bcdCacheController.get(accountId, arg);
        return result != null ? result : DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId,
                                        final InternalTenantContext context) {
        return ImmutableList.<AccountEmail>copyOf(Collections2.transform(accountDao.getEmailsByAccountId(accountId, context),
                                                                         new Function<AccountEmailModelDao, AccountEmail>() {
                                                                             @Override
                                                                             public AccountEmail apply(final AccountEmailModelDao input) {
                                                                                 return new DefaultAccountEmail(input);
                                                                             }
                                                                         }));
    }

    @Override
    public void removePaymentMethod(final UUID accountId, final InternalCallContext context) throws AccountApiException {
        updatePaymentMethod(accountId, null, context);
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId,
                                    final InternalCallContext context) throws AccountApiException {
        accountDao.updatePaymentMethod(accountId, paymentMethodId, context);
    }

    @Override
    public UUID getByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = getAccountModelDaoByRecordId(recordId, context);
        return accountModelDao.getId();
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataById(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        return immutableAccountInternalApi.getImmutableAccountDataById(accountId, context);
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        return immutableAccountInternalApi.getImmutableAccountDataByRecordId(recordId, context);
    }

    private AccountModelDao getAccountModelDaoByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = accountDao.getByRecordId(recordId, context);
        if (accountModelDao == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_RECORD_ID, recordId);
        }
        return accountModelDao;
    }

    private CacheLoaderArgument createBCDCacheLoaderArgument(final InternalTenantContext context) {
        final AccountBCDCacheLoader.LoaderCallback loaderCallback = new AccountBCDCacheLoader.LoaderCallback() {
            @Override
            public Integer loadAccountBCD(final UUID accountId, final InternalTenantContext context) {
                Integer result = accountDao.getAccountBCD(accountId, context);
                if (result != null) {
                    // If the value is 0, then account BCD was not set so we don't want to create a cache entry
                    result = result.equals(DefaultMutableAccountData.DEFAULT_BILLING_CYCLE_DAY_LOCAL) ? null : result;
                }
                return result;
            }
        };
        final Object[] args = new Object[1];
        args[0] = loaderCallback;
        final ObjectType irrelevant = null;
        return new CacheLoaderArgument(irrelevant, args, context);
    }

    @Override
    public List<Account> getChildrenAccounts(final UUID parentAccountId, final InternalCallContext context) throws AccountApiException {
        return ImmutableList.<Account>copyOf(Collections2.transform(accountDao.getAccountsByParentId(parentAccountId, context),
                                                                    new Function<AccountModelDao, Account>() {
                                                                        @Override
                                                                        public Account apply(final AccountModelDao input) {
                                                                            return new DefaultAccount(input);
                                                                        }
                                                                    }));
    }
}
