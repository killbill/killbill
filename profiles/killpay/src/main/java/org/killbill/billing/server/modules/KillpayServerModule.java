/*
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.server.modules;

import javax.servlet.ServletContext;

import org.killbill.billing.account.glue.DefaultAccountModule;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.catalog.glue.CatalogModule;
import org.killbill.billing.currency.glue.CurrencyModule;
import org.killbill.billing.entitlement.glue.DefaultEntitlementModule;
import org.killbill.billing.invoice.glue.DefaultInvoiceModule;
import org.killbill.billing.jaxrs.glue.DefaultJaxrsModule;
import org.killbill.billing.junction.glue.DefaultJunctionModule;
import org.killbill.billing.overdue.glue.DefaultOverdueModule;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.server.config.KillbillServerConfig;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.usage.glue.UsageModule;
import org.killbill.billing.util.config.definition.JaxrsConfig;
import org.killbill.billing.util.email.templates.TemplateModule;
import org.killbill.billing.util.glue.AuditModule;
import org.killbill.billing.util.glue.BroadcastModule;
import org.killbill.billing.util.glue.CacheModule;
import org.killbill.billing.util.glue.CallContextModule;
import org.killbill.billing.util.glue.ConfigModule;
import org.killbill.billing.util.glue.CustomFieldModule;
import org.killbill.billing.util.glue.EventModule;
import org.killbill.billing.util.glue.ExportModule;
import org.killbill.billing.util.glue.GlobalLockerModule;
import org.killbill.billing.util.glue.KillBillShiroAopModule;
import org.killbill.billing.util.glue.KillbillApiAopModule;
import org.killbill.billing.util.glue.NodesModule;
import org.killbill.billing.util.glue.NonEntityDaoModule;
import org.killbill.billing.util.glue.RecordIdModule;
import org.killbill.billing.util.glue.SecurityModule;
import org.killbill.billing.util.glue.TagStoreModule;
import org.skife.config.AugmentedConfigurationObjectFactory;

public class KillpayServerModule extends KillbillServerModule {

    public KillpayServerModule(final ServletContext servletContext, final KillbillServerConfig serverConfig, final KillbillConfigSource configSource) {
        super(servletContext, serverConfig, configSource);
    }

    @Override
    protected void installKillbillModules() {
        install(new AuditModule(configSource));
        install(new NodesModule(configSource));
        install(new BroadcastModule(configSource));
        install(new BeatrixModule(configSource));
        install(new CacheModule(configSource));
        install(new ConfigModule(configSource));
        install(new EventModule(configSource));
        install(new CallContextModule(configSource));
        install(new CurrencyModule(configSource));
        install(new CustomFieldModule(configSource));
        install(new DefaultAccountModule(configSource));
        install(new ExportModule(configSource));
        install(new GlobalLockerModule(configSource));
        install(new KillBillShiroAopModule(configSource));

        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final JaxrsConfig jaxrsConfig = factory.build(JaxrsConfig.class);
        install(new KillbillApiAopModule(jaxrsConfig));
        install(new JaxRSAopModule(jaxrsConfig));

        install(new KillBillShiroWebModule(servletContext, skifeConfigSource));
        install(new NonEntityDaoModule(configSource));
        install(new PaymentModule(configSource));
        install(new RecordIdModule(configSource));
        install(new SecurityModule(configSource));
        install(new TagStoreModule(configSource));
        install(new DefaultTenantModule(configSource));

        // TODO Required by payment for InvoiceInternalApi and InvoicePaymentApi
        install(new DefaultInvoiceModule(configSource));
        // TODO Dependencies for DefaultInvoiceModule
        install(new CatalogModule(configSource));
        install(new DefaultEntitlementModule(configSource));
        install(new DefaultJunctionModule(configSource));
        install(new DefaultSubscriptionModule(configSource));
        install(new TemplateModule(configSource));
        install(new UsageModule(configSource));
        install(new DefaultJaxrsModule(configSource));
        // TODO Dependencies for AccountResource
        install(new DefaultOverdueModule(configSource));
    }
}
