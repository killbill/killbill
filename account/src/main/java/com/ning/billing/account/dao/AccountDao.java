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

package com.ning.billing.account.dao;

import java.util.List;
import java.util.UUID;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.UpdatableEntityDao;

import javax.annotation.Nullable;

public interface AccountDao extends UpdatableEntityDao<Account> {
    public Account getAccountByKey(String key);

    /***
     *
     * @param externalKey
     * @return
     * @throws AccountApiException when externalKey is null
     */
    public UUID getIdFromKey(String externalKey) throws AccountApiException;

    public List<AccountEmail> getEmails(UUID accountId);

    public void saveEmails(UUID accountId, List<AccountEmail> emails, CallContext context);
}