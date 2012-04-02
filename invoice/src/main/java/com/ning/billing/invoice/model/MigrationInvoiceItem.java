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
import com.ning.billing.util.clock.Clock;

public class MigrationInvoiceItem extends FixedPriceInvoiceItem {
    private final static UUID MIGRATION_SUBSCRIPTION_ID = UUID.fromString("ed25f954-3aa2-4422-943b-c3037ad7257c"); 
    private final static UUID MIGRATION_BUNDLE_ID = UUID.fromString("ed25f954-3aa2-4422-943b-c3037ad7257d"); // Going away anyway

	public MigrationInvoiceItem(UUID invoiceId, DateTime startDate, BigDecimal amount, Currency currency, Clock clock) {
		super(invoiceId, MIGRATION_SUBSCRIPTION_ID, MIGRATION_BUNDLE_ID, MigrationPlan.MIGRATION_PLAN_NAME, MigrationPlan.MIGRATION_PLAN_PHASE_NAME, startDate, startDate,
				amount, currency, clock.getUTCNow());
	}

	
}
