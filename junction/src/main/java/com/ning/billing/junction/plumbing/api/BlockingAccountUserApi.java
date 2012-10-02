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

package com.ning.billing.junction.plumbing.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.glue.RealImplementation;

import com.google.inject.Inject;

public class BlockingAccountUserApi implements AccountUserApi {

    private final AccountUserApi userApi;
    private final BlockingApi blockingApi;

    @Inject
    public BlockingAccountUserApi(@RealImplementation final AccountUserApi userApi, final BlockingApi blockingApi) {
        this.userApi = userApi;
        this.blockingApi = blockingApi;
    }

    @Override
    public Account createAccount(final AccountData data, final CallContext context)
            throws AccountApiException {
        return userApi.createAccount(data, context);
    }

    @Override
    public Account migrateAccount(final MigrationAccountData data, final CallContext context) throws AccountApiException {
        return userApi.migrateAccount(data, context);
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) throws AccountApiException {
        userApi.updateAccount(account, context);
    }

    @Override
    public void updateAccount(final String key, final AccountData accountData, final CallContext context) throws AccountApiException {
        userApi.updateAccount(key, accountData, context);
    }

    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context) throws AccountApiException {
        userApi.updateAccount(accountId, accountData, context);
    }

    @Override
    public Account getAccountByKey(final String key, final TenantContext context) throws AccountApiException {
        return new BlockingAccount(userApi.getAccountByKey(key, context), blockingApi, context);
    }

    @Override
    public Account getAccountById(final UUID accountId, final TenantContext context) throws AccountApiException {
        return userApi.getAccountById(accountId, context);
    }

    @Override
    public List<Account> getAccounts(final TenantContext context) {
        return userApi.getAccounts(context);
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final TenantContext context) throws AccountApiException {
        return userApi.getIdFromKey(externalKey, context);
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final TenantContext context) {
        return userApi.getEmails(accountId, context);
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> emails, final CallContext context) {
        userApi.saveEmails(accountId, emails, context);
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        userApi.addEmail(accountId, email, context);
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        userApi.removeEmail(accountId, email, context);
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, CallContext context) throws AccountApiException {
        userApi.updatePaymentMethod(accountId, paymentMethodId, context);
    }

    @Override
    public void removePaymentMethod(final UUID accountId, final CallContext context) throws AccountApiException {
        userApi.removePaymentMethod(accountId, context);
    }
}
