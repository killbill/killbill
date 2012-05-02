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

package com.ning.billing.account.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.tag.TagDefinition;

import javax.annotation.Nullable;

public interface AccountUserApi {
    public Account createAccount(AccountData data, @Nullable List<CustomField> fields,
                                 @Nullable List<TagDefinition> tagDefinitions, CallContext context) throws AccountApiException;

    public Account migrateAccount(MigrationAccountData data, @Nullable List<CustomField> fields,
                                  @Nullable List<TagDefinition> tagDefinitions, CallContext context) throws AccountApiException;

    /***
     *
     * Note: does not update the external key
     * @param account account to be updated
     * @param context contains specific information about the call
     * @throws AccountApiException if a failure occurs
     */
    public void updateAccount(Account account, CallContext context) throws AccountApiException;

    public void updateAccount(String key, AccountData accountData, CallContext context) throws AccountApiException;

    public void updateAccount(UUID accountId, AccountData accountData, CallContext context) throws AccountApiException;

    public Account getAccountByKey(String key);

    public Account getAccountById(UUID accountId);

    public List<Account> getAccounts();

    public UUID getIdFromKey(String externalKey) throws AccountApiException;

    public List<AccountEmail> getEmails(UUID accountId);

    public void saveEmails(UUID accountId, List<AccountEmail> emails, CallContext context);
}
