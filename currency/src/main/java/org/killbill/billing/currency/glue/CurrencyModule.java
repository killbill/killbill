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

package org.killbill.billing.currency.glue;

import org.killbill.billing.currency.DefaultCurrencyService;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.currency.api.CurrencyService;
import org.killbill.billing.currency.api.DefaultCurrencyConversionApi;
import org.killbill.billing.currency.plugin.api.CurrencyPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.config.definition.CurrencyConfig;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.AugmentedConfigurationObjectFactory;

import com.google.inject.TypeLiteral;

public class CurrencyModule extends KillBillModule {

    public CurrencyModule(final KillbillConfigSource configSource) {
        super(configSource);
    }

    @Override
    protected void configure() {
        final AugmentedConfigurationObjectFactory factory = new AugmentedConfigurationObjectFactory(skifeConfigSource);
        final CurrencyConfig currencyConfig = factory.build(CurrencyConfig.class);
        bind(CurrencyConfig.class).toInstance(currencyConfig);

        bind(new TypeLiteral<OSGIServiceRegistration<CurrencyPluginApi>>() {}).toProvider(DefaultCurrencyProviderPluginRegistryProvider.class).asEagerSingleton();

        bind(CurrencyConversionApi.class).to(DefaultCurrencyConversionApi.class).asEagerSingleton();
        bind(CurrencyService.class).to(DefaultCurrencyService.class).asEagerSingleton();
    }
}
