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

package com.ning.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.model.MigrationInvoiceItem;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.Clock;

import com.google.inject.Inject;

public class DefaultInvoiceMigrationApi implements InvoiceMigrationApi {
    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceMigrationApi.class);

    private final AccountUserApi accountUserApi;
    private final DefaultInvoiceDao dao;
    private final Clock clock;

    @Inject
    public DefaultInvoiceMigrationApi(final AccountUserApi accountUserApi, final DefaultInvoiceDao dao, final Clock clock) {
        this.accountUserApi = accountUserApi;
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public UUID createMigrationInvoice(final UUID accountId, final LocalDate targetDate, final BigDecimal balance, final Currency currency) {
        final Account account;
        try {
            account = accountUserApi.getAccountById(accountId);
        } catch (AccountApiException e) {
            log.warn("Unable to find account for id {}", accountId);
            return null;
        }

        final CallContext context = new DefaultCallContextFactory(clock).createMigrationCallContext("Migration", CallOrigin.INTERNAL, UserType.MIGRATION, clock.getUTCNow(), clock.getUTCNow());
        final Invoice migrationInvoice = new MigrationInvoice(accountId, clock.getUTCToday(), targetDate, currency);
        final InvoiceItem migrationInvoiceItem = new MigrationInvoiceItem(migrationInvoice.getId(), accountId, targetDate, balance, currency);
        migrationInvoice.addInvoiceItem(migrationInvoiceItem);

        dao.create(migrationInvoice, account.getBillCycleDay().getDayOfMonthUTC(), true, context);
        return migrationInvoice.getId();
    }
}
