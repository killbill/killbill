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

import java.util.Map;
import java.util.UUID;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultMutableAccountData;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.inject.Inject;

public class MockAccountDao extends MockEntityDaoBase<Account, AccountApiException> implements AccountDao {

    private final InternalBus eventBus;

    @Inject
    public MockAccountDao(final InternalBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(final Account account, final InternalCallContext context) throws AccountApiException {
        super.create(account, context);

        try {
            eventBus.post(new DefaultAccountCreationEvent(account, null, getRecordId(account.getId(), context), context.getTenantRecordId()), context);
        } catch (final EventBusException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void update(final Account account, final InternalCallContext context) {
        final Account currentAccount = getById(account.getId(), context);
        super.update(account, context);

        final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(account.getId(), null, currentAccount, account,
                                                                                     getRecordId(account.getId(), context), context.getTenantRecordId());
        if (changeEvent.hasChanges()) {
            try {
                eventBus.post(changeEvent, context);
            } catch (final EventBusException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public Account getAccountByKey(final String externalKey, final InternalTenantContext context) {
        for (final Map<Long, Account> accountRow : entities.values()) {
            final Account account = accountRow.values().iterator().next();
            if (account.getExternalKey().equals(externalKey)) {
                return account;
            }
        }

        return null;
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final InternalTenantContext context) {
        final Account account = getAccountByKey(externalKey, context);
        return account == null ? null : account.getId();
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalCallContext context) throws AccountApiException {
        final Account currentAccount = getById(accountId, context);
        if (currentAccount == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }

        final DefaultMutableAccountData updatedAccount = new DefaultMutableAccountData(currentAccount);
        updatedAccount.setPaymentMethodId(paymentMethodId);

        update(new DefaultAccount(updatedAccount), context);
    }
}
