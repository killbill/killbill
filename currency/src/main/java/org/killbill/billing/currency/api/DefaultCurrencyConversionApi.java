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

package org.killbill.billing.currency.api;

import java.util.Set;

import javax.inject.Inject;

import org.joda.time.DateTime;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.currency.plugin.api.CurrencyPluginApi;
import org.killbill.billing.osgi.api.OSGIServiceRegistration;
import org.killbill.billing.util.config.definition.CurrencyConfig;

public class DefaultCurrencyConversionApi implements CurrencyConversionApi {

    private final CurrencyConfig config;
    private final OSGIServiceRegistration<CurrencyPluginApi> registry;

    @Inject
    public DefaultCurrencyConversionApi(final CurrencyConfig config, final OSGIServiceRegistration<CurrencyPluginApi> registry) {
        this.config = config;
        this.registry = registry;

    }

    private CurrencyPluginApi getPluginApi() throws CurrencyConversionException {
        final CurrencyPluginApi result = registry.getServiceForName(config.getDefaultCurrencyProvider());
        if (result == null) {
            throw new CurrencyConversionException(ErrorCode.CURRENCY_NO_SUCH_PAYMENT_PLUGIN, config.getDefaultCurrencyProvider());
        }
        return result;
    }

    @Override
    public Set<Currency> getBaseRates() throws CurrencyConversionException {
        final CurrencyPluginApi pluginApi = getPluginApi();
        return pluginApi.getBaseCurrencies();
    }

    @Override
    public CurrencyConversion getCurrentCurrencyConversion(final Currency baseCurrency) throws CurrencyConversionException {
        final Set<Rate> allRates = getPluginApi().getCurrentRates(baseCurrency);
        return getCurrencyConversionInternal(baseCurrency, allRates);
    }

    @Override
    public CurrencyConversion getCurrencyConversion(final Currency baseCurrency, final DateTime dateConversion) throws CurrencyConversionException {
        final Set<Rate> allRates = getPluginApi().getRates(baseCurrency, dateConversion);
        return getCurrencyConversionInternal(baseCurrency, allRates);
    }

    private CurrencyConversion getCurrencyConversionInternal(final Currency baseCurrency, final Set<Rate> allRates) {
        final CurrencyConversion result = new DefaultCurrencyConversion(baseCurrency, allRates);
        return result;
    }
}
