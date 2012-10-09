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

package com.ning.billing.invoice.api.svcs;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.LocalDate;

import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.svcapi.invoice.InvoiceInternalApi;

public class DefaultInvoiceInternalApi implements InvoiceInternalApi {

    private final InvoiceDao dao;

    @Inject
    public DefaultInvoiceInternalApi(final InvoiceDao dao) {
        this.dao = dao;
    }

    @Override
    public Invoice getInvoiceById(final UUID invoiceId, final InternalTenantContext context) throws InvoiceApiException {
        return dao.getById(invoiceId, context);
    }

    @Override
    public Collection<Invoice> getUnpaidInvoicesByAccountId(final UUID accountId, final LocalDate upToDate, final InternalTenantContext context) {
        return dao.getUnpaidInvoicesByAccountId(accountId, upToDate, context);
    }

    @Override
    public Collection<Invoice> getInvoicesByAccountId(final UUID accountId, final InternalTenantContext context) {
        return dao.getInvoicesByAccount(accountId, context);
    }

    @Override
    public BigDecimal getAccountBalance(final UUID accountId, final InternalTenantContext context) {
        return dao.getAccountBalance(accountId, context);
    }
}
