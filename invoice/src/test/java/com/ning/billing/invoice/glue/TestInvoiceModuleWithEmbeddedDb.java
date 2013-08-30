/*
 * Copyright 2010-2013 Ning, Inc.
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

import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestWithEmbeddedDBModule;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.TestInvoiceNotificationQListener;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.MetricsModule;
import com.ning.billing.util.glue.NonEntityDaoModule;

public class TestInvoiceModuleWithEmbeddedDb extends TestInvoiceModule {

    public TestInvoiceModuleWithEmbeddedDb(final ConfigSource configSource) {
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

        install(new GuicyKillbillTestWithEmbeddedDBModule());
        install(new NonEntityDaoModule());
        install(new MetricsModule());
        install(new BusModule(configSource));
    }
}
