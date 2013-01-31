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

package com.ning.billing.osgi.glue;

import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.osgi.KillbillActivator;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.DefaultPluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.PluginFinder;
import com.ning.billing.util.config.OSGIConfig;

import com.google.inject.AbstractModule;

public class DefaultOSGIModule extends AbstractModule {

    protected void installConfig() {
        final OSGIConfig config = new ConfigurationObjectFactory(System.getProperties()).build(OSGIConfig.class);
        bind(OSGIConfig.class).toInstance(config);
    }

    @Override
    protected void configure() {
        installConfig();
        bind(KillbillActivator.class).asEagerSingleton();
        bind(PluginFinder.class).asEagerSingleton();
        bind(PluginConfigServiceApi.class).to(DefaultPluginConfigServiceApi.class).asEagerSingleton();
    }
}
