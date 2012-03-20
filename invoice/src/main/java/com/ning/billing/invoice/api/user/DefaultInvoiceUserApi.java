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

package com.ning.billing.invoice.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.CallContext;
import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.invoice.InvoiceDispatcher;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.dao.InvoiceDao;

public class DefaultInvoiceUserApi implements InvoiceUserApi {
    private final InvoiceDao dao;
    private final InvoiceDispatcher dispatcher;

    @Inject
    public DefaultInvoiceUserApi(final InvoiceDao dao, final InvoiceDispatcher dispatcher) {
        this.dao = dao;
        this.dispatcher = dispatcher;
    }

    @Override
    public List<UUID> getInvoicesForPayment(final DateTime targetDate, final int numberOfDays) {
        return dao.getInvoicesForPayment(targetDate, numberOfDays);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId) {
        return dao.getInvoicesByAccount(accountId);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(final UUID accountId, final DateTime fromDate) {
        return dao.getInvoicesByAccount(accountId, fromDate);
    }

    @Override
    public void notifyOfPaymentAttempt(InvoicePayment invoicePayment) {
        dao.notifyOfPaymentAttempt(invoicePayment);
    }

    @Override
	public BigDecimal getAccountBalance(UUID accountId) {
		BigDecimal result = dao.getAccountBalance(accountId);
		return result == null ? BigDecimal.ZERO : result;
	}

    @Override
    public Invoice getInvoice(final UUID invoiceId) {
        return dao.getById(invoiceId);
    }

    @Override
    public List<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final DateTime upToDate) {
        return dao.getUnpaidInvoicesByAccountId(accountId, upToDate);
    }

	@Override
	public Invoice triggerInvoiceGeneration(final UUID accountId,
			final DateTime targetDate, final boolean dryRun,
            final CallContext context) throws InvoiceApiException {
		return dispatcher.processAccount(accountId, targetDate, dryRun, context);
	}
}
