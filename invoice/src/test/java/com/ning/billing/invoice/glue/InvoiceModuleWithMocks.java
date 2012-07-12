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

import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.MockInvoiceDao;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.MockGlobalLocker;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class InvoiceModuleWithMocks extends DefaultInvoiceModule {
    @Override
    protected void installInvoiceDao() {
        bind(MockInvoiceDao.class).asEagerSingleton();
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
        bind(GlobalLocker.class).to(MockGlobalLocker.class).asEagerSingleton();
    }

    @Override
    protected void installInvoiceListener() {
    }

    @Override
    protected void installNotifiers() {
        bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
        final TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        bind(TranslatorConfig.class).toInstance(config);
        bind(InvoiceFormatterFactory.class).to(DefaultInvoiceFormatterFactory.class).asEagerSingleton();
    }

    @Override
    protected void installInvoiceService() {
    }

    @Override
    public void installInvoiceMigrationApi() {
    }
}
