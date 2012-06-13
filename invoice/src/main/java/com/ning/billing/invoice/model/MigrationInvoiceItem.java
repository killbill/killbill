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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.MigrationPlan;

public class MigrationInvoiceItem extends FixedPriceInvoiceItem {

    public MigrationInvoiceItem(final UUID invoiceId, final UUID accountId, final DateTime startDate, final BigDecimal amount, final Currency currency) {
        super(invoiceId, accountId, null, null, MigrationPlan.MIGRATION_PLAN_NAME, MigrationPlan.MIGRATION_PLAN_PHASE_NAME,
              startDate, startDate, amount, currency);
    }
}
