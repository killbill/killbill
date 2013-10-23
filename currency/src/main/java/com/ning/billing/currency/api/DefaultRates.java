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

package com.ning.billing.currency.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Currency;

public class DefaultRates implements Rates {

    private final Map<Currency, BigDecimal> rates;


    public DefaultRates(final Map<Currency, BigDecimal> rates) {
        this.rates = rates;
    }

    @Override
    public Set<Currency> getCurrencies() {
        return rates.keySet();
    }

    @Override
    public BigDecimal getRate(final Currency currency) throws CurrencyConversionException {
        final BigDecimal result = rates.get(currency);
        if (result == null) {
            throw new CurrencyConversionException(ErrorCode.CURRENCY_NO_SUCH_RATE_FOR_CURRENCY, currency.name());
        }
        return result;
    }
}
