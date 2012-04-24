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

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.api.MigrationAccountData;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.tag.Tag;

public class BlockingAccountUserApi implements AccountUserApi {
    
    private AccountUserApi userApi;

    @Inject
    public BlockingAccountUserApi(@RealImplementation AccountUserApi userApi) {
        this.userApi = userApi;
    }

    @Override
    public Account createAccount(AccountData data, List<CustomField> fields, List<Tag> tags, CallContext context)
            throws AccountApiException {
        return userApi.createAccount(data, fields, tags, context);
    }

    @Override
    public Account migrateAccount(MigrationAccountData data, List<CustomField> fields, List<Tag> tags,
            CallContext context) throws AccountApiException {
        return userApi.migrateAccount(data, fields, tags, context);
    }

    @Override
    public void updateAccount(Account account, CallContext context) throws AccountApiException {
        userApi.updateAccount(account, context);
    }

    @Override
    public void updateAccount(String key, AccountData accountData, CallContext context) throws AccountApiException {
        userApi.updateAccount(key, accountData, context);
    }

    @Override
    public void updateAccount(UUID accountId, AccountData accountData, CallContext context) throws AccountApiException {
        userApi.updateAccount(accountId, accountData, context);
    }

    @Override
    public Account getAccountByKey(String key) {
        return userApi.getAccountByKey(key);
    }

    @Override
    public Account getAccountById(UUID accountId) {
        return userApi.getAccountById(accountId);
    }

    @Override
    public List<Account> getAccounts() {
        return userApi.getAccounts();
    }

    @Override
    public UUID getIdFromKey(String externalKey) throws AccountApiException {
        return userApi.getIdFromKey(externalKey);
    }

    @Override
    public List<AccountEmail> getEmails(UUID accountId) {
        return userApi.getEmails(accountId);
    }

    @Override
    public void saveEmails(UUID accountId, List<AccountEmail> emails, CallContext context) {
        userApi.saveEmails(accountId, emails, context);
    }

}
