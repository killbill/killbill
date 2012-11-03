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

package com.ning.billing.account.api.svcs;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.AccountEmailDao;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

public class DefaultAccountInternalApi implements AccountInternalApi {

    private final AccountDao accountDao;
    private final AccountEmailDao accountEmailDao;

    @Inject
    public DefaultAccountInternalApi(final AccountDao accountDao, final AccountEmailDao accountEmailDao) {
        this.accountDao = accountDao;
        this.accountEmailDao = accountEmailDao;
    }

    @Override
    public Account getAccountById(final UUID accountId, final InternalTenantContext context)
            throws AccountApiException {
        final Account account = accountDao.getById(accountId, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }
        return account;
    }

    @Override
    public Account getAccountByRecordId(final Long recordId,
                                        final InternalTenantContext context) throws AccountApiException {
        return accountDao.getByRecordId(recordId, context);
    }

    @Override
    public void updateAccount(final String externalKey, final AccountData accountData,
                              final InternalCallContext context) throws AccountApiException {
        try {
            final Account account = getAccountByKey(externalKey, context);
            final Account updatedAccount = new DefaultAccount(account.getId(), accountData);
            accountDao.update(updatedAccount, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID);
        }
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId,
                                        final InternalTenantContext context) {
        return accountEmailDao.getEmails(accountId, context);
    }

    @Override
    public Account getAccountByKey(final String key, final InternalTenantContext context)
            throws AccountApiException {
        final Account account = accountDao.getAccountByKey(key, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }
        return account;
    }

    @Override
    public void removePaymentMethod(final UUID accountId, final InternalCallContext context)
            throws AccountApiException {
        updatePaymentMethod(accountId, null, context);
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId,
                                    final InternalCallContext context) throws AccountApiException {
        try {
            accountDao.updatePaymentMethod(accountId, paymentMethodId, context);
        } catch (final EntityPersistenceException e) {
            throw new AccountApiException(e, e.getCode(), e.getMessage());
        }
    }
}
