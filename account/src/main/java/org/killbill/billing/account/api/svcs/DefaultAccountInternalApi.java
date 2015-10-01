/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.account.api.svcs;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultAccountEmail;
import org.killbill.billing.account.api.DefaultImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.api.MutableAccountData;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.dao.AccountEmailModelDao;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultAccountInternalApi implements AccountInternalApi {

    private final AccountDao accountDao;

    @Inject
    public DefaultAccountInternalApi(final AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    public Account getAccountById(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao account = accountDao.getById(accountId, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }
        return new DefaultAccount(account);
    }

    @Override
    public Account getAccountByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = getAccountModelDaoByRecordId(recordId, context);
        return new DefaultAccount(accountModelDao);
    }

    @Override
    public void updateBCD(final String externalKey, final int bcd,
                              final InternalCallContext context) throws AccountApiException {
        final Account currentAccount = getAccountByKey(externalKey, context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, externalKey);
        }

        final MutableAccountData mutableAccountData = currentAccount.toMutableAccountData();
        mutableAccountData.setBillCycleDayLocal(bcd);
        final AccountModelDao accountToUpdate = new AccountModelDao(currentAccount.getId(), mutableAccountData);
        accountDao.update(accountToUpdate, context);
    }

    @Override
    public int getBCD(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        final Account account = getAccountById(accountId, context);
        return account.getBillCycleDayLocal();
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
    public Account getAccountByKey(final String key, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = accountDao.getAccountByKey(key, context);
        if (accountModelDao == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }
        return new DefaultAccount(accountModelDao);
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
        final Account account = getAccountById(accountId, context);
        return new DefaultImmutableAccountData(account);
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataByKey(final String key, final InternalTenantContext context) throws AccountApiException {
        final Account account = getAccountByKey(key, context);
        return new DefaultImmutableAccountData(account);
    }

    @Override
    public ImmutableAccountData getImmutableAccountDataByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final Account account = getAccountByRecordId(recordId, context);
        return new DefaultImmutableAccountData(account);
    }

    private AccountModelDao getAccountModelDaoByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = accountDao.getByRecordId(recordId, context);
        if (accountModelDao == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_RECORD_ID, recordId);
        }
        return accountModelDao;
    }


}
