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

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.sql.DataSource;

import org.osgi.service.http.HttpService;
import org.skife.config.ConfigurationObjectFactory;

import com.ning.billing.osgi.DefaultOSGIKillbill;
import com.ning.billing.osgi.DefaultOSGIService;
import com.ning.billing.osgi.KillbillActivator;
import com.ning.billing.osgi.PureOSGIBundleFinder;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.OSGIService;
import com.ning.billing.osgi.api.OSGIServiceRegistration;
import com.ning.billing.osgi.api.config.PluginConfigServiceApi;
import com.ning.billing.osgi.http.DefaultHttpService;
import com.ning.billing.osgi.http.DefaultServletRouter;
import com.ning.billing.osgi.http.OSGIServlet;
import com.ning.billing.osgi.pluginconf.DefaultPluginConfigServiceApi;
import com.ning.billing.osgi.pluginconf.PluginFinder;
import com.ning.billing.util.config.OSGIConfig;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

public class DefaultOSGIModule extends AbstractModule {

    public static final String OSGI_NAMED = "osgi";

    protected void installConfig() {
        final OSGIConfig config = new ConfigurationObjectFactory(System.getProperties()).build(OSGIConfig.class);
        bind(OSGIConfig.class).toInstance(config);

        final OSGIDataSourceConfig osgiDataSourceConfig = new ConfigurationObjectFactory(System.getProperties()).build(OSGIDataSourceConfig.class);
        bind(OSGIDataSourceConfig.class).toInstance(osgiDataSourceConfig);
    }

    protected void installOSGIServlet() {
        bind(new TypeLiteral<OSGIServiceRegistration<Servlet>>() {}).to(DefaultServletRouter.class).asEagerSingleton();
        bind(HttpServlet.class).annotatedWith(Names.named(OSGI_NAMED)).to(OSGIServlet.class).asEagerSingleton();
    }

    protected void installHttpService() {
        bind(HttpService.class).to(DefaultHttpService.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        installConfig();
        installOSGIServlet();
        installHttpService();

        bind(OSGIService.class).to(DefaultOSGIService.class).asEagerSingleton();

        bind(KillbillActivator.class).asEagerSingleton();
        bind(PureOSGIBundleFinder.class).asEagerSingleton();
        bind(PluginFinder.class).asEagerSingleton();
        bind(PluginConfigServiceApi.class).to(DefaultPluginConfigServiceApi.class).asEagerSingleton();
        bind(OSGIKillbill.class).to(DefaultOSGIKillbill.class).asEagerSingleton();
        bind(OSGIDataSourceProvider.class).asEagerSingleton();
        bind(DataSource.class).annotatedWith(Names.named(OSGI_NAMED)).toProvider(OSGIDataSourceProvider.class).asEagerSingleton();
    }
}
