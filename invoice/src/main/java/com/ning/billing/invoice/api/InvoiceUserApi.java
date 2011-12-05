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

public class InvoiceUserApi implements IInvoiceUserApi {
    private final InvoiceDao dao;

    @Inject
    public InvoiceUserApi(IDBI dbi) {
        dao = dbi.onDemand(InvoiceDao.class);
    }

    @Override
    public List<UUID> getInvoicesForPayment(DateTime targetDate, int numberOfDays) {
        return dao.getInvoicesForPayment(targetDate.toDate(), numberOfDays);
    }

    @Override
    public List<Invoice> getInvoicesByAccount() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void paymentAttemptFailed(UUID invoiceId, DateTime paymentAttemptDate) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void paymentAttemptSuccessful(UUID invoiceId, DateTime paymentAttemptDate, BigDecimal paymentAmount) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
