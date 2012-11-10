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

import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.MigrationPlan;
import com.ning.billing.invoice.api.InvoiceItemType;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceItemModelDao;
import com.ning.billing.invoice.dao.InvoiceModelDao;
import com.ning.billing.invoice.dao.InvoicePaymentModelDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.svcapi.account.AccountInternalApi;

import com.google.common.collect.ImmutableList;
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
        try {
            accountUserApi.getAccountById(accountId, internalCallContextFactory.createInternalTenantContext(context));
        } catch (AccountApiException e) {
            log.warn("Unable to find account for id {}", accountId);
            return null;
        }

        final InvoiceModelDao migrationInvoice = new InvoiceModelDao(accountId, clock.getUTCToday(), targetDate, currency, true);
        final InvoiceItemModelDao migrationInvoiceItem = new InvoiceItemModelDao(InvoiceItemType.FIXED, migrationInvoice.getId(), accountId, null, null,
                                                                                 MigrationPlan.MIGRATION_PLAN_NAME, MigrationPlan.MIGRATION_PLAN_PHASE_NAME,
                                                                                 targetDate, null, balance, null, currency, null);
        dao.createInvoice(migrationInvoice, ImmutableList.<InvoiceItemModelDao>of(migrationInvoiceItem),
                          ImmutableList.<InvoicePaymentModelDao>of(), true, internalCallContextFactory.createInternalCallContext(accountId, context));

        return migrationInvoice.getId();
    }
}
