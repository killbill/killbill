/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.invoice.api.migration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications;
import org.killbill.billing.invoice.InvoiceDispatcher.FutureAccountNotifications.SubscriptionNotification;
import org.killbill.billing.util.timezone.DateAndTimeZoneContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.MigrationPlan;
import org.killbill.clock.Clock;
import org.killbill.billing.invoice.api.InvoiceItemType;
import org.killbill.billing.invoice.api.InvoiceMigrationApi;
import org.killbill.billing.invoice.dao.DefaultInvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.account.api.AccountInternalApi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;

public class DefaultInvoiceMigrationApi implements InvoiceMigrationApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvoiceMigrationApi.class);

    private final AccountInternalApi accountUserApi;
    private final DefaultInvoiceDao dao;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultInvoiceMigrationApi(final AccountInternalApi accountUserApi,
                                      final DefaultInvoiceDao dao,
                                      final Clock clock,
                                      final InternalCallContextFactory internalCallContextFactory) {
        this.accountUserApi = accountUserApi;
        this.dao = dao;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public UUID createMigrationInvoice(final UUID accountId, final LocalDate targetDate, final BigDecimal balance, final Currency currency, final CallContext context) {
        Account account;
        try {
            account = accountUserApi.getAccountById(accountId, internalCallContextFactory.createInternalTenantContext(accountId, context));
        } catch (AccountApiException e) {
            log.warn("Unable to find account for id {}", accountId);
            return null;
        }

        final InvoiceModelDao migrationInvoice = new InvoiceModelDao(accountId, clock.getUTCToday(), targetDate, currency, true);
        final InvoiceItemModelDao migrationInvoiceItem = new InvoiceItemModelDao(context.getCreatedDate(), InvoiceItemType.FIXED, migrationInvoice.getId(), accountId, null, null,
                                                                                 null, MigrationPlan.MIGRATION_PLAN_NAME, MigrationPlan.MIGRATION_PLAN_PHASE_NAME, null,
                                                                                 targetDate, null, balance, null, currency, null);

        final DateTime wrongEffectiveDateButDoesNotMatter = null;
        final DateAndTimeZoneContext dateAndTimeZoneContext = new DateAndTimeZoneContext(wrongEffectiveDateButDoesNotMatter, account.getTimeZone(), clock);
        dao.createInvoice(migrationInvoice, ImmutableList.<InvoiceItemModelDao>of(migrationInvoiceItem),
                          true, new FutureAccountNotifications(dateAndTimeZoneContext, ImmutableMap.<UUID, List<SubscriptionNotification>>of()), internalCallContextFactory.createInternalCallContext(accountId, context));

        return migrationInvoice.getId();
    }
}
