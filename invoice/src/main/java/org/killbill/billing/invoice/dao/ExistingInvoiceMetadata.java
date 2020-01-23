/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.invoice.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;

public class ExistingInvoiceMetadata {

    private final Map<UUID, InvoiceModelDao> invoicesCache = new HashMap<UUID, InvoiceModelDao>();
    private final Map<UUID, InvoiceItemModelDao> invoiceItemsCache = new HashMap<UUID, InvoiceItemModelDao>();

    private InvoiceSqlDao invoiceSqlDao;
    private InvoiceItemSqlDao invoiceItemSqlDao;

    public ExistingInvoiceMetadata(final Iterable<Invoice> existingInvoices) {
        for (final Invoice invoice : existingInvoices) {
            invoicesCache.put(invoice.getId(), new InvoiceModelDao(invoice));
            for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                invoiceItemsCache.put(invoiceItem.getId(), new InvoiceItemModelDao(invoiceItem));
            }
        }
    }

    public ExistingInvoiceMetadata(final InvoiceSqlDao invoiceSqlDao, final InvoiceItemSqlDao invoiceItemSqlDao) {
        this.invoiceSqlDao = invoiceSqlDao;
        this.invoiceItemSqlDao = invoiceItemSqlDao;
    }

    public InvoiceModelDao getExistingInvoice(final UUID invoiceId, final InternalTenantContext context) {
        if (invoiceSqlDao != null) {
            return invoiceSqlDao.getById(invoiceId.toString(), context);
        } else {
            return invoicesCache.get(invoiceId);
        }
    }

    public InvoiceItemModelDao getExistingInvoiceItem(final UUID invoiceItemId, final InternalTenantContext context) {
        if (invoiceItemSqlDao != null) {
            return invoiceItemSqlDao.getById(invoiceItemId.toString(), context);
        } else {
            return invoiceItemsCache.get(invoiceItemId);
        }
    }
}
