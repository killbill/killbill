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

package com.ning.billing.invoice.glue;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import com.ning.billing.GuicyKillbillTestNoDBModule;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.currency.api.CurrencyConversion;
import com.ning.billing.currency.api.CurrencyConversionApi;
import com.ning.billing.currency.api.CurrencyConversionException;
import com.ning.billing.currency.api.Rate;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.MockInvoiceDao;
import com.ning.billing.mock.glue.MockNonEntityDaoModule;
import com.ning.billing.util.bus.InMemoryBusModule;
import com.ning.billing.account.api.AccountInternalApi;

public class TestInvoiceModuleNoDB extends TestInvoiceModule {

    public TestInvoiceModuleNoDB(final ConfigSource configSource) {
        super(configSource);
    }

    protected void installInvoiceDao() {
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
    }

    @Override
    public void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule());
        install(new MockNonEntityDaoModule());
        install(new InMemoryBusModule(configSource));

        bind(AccountInternalApi.class).toInstance(Mockito.mock(AccountInternalApi.class));
        bind(AccountUserApi.class).toInstance(Mockito.mock(AccountUserApi.class));

        installCurrencyConversionApi();
    }

    private void installCurrencyConversionApi() {
        final CurrencyConversionApi currencyConversionApi = Mockito.mock(CurrencyConversionApi.class);
        final CurrencyConversion currencyConversion = Mockito.mock(CurrencyConversion.class);
        final Set<Rate> rates = new HashSet<Rate>();
        rates.add(new Rate() {
            @Override
            public Currency getBaseCurrency() {
                return Currency.USD;
            }

            @Override
            public Currency getCurrency() {
                return Currency.BRL;
            }

            @Override
            public BigDecimal getValue() {
                return new BigDecimal("0.4234");
            }

            @Override
            public DateTime getConversionDate() {
                return new DateTime(DateTimeZone.UTC);
            }
        });
        Mockito.when(currencyConversion.getRates()).thenReturn(rates);
        try {
            Mockito.when(currencyConversionApi.getCurrencyConversion(Mockito.<Currency>any(), Mockito.<DateTime>any())).thenReturn(currencyConversion);
        } catch (CurrencyConversionException e) {
            throw new RuntimeException(e);
        }

        bind(CurrencyConversionApi.class).toInstance(currencyConversionApi);
    }
}
