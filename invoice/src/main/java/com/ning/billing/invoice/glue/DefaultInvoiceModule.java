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

import com.google.inject.AbstractModule;
import com.ning.billing.config.InvoiceConfig;
import com.ning.billing.glue.InvoiceModule;
import com.ning.billing.invoice.InvoiceListener;
import com.ning.billing.invoice.api.DefaultInvoiceService;
import com.ning.billing.invoice.api.InvoiceMigrationApi;
import com.ning.billing.invoice.api.InvoiceNotifier;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.api.formatters.InvoiceFormatterFactory;
import com.ning.billing.invoice.api.invoice.DefaultInvoicePaymentApi;
import com.ning.billing.invoice.api.migration.DefaultInvoiceMigrationApi;
import com.ning.billing.invoice.api.user.DefaultInvoiceUserApi;
import com.ning.billing.invoice.dao.DefaultInvoiceDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.model.DefaultInvoiceGenerator;
import com.ning.billing.invoice.model.InvoiceGenerator;
import com.ning.billing.invoice.notification.DefaultNextBillingDateNotifier;
import com.ning.billing.invoice.notification.DefaultNextBillingDatePoster;
import com.ning.billing.invoice.notification.NextBillingDateNotifier;
import com.ning.billing.invoice.notification.NextBillingDatePoster;
import com.ning.billing.invoice.notification.NullInvoiceNotifier;
import com.ning.billing.invoice.template.formatters.DefaultInvoiceFormatterFactory;
import com.ning.billing.util.template.translation.TranslatorConfig;

public class DefaultInvoiceModule extends AbstractModule implements InvoiceModule {
    protected void installInvoiceDao() {
        bind(InvoiceDao.class).to(DefaultInvoiceDao.class).asEagerSingleton();
    }

    /* (non-Javadoc)
     * @see com.ning.billing.invoice.glue.InvoiceModule#installInvoiceUserApi()
     */
    @Override
    public void installInvoiceUserApi() {
        bind(InvoiceUserApi.class).to(DefaultInvoiceUserApi.class).asEagerSingleton();
    }

    /* (non-Javadoc)
     * @see com.ning.billing.invoice.glue.InvoiceModule#installInvoicePaymentApi()
     */
    @Override
    public void installInvoicePaymentApi() {
        bind(InvoicePaymentApi.class).to(DefaultInvoicePaymentApi.class).asEagerSingleton();
    }

    protected void installConfig() {
        final InvoiceConfig config = new ConfigurationObjectFactory(System.getProperties()).build(InvoiceConfig.class);
        bind(InvoiceConfig.class).toInstance(config);
    }

    protected void installInvoiceService() {
        bind(InvoiceService.class).to(DefaultInvoiceService.class).asEagerSingleton();
    }
    
    /* (non-Javadoc)
     * @see com.ning.billing.invoice.glue.InvoiceModule#installInvoiceMigrationApi()
     */
    @Override
    public void installInvoiceMigrationApi() {
    	bind(InvoiceMigrationApi.class).to(DefaultInvoiceMigrationApi.class).asEagerSingleton();
	}

    protected void installNotifiers() {
        bind(NextBillingDateNotifier.class).to(DefaultNextBillingDateNotifier.class).asEagerSingleton();
        bind(NextBillingDatePoster.class).to(DefaultNextBillingDatePoster.class).asEagerSingleton();
        TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        bind(TranslatorConfig.class).toInstance(config);
        bind(InvoiceFormatterFactory.class).to(DefaultInvoiceFormatterFactory.class).asEagerSingleton();
        bind(InvoiceNotifier.class).to(NullInvoiceNotifier.class).asEagerSingleton();
    }

    protected void installInvoiceListener() {
        bind(InvoiceListener.class).asEagerSingleton();
    }

    protected void installInvoiceGenerator() {
        bind(InvoiceGenerator.class).to(DefaultInvoiceGenerator.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installInvoiceService();
        installConfig();
        installNotifiers();
        installInvoiceListener();
        installInvoiceGenerator();
        installInvoiceDao();
        installInvoiceUserApi();
        installInvoicePaymentApi();
        installInvoiceMigrationApi();
    }
}
