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

import org.killbill.billing.glue.InvoiceModule;
import org.killbill.billing.invoice.InvoiceListener;
import org.killbill.billing.invoice.InvoiceTagHandler;
import org.killbill.billing.invoice.api.DefaultInvoiceService;
import org.killbill.billing.invoice.api.InvoiceInternalApi;
import org.killbill.billing.invoice.api.InvoiceMigrationApi;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.invoice.api.formatters.InvoiceFormatterFactory;
import org.killbill.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import org.killbill.billing.invoice.api.migration.DefaultInvoiceMigrationApi;
import org.killbill.billing.invoice.api.svcs.DefaultInvoiceInternalApi;
import org.killbill.billing.invoice.api.user.DefaultInvoiceUserApi;
import org.killbill.billing.invoice.dao.DefaultInvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.generator.DefaultInvoiceGenerator;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.notification.DefaultNextBillingDateNotifier;
import org.killbill.billing.invoice.notification.DefaultNextBillingDatePoster;
import org.killbill.billing.invoice.notification.EmailInvoiceNotifier;
import org.killbill.billing.invoice.notification.NextBillingDateNotifier;
import org.killbill.billing.invoice.notification.NextBillingDatePoster;
import org.killbill.billing.invoice.notification.NullInvoiceNotifier;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.InvoiceConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.skife.config.ConfigurationObjectFactory;

public class DefaultInvoiceModule extends KillBillModule implements InvoiceModule {

    InvoiceConfig config;

    public DefaultInvoiceModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installInvoiceDao() {
        bind(InvoiceDao.class).to(DefaultInvoiceDao.class).asEagerSingleton();
    }

    @Override
    public void installInvoiceUserApi() {
        bind(InvoiceUserApi.class).to(DefaultInvoiceUserApi.class).asEagerSingleton();
    }

    @Override
    public void installInvoiceInternalApi() {
        bind(InvoiceInternalApi.class).to(DefaultInvoiceInternalApi.class).asEagerSingleton();
    }

    @Override
    public void installInvoicePaymentApi() {
        bind(InvoicePaymentApi.class).to(DefaultInvoicePaymentApi.class).asEagerSingleton();
    }

    protected void installConfig() {
        config = new ConfigurationObjectFactory(skifeConfigSource).build(InvoiceConfig.class);
        bind(InvoiceConfig.class).toInstance(config);
    }

    protected void installInvoiceService() {
        bind(InvoiceService.class).to(DefaultInvoiceService.class).asEagerSingleton();
    }

    @Override
    public void installInvoiceMigrationApi() {
        bind(InvoiceMigrationApi.class).to(DefaultInvoiceMigrationApi.class).asEagerSingleton();
    }

    protected void installNotifiers() {
        bind(NextBillingDateNotifier.class).to(DefaultNextBillingDateNotifier.class).asEagerSingleton();
        bind(NextBillingDatePoster.class).to(DefaultNextBillingDatePoster.class).asEagerSingleton();
        final TranslatorConfig config = new ConfigurationObjectFactory(skifeConfigSource).build(TranslatorConfig.class);
        bind(TranslatorConfig.class).toInstance(config);
        bind(InvoiceFormatterFactory.class).to(config.getInvoiceFormatterFactoryClass()).asEagerSingleton();
    }

    protected void installInvoiceNotifier() {
        if (config.isEmailNotificationsEnabled()) {
            bind(InvoiceNotifier.class).to(EmailInvoiceNotifier.class).asEagerSingleton();
        } else {
            bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
        }
    }

    protected void installInvoiceListener() {
        bind(InvoiceListener.class).asEagerSingleton();
    }

    protected void installTagHandler() {
        bind(InvoiceTagHandler.class).asEagerSingleton();
    }

    protected void installInvoiceGenerator() {
        bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();

        installInvoiceService();
        installInvoiceNotifier();
        installNotifiers();
        installInvoiceListener();
        installTagHandler();
        installInvoiceGenerator();
        installInvoiceDao();
        installInvoiceUserApi();
        installInvoiceInternalApi();
        installInvoicePaymentApi();
        installInvoiceMigrationApi();
    }
}
