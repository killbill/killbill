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
import com.ning.billing.util.callcontext.TenantContext;

/**
 * The interface {@code AccountUserApi} offers APIs related to account operations.
 */
public interface AccountUserApi {

    /**
     *
     * @param data      the account data
     * @param context   the user context
     * @return          the created Account
     *
     * @throws AccountApiException
     */
    public Account createAccount(AccountData data, CallContext context) throws AccountApiException;

    /**
     *
     * @param data      the account data
     * @param context   the user context
     * @return          the migrated account
     *
     * @throws AccountApiException
     */
    public Account migrateAccount(MigrationAccountData data, CallContext context) throws AccountApiException;

    /**
     * Updates the account by specifying the destination {@code Account} object
     * <p>
     *
     * @param account account to be updated
     * @param context contains specific information about the call
     *
     * @throws AccountApiException if a failure occurs
     */
    public void updateAccount(Account account, CallContext context) throws AccountApiException;

    /**
     * Updates the account by specifying the {@code AccountData} object
     * <p>
     *
     * @param key account external key
     * @param context contains specific information about the call
     *
     * @throws AccountApiException if a failure occurs
     */
    public void updateAccount(String key, AccountData accountData, CallContext context) throws AccountApiException;

    /**
     * Updates the account by specifying the {@code AccountData} object
     * <p>
     *
     * @param accountId account unique id
     * @param context contains specific information about the call
     *
     * @throws AccountApiException if a failure occurs
     */
    public void updateAccount(UUID accountId, AccountData accountData, CallContext context) throws AccountApiException;

    /**
     *
     * @param key       the externalKey for the account
     * @param context   the user context
     * @return          the account
     *
     * @throws AccountApiException if there is no such account
     */
    public Account getAccountByKey(String key, TenantContext context) throws AccountApiException;

    /**
     *
     * @param accountId the unique id for the account
     * @param context   the user context
     * @return          the account
     *
     * @throws AccountApiException if there is no such account
     */
    public Account getAccountById(UUID accountId, TenantContext context) throws AccountApiException;

    /**
     *
     * @param context   the user context
     * @return          the list of accounts for that tenant
     */
    public List<Account> getAccounts(TenantContext context);

    /**
     *
     * @param externalKey   the externalKey for the account
     * @param context       the user context
     * @return              the unique id for that account
     *
     * @throws AccountApiException  if there is no such account
     */
    public UUID getIdFromKey(String externalKey, TenantContext context) throws AccountApiException;

    /**
     *
     * @param accountId the account unique id
     * @param context   the user context
     * @return          the laist of emails configured for that account
     */
    public List<AccountEmail> getEmails(UUID accountId, TenantContext context);

    /**
     *
     * @param accountId the account unique id
     * @param email     the email to be added
     * @param context   the user context
     */
    public void addEmail(UUID accountId, AccountEmail email, CallContext context);

    /**
     *
     * @param accountId the account unique id
     * @param email     the email to be removed
     * @param context   the user context
     */
    public void removeEmail(UUID accountId, AccountEmail email, CallContext context);
}
