/*
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

import java.util.UUID;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultImmutableAccountData;
import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.dao.AccountModelDao;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;

public class DefaultAccountApiBase {

    private final AccountDao accountDao;
    private final CacheController<Long, ImmutableAccountData> accountCacheController;
    private final CacheController<String, Long> recordIdCacheController;
    private final NonEntityDao nonEntityDao;

    public DefaultAccountApiBase(final AccountDao accountDao,
                                 final NonEntityDao nonEntityDao,
                                 final CacheControllerDispatcher cacheControllerDispatcher) {
        this.accountDao = accountDao;
        this.nonEntityDao = nonEntityDao;
        this.accountCacheController = cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_IMMUTABLE);
        this.recordIdCacheController = cacheControllerDispatcher.getCacheController(CacheType.RECORD_ID);
    }

    protected Account getAccountById(final UUID accountId, final InternalTenantContext context) throws AccountApiException {
        final Long recordId = nonEntityDao.retrieveRecordIdFromObject(accountId, ObjectType.ACCOUNT, recordIdCacheController);
        final Account account = getAccountByRecordIdInternal(recordId, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }
        accountCacheController.putIfAbsent(recordId, new DefaultImmutableAccountData(account));
        return account;
    }

    protected Account getAccountByKey(final String key, final InternalTenantContext context) throws AccountApiException {
        final AccountModelDao accountModelDao = accountDao.getAccountByKey(key, context);
        if (accountModelDao == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_KEY, key);
        }
        final Account account = new DefaultAccount(accountModelDao);
        final Long recordId = nonEntityDao.retrieveRecordIdFromObject(account.getId(), ObjectType.ACCOUNT, recordIdCacheController);
        accountCacheController.putIfAbsent(recordId, new DefaultImmutableAccountData(account));
        return account;
    }

    protected Account getAccountByRecordId(final Long recordId, final InternalTenantContext context) throws AccountApiException {
        final Account account = getAccountByRecordIdInternal(recordId, context);
        if (account == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_RECORD_ID, recordId);
        }
        return account;
    }

    protected Account getAccountByRecordIdInternal(final Long recordId, final InternalTenantContext context) {
        final AccountModelDao accountModelDao = accountDao.getByRecordId(recordId, context);
        return accountModelDao != null ? new DefaultAccount(accountModelDao) : null;
    }
}
