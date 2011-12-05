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

package com.ning.billing.invoice.dao;

import com.google.inject.Inject;
import com.ning.billing.invoice.api.IInvoiceItem;
import org.skife.jdbi.v2.IDBI;

import java.util.List;

public class InvoiceItemDaoWrapper implements InvoiceItemDao {
    private final InvoiceItemDao dao;

    @Inject
    public InvoiceItemDaoWrapper(IDBI dbi) {
        dao = dbi.onDemand(InvoiceItemDao.class);
    }

    @Override
    public IInvoiceItem getInvoiceItem(String invoiceItemId) {
        return dao.getInvoiceItem(invoiceItemId);
    }

    @Override
    public List<IInvoiceItem> getInvoiceItemsByInvoice(String invoiceId) {
        return dao.getInvoiceItemsByInvoice(invoiceId);
    }

    @Override
    public List<IInvoiceItem> getInvoiceItemsByAccount(String accountId) {
        return dao.getInvoiceItemsByAccount(accountId);
    }

    @Override
    public List<IInvoiceItem> getInvoiceItemsBySubscription(String subscriptionId) {
        return dao.getInvoiceItemsBySubscription(subscriptionId);
    }

    @Override
    public void createInvoiceItem(IInvoiceItem invoiceItem) {
        dao.createInvoiceItem(invoiceItem);
    }

    @Override
    public void test() {
        dao.test();
    }
}
