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
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.tag.Tag;

public interface AccountUserApi {

    public Account createAccount(AccountData data, List<CustomField> fields, List<Tag> tags) throws AccountApiException;

    /***
     *
     * Note: does not update the external key
     * @param account
     */
    public void updateAccount(Account account) throws AccountApiException;

    public Account getAccountByKey(String key);

    public Account getAccountById(UUID accountId);

    public List<Account> getAccounts();

    public UUID getIdFromKey(String externalKey) throws AccountApiException;

	public void deleteAccountByKey(String externalKey) throws AccountApiException;
}
