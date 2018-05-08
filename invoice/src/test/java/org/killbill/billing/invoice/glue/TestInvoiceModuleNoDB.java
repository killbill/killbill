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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.currency.api.CurrencyConversion;
import org.killbill.billing.currency.api.CurrencyConversionApi;
import org.killbill.billing.currency.api.CurrencyConversionException;
import org.killbill.billing.currency.api.Rate;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.MockInvoiceDao;
import org.killbill.billing.mock.api.MockAccountUserApi;
import org.killbill.billing.mock.glue.MockAccountModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.mock.glue.MockTagModule;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.audit.dao.MockAuditDao;
import org.mockito.Mockito;

public class TestInvoiceModuleNoDB extends TestInvoiceModule {

    public TestInvoiceModuleNoDB(final KillbillConfigSource configSource) {
        super(configSource);
    }

    protected void installInvoiceDao() {
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
    }

    @Override
    public void configure() {
        super.configure();
        install(new GuicyKillbillTestNoDBModule(configSource));
        install(new MockNonEntityDaoModule(configSource));
        install(new MockTagModule(configSource));
        install(new MockAccountModule(configSource));
        bind(AuditDao.class).toInstance(new MockAuditDao());
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
