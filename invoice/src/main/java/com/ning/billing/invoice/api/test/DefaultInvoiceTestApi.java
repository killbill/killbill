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

package com.ning.billing.invoice.api.test;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.util.callcontext.CallContext;

public class DefaultInvoiceTestApi implements InvoiceTestApi {
    private final InvoiceDao invoiceDao;

    @Inject
    public DefaultInvoiceTestApi(InvoiceDao invoiceDao) {
        this.invoiceDao = invoiceDao;
    }

    @Override
    public void create(final Invoice invoice, final CallContext context) {
        invoiceDao.create(invoice, context);
    }
}
