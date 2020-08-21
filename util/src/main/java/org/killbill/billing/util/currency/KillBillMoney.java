/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.util.currency;

import java.math.BigDecimal;

import org.joda.money.CurrencyUnit;

import org.killbill.billing.catalog.api.Currency;

public class KillBillMoney {

    public static final int ROUNDING_METHOD = BigDecimal.ROUND_HALF_UP;
    public static final int MAX_SCALE = 9;

    private KillBillMoney() {}

    public static BigDecimal of(final BigDecimal amount, final Currency currency) {
        final CurrencyUnit currencyUnit = CurrencyUnit.of(currency.toString());
        return amount.setScale(currencyUnit.getDecimalPlaces(), ROUNDING_METHOD);
    }
}
