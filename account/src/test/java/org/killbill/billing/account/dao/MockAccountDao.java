/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.account.dao;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.BillingExceptionBase;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.DefaultAccount;
import org.killbill.billing.account.api.DefaultMutableAccountData;
import org.killbill.billing.account.api.user.DefaultAccountChangeEvent;
import org.killbill.billing.account.api.user.DefaultAccountCreationEvent;
import org.killbill.billing.account.api.user.DefaultAccountCreationEvent.DefaultAccountData;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.events.AccountChangeInternalEvent;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.MockEntityDaoBase;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.testng.Assert;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;

public class MockAccountDao extends MockEntityDaoBase<AccountModelDao, Account, AccountApiException> implements AccountDao {

    private final MockEntityDaoBase<AccountModelDao, Account, AccountApiException> accountSqlDao = new MockEntityDaoBase<AccountModelDao, Account, AccountApiException>();
    private final MockEntityDaoBase<AccountEmailModelDao, AccountEmail, AccountApiException> accountEmailSqlDao = new MockEntityDaoBase<AccountEmailModelDao, AccountEmail, AccountApiException>();
    private final PersistentBus eventBus;
    private final Clock clock;

    @Inject
    public MockAccountDao(final PersistentBus eventBus, final Clock clock) {
        this.eventBus = eventBus;
        this.clock = clock;
    }

    @Override
    public void create(final AccountModelDao account, final InternalCallContext context) throws AccountApiException {
        super.create(account, context);

        try {
            final Long accountRecordId = getRecordId(account.getId(), context);
            final long tenantRecordId = context == null ? InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID
                                                        : context.getTenantRecordId();
            eventBus.post(new DefaultAccountCreationEvent(new DefaultAccountData(account), account.getId(), accountRecordId, tenantRecordId, UUID.randomUUID()));
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
        final AccountChangeInternalEvent changeEvent = new DefaultAccountChangeEvent(account.getId(), currentAccount, account,
                                                                                     accountRecordId, tenantRecordId, UUID.randomUUID(),
                                                                                     clock.getUTCNow());
        if (changeEvent.hasChanges()) {
            try {
                eventBus.post(changeEvent);
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
    public Pagination<AccountModelDao> searchAccounts(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        final Collection<AccountModelDao> results = new LinkedList<AccountModelDao>();
        int maxNbRecords = 0;
        for (final AccountModelDao account : getAll(context)) {
            maxNbRecords++;
            if ((account.getName() != null && account.getName().contains(searchKey)) ||
                (account.getEmail() != null && account.getEmail().contains(searchKey)) ||
                (account.getExternalKey() != null && account.getExternalKey().contains(searchKey)) ||
                (account.getCompanyName() != null && account.getCompanyName().contains(searchKey))) {
                results.add(account);
            }
        }

        return DefaultPagination.<AccountModelDao>build(offset, limit, maxNbRecords, results);
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
        } catch (final BillingExceptionBase billingExceptionBase) {
            Assert.fail(billingExceptionBase.toString());
        }
    }

    @Override
    public void removeEmail(final AccountEmailModelDao email, final InternalCallContext context) {
        accountEmailSqlDao.delete(email, context);
    }

    @Override
    public List<AccountEmailModelDao> getEmailsByAccountId(final UUID accountId, final InternalTenantContext context) {
        return ImmutableList.<AccountEmailModelDao>copyOf(Iterables.<AccountEmailModelDao>filter(accountEmailSqlDao.getAll(context), new Predicate<AccountEmailModelDao>() {
            @Override
            public boolean apply(final AccountEmailModelDao input) {
                return input.getAccountId().equals(accountId);
            }
        }));
    }

    @Override
    public Integer getAccountBCD(final UUID accountId, final InternalTenantContext context) {
        final AccountModelDao account = getById(accountId, context);
        return account != null ? account.getBillingCycleDayLocal() : 0;
    }

    @Override
    public List<AccountModelDao> getAccountsByParentId(final UUID parentAccountId, final InternalTenantContext context) {
        return ImmutableList.<AccountModelDao>copyOf(Iterables.<AccountModelDao>filter(accountSqlDao.getAll(context), new Predicate<AccountModelDao>() {
            @Override
            public boolean apply(final AccountModelDao input) {
                return parentAccountId.equals(input.getParentAccountId());
            }
        }));
    }

    @Override
    public List<AuditLogWithHistory> getAuditLogsWithHistoryForId(final UUID accountId, final AuditLevel auditLevel, final InternalTenantContext context) throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getEmailAuditLogsWithHistoryForId(final UUID accountEmailId, final AuditLevel auditLevel, final InternalTenantContext context) throws AccountApiException {
        throw new UnsupportedOperationException();
    }
}
