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
    public Account getAccountById(UUID accountId, InternalTenantContext context)
            throws AccountApiException {
        final Account account = accountDao.getById(accountId, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }
        return account;
    }

    @Override
    public void updateAccount(String externalKey, AccountData accountData,
            InternalCallContext context) throws AccountApiException {
        final Account account = getAccountByKey(externalKey, context);
        try {
            accountDao.update(account,context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, ErrorCode.ACCOUNT_UPDATE_FAILED);
        }
    }

    @Override
    public List<AccountEmail> getEmails(UUID accountId,
            InternalTenantContext context)  {
        return accountEmailDao.getEmails(accountId, context);
    }

    @Override
    public Account getAccountByKey(String key, InternalTenantContext context)
            throws AccountApiException {
        final Account account = accountDao.getAccountByKey(key, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }
        return account;
    }

    @Override
    public void removePaymentMethod(UUID accountId, InternalCallContext context)
            throws AccountApiException {
        updatePaymentMethod(accountId, null, context);
    }

    @Override
    public void updatePaymentMethod(UUID accountId, UUID paymentMethodId,
            InternalCallContext context) throws AccountApiException {
        try {
            accountDao.updatePaymentMethod(accountId, paymentMethodId, context);
        } catch (EntityPersistenceException e) {
            throw new AccountApiException(e, e.getCode(), e.getMessage());
        }
    }
}
