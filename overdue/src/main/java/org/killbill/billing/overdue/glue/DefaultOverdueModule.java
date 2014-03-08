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

package org.killbill.billing.overdue.glue;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;

import org.killbill.billing.glue.OverdueModule;
import org.killbill.billing.overdue.notification.OverdueAsyncBusNotifier;
import org.killbill.billing.overdue.notification.OverdueAsyncBusPoster;
import org.killbill.billing.overdue.notification.OverdueCheckNotifier;
import org.killbill.billing.overdue.notification.OverdueCheckPoster;
import org.killbill.billing.overdue.notification.OverduePoster;
import org.killbill.billing.overdue.notification.OverdueNotifier;
import org.killbill.billing.overdue.OverdueProperties;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.OverdueUserApi;
import org.killbill.billing.overdue.api.DefaultOverdueUserApi;
import org.killbill.billing.overdue.applicator.OverdueEmailGenerator;
import org.killbill.billing.overdue.applicator.formatters.DefaultOverdueEmailFormatterFactory;
import org.killbill.billing.overdue.applicator.formatters.OverdueEmailFormatterFactory;
import org.killbill.billing.overdue.service.DefaultOverdueService;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DefaultOverdueModule extends AbstractModule implements OverdueModule {

    protected final ConfigSource configSource;

    public static final String OVERDUE_NOTIFIER_CHECK_NAMED = "overdueNotifierCheck";
    public static final String OVERDUE_NOTIFIER_ASYNC_BUS_NAMED = "overdueNotifierAsyncBus";

    public DefaultOverdueModule(final ConfigSource configSource) {
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        installOverdueUserApi();

        // internal bindings
        installOverdueService();
        installOverdueWrapperFactory();
        installOverdueEmail();

        final OverdueProperties config = new ConfigurationObjectFactory(configSource).build(OverdueProperties.class);
        bind(OverdueProperties.class).toInstance(config);

        bind(OverdueNotifier.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_CHECK_NAMED)).to(OverdueCheckNotifier.class).asEagerSingleton();
        bind(OverdueNotifier.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)).to(OverdueAsyncBusNotifier.class).asEagerSingleton();

        bind(OverduePoster.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_CHECK_NAMED)).to(OverdueCheckPoster.class).asEagerSingleton();
        bind(OverduePoster.class).annotatedWith(Names.named(OVERDUE_NOTIFIER_ASYNC_BUS_NAMED)).to(OverdueAsyncBusPoster.class).asEagerSingleton();
    }

    protected void installOverdueService() {
        bind(OverdueService.class).to(DefaultOverdueService.class).asEagerSingleton();
    }

    protected void installOverdueWrapperFactory() {
        bind(OverdueWrapperFactory.class).asEagerSingleton();
    }

    protected void installOverdueEmail() {
        bind(OverdueEmailFormatterFactory.class).to(DefaultOverdueEmailFormatterFactory.class).asEagerSingleton();
        bind(OverdueEmailGenerator.class).asEagerSingleton();
    }

    @Override
    public void installOverdueUserApi() {
        bind(OverdueUserApi.class).to(DefaultOverdueUserApi.class).asEagerSingleton();
    }
}
