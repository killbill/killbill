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

package org.killbill.billing.mock.api;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountEmail;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.entity.DefaultPagination;
import org.killbill.billing.util.entity.Pagination;
import org.testng.Assert;

public class MockAccountUserApi implements AccountUserApi {

    private final ConcurrentLinkedQueue<Account> accounts = new ConcurrentLinkedQueue<Account>();

    public Account createAccountFromParams(final UUID id,
                                           final String externalKey,
                                           final String email,
                                           final String name,
                                           final int firstNameLength,
                                           final Currency currency,
                                           final int billCycleDayLocal,
                                           final UUID paymentMethodId,
                                           final DateTimeZone timeZone,
                                           final String locale,
                                           final String address1,
                                           final String address2,
                                           final String companyName,
                                           final String city,
                                           final String stateOrProvince,
                                           final String country,
                                           final String postalCode,
                                           final String phone,
                                           final String notes) {
        final Account result = new MockAccountBuilder(id)
                .externalKey(externalKey)
                .email(email)
                .name(name).firstNameLength(firstNameLength)
                .currency(currency)
                .billingCycleDayLocal(billCycleDayLocal)
                .paymentMethodId(paymentMethodId)
                .timeZone(timeZone)
                .locale(locale)
                .address1(address1)
                .address2(address2)
                .companyName(companyName)
                .city(city)
                .stateOrProvince(stateOrProvince)
                .country(country)
                .postalCode(postalCode)
                .phone(phone)
                .notes(notes)
                .build();
        accounts.add(result);
        return result;
    }

    @Override
    public Account createAccount(final AccountData data, final CallContext context) throws AccountApiException {
        final Account result = new MockAccountBuilder(data).build();
        accounts.add(result);
        return result;
    }

    @Override
    public Account getAccountByKey(final String key, final TenantContext context) {
        for (final Account account : accounts) {
            if (key.equals(account.getExternalKey())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public Account getAccountById(final UUID uid, final TenantContext context) {
        for (final Account account : accounts) {
            if (uid.equals(account.getId())) {
                return account;
            }
        }
        return null;
    }

    @Override
    public Pagination<Account> searchAccounts(final String searchKey, final Long offset, final Long limit, final TenantContext tenantContext) {
        final List<Account> results = new LinkedList<Account>();
        for (final Account account : accounts) {
            if ((account.getName() != null && account.getName().contains(searchKey)) ||
                (account.getEmail() != null && account.getEmail().contains(searchKey)) ||
                (account.getExternalKey() != null && account.getExternalKey().contains(searchKey)) ||
                (account.getCompanyName() != null && account.getCompanyName().contains(searchKey))) {
                results.add(account);
            }
        }
        return DefaultPagination.<Account>build(offset, limit, accounts.size(), results);
    }

    @Override
    public Pagination<Account> getAccounts(final Long offset, final Long limit, final TenantContext context) {
        return DefaultPagination.<Account>build(offset, limit, accounts);
    }

    @Override
    public UUID getIdFromKey(final String externalKey, final TenantContext context) {
        for (final Account account : accounts) {
            if (externalKey.equals(account.getExternalKey())) {
                return account.getId();
            }
        }
        return null;
    }

    @Override
    public List<AccountEmail> getEmails(final UUID accountId, final TenantContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeEmail(final UUID accountId, final AccountEmail email, final CallContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccount(final Account account, final CallContext context) {
        final Iterator<Account> iterator = accounts.iterator();
        while (iterator.hasNext()) {
            final Account account1 = iterator.next();
            if (account.getId().equals(account1.getId())) {
                iterator.remove();
                break;
            }
        }
        try {
            createAccount(account, context);
        } catch (final AccountApiException e) {
            Assert.fail(e.toString());
        }
    }

    @Override
    public void updateAccount(final String key, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateAccount(final UUID accountId, final AccountData accountData, final CallContext context)
            throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Account> getChildrenAccounts(final UUID uuid, final TenantContext tenantContext) throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getAuditLogsWithHistoryForId(final UUID accountId, final AuditLevel auditLevel, final TenantContext tenantContext) throws AccountApiException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AuditLogWithHistory> getEmailAuditLogsWithHistoryForId(final UUID accountEmailId, final AuditLevel auditLevel, final TenantContext tenantContext) throws AccountApiException {
        throw new UnsupportedOperationException();
    }
}
