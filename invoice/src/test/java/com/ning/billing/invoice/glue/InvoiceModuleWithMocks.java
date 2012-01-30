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

package com.ning.billing.invoice.glue;

import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.MockInvoiceDao;

public class InvoiceModuleWithMocks extends InvoiceModule {
    @Override
    protected void installInvoiceDao() {
        bind(MockInvoiceDao.class).asEagerSingleton();
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
    }

    @Override
    protected void installInvoiceListener() {

    }

    @Override
    protected void installNotifier() {

    }

    @Override
    protected void installInvoiceService() {

    }
}
