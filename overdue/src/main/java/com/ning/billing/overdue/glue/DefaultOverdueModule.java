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

package com.ning.billing.overdue.glue;

import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.glue.OverdueModule;
import com.ning.billing.ovedue.notification.DefaultOverdueCheckNotifier;
import com.ning.billing.ovedue.notification.DefaultOverdueCheckPoster;
import com.ning.billing.ovedue.notification.OverdueCheckNotifier;
import com.ning.billing.ovedue.notification.OverdueCheckPoster;
import com.ning.billing.overdue.OverdueProperties;
import com.ning.billing.overdue.OverdueService;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.api.DefaultOverdueUserApi;
import com.ning.billing.overdue.applicator.OverdueEmailGenerator;
import com.ning.billing.overdue.applicator.formatters.DefaultOverdueEmailFormatterFactory;
import com.ning.billing.overdue.applicator.formatters.OverdueEmailFormatterFactory;
import com.ning.billing.overdue.service.DefaultOverdueService;
import com.ning.billing.overdue.service.ExtendedOverdueService;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;

import com.google.inject.AbstractModule;

public class DefaultOverdueModule extends AbstractModule implements OverdueModule {

    @Override
    protected void configure() {
        installOverdueUserApi();

        // internal bindings
        installOverdueService();
        installOverdueWrapperFactory();
        installOverdueEmail();

        final OverdueProperties config = new ConfigurationObjectFactory(System.getProperties()).build(OverdueProperties.class);
        bind(OverdueProperties.class).toInstance(config);
        bind(ExtendedOverdueService.class).to(DefaultOverdueService.class).asEagerSingleton();
        bind(OverdueCheckNotifier.class).to(DefaultOverdueCheckNotifier.class).asEagerSingleton();
        bind(OverdueCheckPoster.class).to(DefaultOverdueCheckPoster.class).asEagerSingleton();
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
