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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.EntityPersistenceException;

import com.google.common.collect.ImmutableList;

public class MockAccountEmailDao implements AccountEmailDao {

    private final Map<UUID, Set<AccountEmail>> emails = new ConcurrentHashMap<UUID, Set<AccountEmail>>();

    @Override
    public void create(final AccountEmail entity, final InternalCallContext context) throws EntityPersistenceException {
        saveEmails(entity.getAccountId(), ImmutableList.<AccountEmail>of(entity), context);
    }

    @Override
    public Long getRecordId(final UUID id, final InternalTenantContext context) {
        return 1L;
    }

    @Override
    public AccountEmail getById(final UUID id, final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountEmail> get(final InternalTenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final InternalTenantContext context) {
        final Set<AccountEmail> accountEmails = emails.get(accountId);
        if (accountEmails == null) {
            return ImmutableList.<AccountEmail>of();
        } else {
            return ImmutableList.<AccountEmail>copyOf(accountEmails.iterator());
        }
    }

    @Override
    public void saveEmails(final UUID accountId, final List<AccountEmail> newEmails, final InternalCallContext context) {
        if (emails.get(accountId) == null) {
            emails.put(accountId, new HashSet<AccountEmail>());
        }

        emails.get(accountId).addAll(newEmails);
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final InternalCallContext context) {
        if (emails.get(accountId) == null) {
            emails.put(accountId, new HashSet<AccountEmail>());
        }

        emails.get(accountId).add(email);
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final InternalCallContext context) {
        if (emails.get(accountId) == null) {
            emails.put(accountId, new HashSet<AccountEmail>());
        }

        emails.get(accountId).remove(email);
    }

    @Override
    public void test(final InternalTenantContext context) {
    }
}
