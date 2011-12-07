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

package com.ning.billing.invoice.api;

import com.google.inject.Inject;
import com.ning.billing.invoice.dao.InvoiceDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class DefaultInvoiceUserApi implements InvoiceUserApi {
    private final InvoiceDao dao;

    @Inject
    public DefaultInvoiceUserApi(IDBI dbi) {
        dao = dbi.onDemand(InvoiceDao.class);
    }

    @Override
    public List<UUID> getInvoicesForPayment(DateTime targetDate, int numberOfDays) {
        return dao.getInvoicesForPayment(targetDate.toDate(), numberOfDays);
    }

    @Override
    public List<Invoice> getInvoicesByAccount(UUID accountId) {
        return dao.getInvoicesByAccount(accountId.toString());
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        return dao.getInvoice(invoiceId.toString());
    }

    @Override
    public void paymentAttemptFailed(UUID invoiceId, DateTime paymentAttemptDate) {
        dao.notifyFailedPayment(invoiceId.toString(), paymentAttemptDate.toDate());
    }

    @Override
    public void paymentAttemptSuccessful(UUID invoiceId, DateTime paymentAttemptDate, BigDecimal paymentAmount) {
        dao.notifySuccessfulPayment(invoiceId.toString(), paymentAttemptDate.toDate(), paymentAmount);
    }
}
