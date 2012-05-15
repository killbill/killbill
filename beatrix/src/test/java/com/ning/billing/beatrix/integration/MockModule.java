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

package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertNotNull;

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.glue.AccountModuleWithMocks;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import org.skife.config.ConfigurationObjectFactory;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.ning.billing.account.glue.AccountModule;
import com.ning.billing.beatrix.lifecycle.DefaultLifecycle;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.dbi.DBIProvider;
import com.ning.billing.dbi.DbiConfig;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.glue.DefaultInvoiceModule;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.lifecycle.KillbillService;
import com.ning.billing.payment.api.PaymentService;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.payment.setup.PaymentModule;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.email.EmailModule;
import com.ning.billing.util.email.templates.TemplateModule;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.FieldStoreModule;
import com.ning.billing.util.glue.GlobalLockerModule;
import com.ning.billing.util.glue.NotificationQueueModule;
import com.ning.billing.util.glue.TagStoreModule;


public class MockModule extends AbstractModule {
    public static final String PLUGIN_NAME = "yoyo";

    @Override
    protected void configure() {

        loadSystemPropertiesFromClasspath("/resource.properties");

        bind(Clock.class).to(ClockMock.class).asEagerSingleton();
        bind(ClockMock.class).asEagerSingleton();
        bind(Lifecycle.class).to(SubsetDefaultLifecycle.class).asEagerSingleton();

        final MysqlTestingHelper helper = new MysqlTestingHelper();
        bind(MysqlTestingHelper.class).toInstance(helper);
        final IDBI dbi;
        if (helper.isUsingLocalInstance()) {
            final DbiConfig config = new ConfigurationObjectFactory(System.getProperties()).build(DbiConfig.class);
            DBIProvider provider = new DBIProvider(config);
            dbi = provider.get();
        } else {
            dbi = helper.getDBI();
        }
        bind(IDBI.class).toInstance(dbi);

        install(new EmailModule());
        install(new CallContextModule());
        install(new GlobalLockerModule());
        install(new BusModule());
        install(new NotificationQueueModule());
        install(new TagStoreModule());

        CustomFieldDao customFieldDao = BrainDeadProxyFactory.createBrainDeadProxyFor(CustomFieldDao.class);
        bind(CustomFieldDao.class).toInstance(customFieldDao);

        install(new AccountModuleWithMocks());
        install(new CatalogModule());
        install(new DefaultEntitlementModule());
        install(new DefaultInvoiceModule());
        install(new TemplateModule());
        install(new PaymentMockModule());
        install(new DefaultJunctionModule());
    }

    private static final class PaymentMockModule extends PaymentModule {
        @Override
        protected void installPaymentProviderPlugins(PaymentConfig config) {
            install(new MockPaymentProviderPluginModule(PLUGIN_NAME));
        }
    }

    private static void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = TestIntegration.class.getResource(resource);
        assertNotNull(url);
        try {
            System.getProperties().load( url.openStream() );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final static class SubsetDefaultLifecycle extends DefaultLifecycle {

        @Inject
        public SubsetDefaultLifecycle(Injector injector) {
            super(injector);
        }

        @Override
        protected Set<? extends KillbillService> findServices() {
            ImmutableSet<? extends KillbillService> services = new ImmutableSet.Builder<KillbillService>()
                            .add(injector.getInstance(AccountService.class))
                            .add(injector.getInstance(BusService.class))
                            .add(injector.getInstance(CatalogService.class))
                            .add(injector.getInstance(EntitlementService.class))
                            .add(injector.getInstance(InvoiceService.class))
                            .add(injector.getInstance(PaymentService.class))
                            .build();
            return services;
        }
    }
}
