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

package com.ning.billing.invoice.api.migration;

import org.mockito.Mockito;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.invoice.MockModule;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class MockModuleNoEntitlement extends MockModule {

    @Override
    protected void installInvoiceModule() {
        install(new DefaultInvoiceModule() {
            @Override
            protected void installNotifiers() {
                bind(NextBillingDateNotifier.class).toInstance(Mockito.mock(NextBillingDateNotifier.class));
                final NextBillingDatePoster poster = Mockito.mock(NextBillingDatePoster.class);
                bind(NextBillingDatePoster.class).toInstance(poster);
                bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();

                final TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
                bind(TranslatorConfig.class).toInstance(config);
            }
        });

        install(new TemplateModule());
    }
}
