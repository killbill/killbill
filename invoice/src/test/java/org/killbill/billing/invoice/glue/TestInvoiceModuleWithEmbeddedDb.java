/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.invoice.glue;

import org.killbill.billing.GuicyKillbillTestWithEmbeddedDBModule;
import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.TestInvoiceNotificationQListener;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.mockito.Mockito;

public class TestInvoiceModuleWithEmbeddedDb extends TestInvoiceModule {

    public TestInvoiceModuleWithEmbeddedDb(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void installInvoiceListener() {
        bind(InvoiceListener.class).to(TestInvoiceNotificationQListener.class).asEagerSingleton();
        bind(TestInvoiceNotificationQListener.class).asEagerSingleton();
    }

    @Override
    public void configure() {
        super.configure();
        install(new DefaultAccountModule(configSource));
        install(new GuicyKillbillTestWithEmbeddedDBModule(configSource));
        install(new NonEntityDaoModule(configSource));
        install(new TagStoreModule(configSource));
        install(new AuditModule(configSource));

        bind(CurrencyConversionApi.class).toInstance(Mockito.mock(CurrencyConversionApi.class));
    }
}
