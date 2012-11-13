/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;

public class MockAccountEmailDao extends MockEntityDaoBase<AccountEmailModelDao, AccountEmail, AccountApiException> implements AccountEmailDao {

    @Override
    public List<AccountEmailModelDao> getByAccountId(final UUID accountId, final InternalTenantContext context) {
        final List<AccountEmailModelDao> accountEmails = new ArrayList<AccountEmailModelDao>();
        for (final Map<Long, AccountEmailModelDao> accountEmail : entities.values()) {
            final AccountEmailModelDao email = accountEmail.values().iterator().next();
            if (email.getAccountId().equals(accountId)) {
                accountEmails.add(email);
            }
        }

        return accountEmails;
    }
}
