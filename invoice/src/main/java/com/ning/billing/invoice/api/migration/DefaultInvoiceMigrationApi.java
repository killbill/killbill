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

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.MigrationInvoiceItem;
import com.ning.billing.util.clock.Clock;

public class DefaultInvoiceMigrationApi implements InvoiceMigrationApi {
	
	private DefaultInvoiceDao dao;
	private Clock clock;

	@Inject
	public DefaultInvoiceMigrationApi(DefaultInvoiceDao dao, Clock clock) {
		this.dao = dao;
		this.clock = clock;
	}

	@Override
	public UUID createMigrationInvoice(UUID accountId, DateTime targetDate, BigDecimal balance, Currency currency) {
		Invoice migrationInvoice = new DefaultInvoice(accountId, targetDate, currency, clock, true);
		InvoiceItem migrationInvoiceItem = new MigrationInvoiceItem(migrationInvoice.getId(), targetDate, balance, currency, clock);
		migrationInvoice.addInvoiceItem(migrationInvoiceItem);
		dao.create(migrationInvoice);
		return migrationInvoice.getId();
	}
}
