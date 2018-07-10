/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import org.killbill.billing.glue.InvoiceModule;
import org.killbill.billing.invoice.InvoiceDispatcher;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.InvoiceTagHandler;
import org.killbill.billing.invoice.ParkedAccountsManager;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.InvoiceApiHelper;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceListenerService;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatterFactory;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.invoice.api.svcs.DefaultInvoiceInternalApi;
import org.killbill.billing.invoice.api.user.DefaultInvoiceUserApi;
import org.killbill.billing.invoice.config.MultiTenantInvoiceConfig;
import org.killbill.billing.invoice.dao.CBADao;
import org.killbill.billing.invoice.dao.DefaultInvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceDaoHelper;
import org.killbill.billing.invoice.generator.DefaultInvoiceGenerator;
import org.killbill.billing.invoice.generator.FixedAndRecurringInvoiceItemGenerator;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.generator.UsageInvoiceItemGenerator;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.notification.DefaultNextBillingDatePoster;
import org.killbill.billing.invoice.notification.NextBillingDateNotifier;
import org.killbill.billing.invoice.notification.NextBillingDatePoster;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.template.bundles.DefaultResourceBundleFactory;
import org.killbill.billing.invoice.usage.RawUsageOptimizer;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.skife.config.ConfigurationObjectFactory;

import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class DefaultInvoiceModule extends KillBillModule implements InvoiceModule {

    InvoiceConfig staticInvoiceConfig;

    public DefaultInvoiceModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installInvoiceDao() {
        bind(InvoiceDao.class).to(DefaultInvoiceDao.class).asEagerSingleton();
        bind(InvoiceDaoHelper.class).asEagerSingleton();
        bind(CBADao.class).asEagerSingleton();
    }

    @Override
    public void installInvoiceUserApi() {
        bind(InvoiceUserApi.class).to(DefaultInvoiceUserApi.class).asEagerSingleton();
    }

    @Override
    public void installInvoiceInternalApi() {
        bind(InvoiceInternalApi.class).to(DefaultInvoiceInternalApi.class).asEagerSingleton();
    }

    protected void installConfig() {
        installConfig(new ConfigurationObjectFactory(skifeConfigSource).build(InvoiceConfig.class));
    }

    protected void installConfig(final InvoiceConfig staticInvoiceConfig) {
        this.staticInvoiceConfig = staticInvoiceConfig;
        bind(InvoiceConfig.class).annotatedWith(Names.named(STATIC_CONFIG)).toInstance(staticInvoiceConfig);
        bind(InvoiceConfig.class).to(MultiTenantInvoiceConfig.class).asEagerSingleton();
    }

    protected void installInvoiceServices() {
        bind(InvoiceService.class).to(DefaultInvoiceService.class).asEagerSingleton();
        bind(InvoiceListenerService.class).to(InvoiceListener.class).asEagerSingleton();
    }

    protected void installResourceBundleFactory() {
        bind(ResourceBundleFactory.class).to(DefaultResourceBundleFactory.class).asEagerSingleton();
    }


    protected void installNotifiers() {
        bind(NextBillingDateNotifier.class).to(DefaultNextBillingDateNotifier.class).asEagerSingleton();
        bind(NextBillingDatePoster.class).to(DefaultNextBillingDatePoster.class).asEagerSingleton();
        final TranslatorConfig config = new ConfigurationObjectFactory(skifeConfigSource).build(TranslatorConfig.class);
        bind(TranslatorConfig.class).toInstance(config);
        bind(InvoiceFormatterFactory.class).to(config.getInvoiceFormatterFactoryClass()).asEagerSingleton();
    }

    protected void installInvoiceDispatcher() {
        bind(InvoiceDispatcher.class).asEagerSingleton();
    }

    protected void installInvoiceListener() {
        bind(InvoiceListener.class).asEagerSingleton();
    }

    protected void installTagHandler() {
        bind(InvoiceTagHandler.class).asEagerSingleton();
    }

    protected void installInvoiceGenerator() {
        bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
        bind(FixedAndRecurringInvoiceItemGenerator.class).asEagerSingleton();
        bind(UsageInvoiceItemGenerator.class).asEagerSingleton();
    }

    protected void installInvoicePluginApi() {
        bind(new TypeLiteral<OSGIServiceRegistration<InvoicePluginApi>>() {}).toProvider(DefaultInvoiceProviderPluginRegistryProvider.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();

        installInvoicePluginApi();
        installInvoiceServices();
        installNotifiers();
        installInvoiceDispatcher();
        installInvoiceListener();
        installTagHandler();
        installInvoiceGenerator();
        installInvoiceDao();
        installInvoiceUserApi();
        installInvoiceInternalApi();
        installResourceBundleFactory();
        bind(RawUsageOptimizer.class).asEagerSingleton();
        bind(InvoiceApiHelper.class).asEagerSingleton();
        bind(ParkedAccountsManager.class).asEagerSingleton();
    }
}
