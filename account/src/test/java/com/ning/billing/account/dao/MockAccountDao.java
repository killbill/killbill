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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.Inject;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;

public class MockAccountDao implements AccountDao {
    private final Bus eventBus;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<UUID, Account>();

    @Inject
    public MockAccountDao(final Bus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(final Account account, final CallContext context) {
        accounts.put(account.getId(), account);

        try {
            eventBus.post(new DefaultAccountCreationEvent(account, null));
        } catch (EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public Account getById(final UUID id) {
        return accounts.get(id);
    }

    @Override
    public List<Account> get() {
        return new ArrayList<Account>(accounts.values());
    }

    @Override
    public void test() {
    }

    @Override
    public Account getAccountByKey(final String externalKey) {
        for (final Account account : accounts.values()) {
            if (externalKey.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public UUID getIdFromKey(final String externalKey) {
        final Account account = getAccountByKey(externalKey);
        return account == null ? null : account.getId();
    }

    @Override
    public void update(final Account account, final CallContext context) {
        final Account currentAccount = accounts.put(account.getId(), account);

        final AccountChangeEvent changeEvent = new DefaultAccountChangeEvent(account.getId(), null, currentAccount, account);
        if (changeEvent.hasChanges()) {
            try {
                eventBus.post(changeEvent);
            } catch (EventBusException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
