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
import java.util.Map;
import java.util.UUID;

import org.testng.Assert;

import com.ning.billing.BillingExceptionBase;
import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.account.api.DefaultAccount;
import com.ning.billing.account.api.DefaultMutableAccountData;
import com.ning.billing.account.api.user.DefaultAccountChangeEvent;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.entity.dao.MockEntityDaoBase;
import com.ning.billing.util.events.AccountChangeInternalEvent;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class MockAccountDao extends MockEntityDaoBase<AccountModelDao, Account, AccountApiException> implements AccountDao {

    private final MockEntityDaoBase<AccountEmailModelDao, AccountEmail, AccountApiException> accountEmailSqlDao = new MockEntityDaoBase<AccountEmailModelDao, AccountEmail, AccountApiException>();
    private final InternalBus eventBus;

    public MockAccountDao(final InternalBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void create(final AccountModelDao account, final InternalCallContext context) throws AccountApiException {
        super.create(account, context);

        try {
            final Long accountRecordId = getRecordId(account.getId(), context);
            final long tenantRecordId = context == null ? InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID
                                                        : context.getTenantRecordId();
            eventBus.post(new DefaultAccountCreationEvent(account, null, accountRecordId, tenantRecordId), context);
        } catch (final EventBusException ex) {
            Assert.fail(ex.toString());
        }
    }

    @Override
    public void update(final AccountModelDao account, final InternalCallContext context) {
        super.update(account, context);

        final AccountModelDao currentAccount = getById(account.getId(), context);
        final Long accountRecordId = getRecordId(account.getId(), context);
        final long tenantRecordId = context == null ? InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID
                                                    : context.getTenantRecordId();
        final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(account.getId(), null, currentAccount, account,
                                                                                     accountRecordId, tenantRecordId);
        if (changeEvent.hasChanges()) {
            try {
                eventBus.post(changeEvent, context);
            } catch (final EventBusException ex) {
                Assert.fail(ex.toString());
            }
        }
    }

    @Override
    public AccountModelDao getAccountByKey(final String externalKey, final InternalTenantContext context) {
        for (final Map<Long, AccountModelDao> accountRow : entities.values()) {
            final AccountModelDao account = accountRow.values().iterator().next();
            if (account.getExternalKey().equals(externalKey)) {
                return account;
            }
        }

        return null;
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final InternalTenantContext context) {
        final AccountModelDao account = getAccountByKey(externalKey, context);
        return account == null ? null : account.getId();
    }

    @Override
    public void updatePaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalCallContext context) throws AccountApiException {
        final AccountModelDao currentAccountModelDao = getById(accountId, context);
        if (currentAccountModelDao == null) {
            throw new AccountApiException(ErrorCode.ACCOUNT_DOES_NOT_EXIST_FOR_ID, accountId);
        }

        final DefaultAccount currentAccount = new DefaultAccount(currentAccountModelDao);
        final DefaultMutableAccountData updatedAccount = new DefaultMutableAccountData(currentAccount);
        updatedAccount.setPaymentMethodId(paymentMethodId);

        update(new AccountModelDao(accountId, updatedAccount), context);
    }

    @Override
    public void addEmail(final AccountEmailModelDao email, final InternalCallContext context) {
        try {
            accountEmailSqlDao.create(email, context);
        } catch (BillingExceptionBase billingExceptionBase) {
            Assert.fail(billingExceptionBase.toString());
        }
    }

    @Override
    public void removeEmail(final AccountEmailModelDao email, final InternalCallContext context) {
        accountEmailSqlDao.delete(email, context);
    }

    @Override
    public List<AccountEmailModelDao> getEmailsByAccountId(final UUID accountId, final InternalTenantContext context) {
        return ImmutableList.<AccountEmailModelDao>copyOf(Collections2.filter(accountEmailSqlDao.get(context), new Predicate<AccountEmailModelDao>() {
            @Override
            public boolean apply(final AccountEmailModelDao input) {
                return input.getAccountId().equals(accountId);
            }
        }));
    }

    @Override
    public AccountModelDao getByRecordId(final Long recordId, final InternalCallContext context) {
        return null;
    }
}
