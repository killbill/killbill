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

package org.killbill.billing.invoice.proRations;

import java.math.BigDecimal;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Price;

import static org.testng.Assert.fail;

public class InternationalPriceMock implements InternationalPrice {

    private final BigDecimal rate;

    public InternationalPriceMock(final BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    public Price[] getPrices() {
        fail();

        return null;
    }

    @Override
    public BigDecimal getPrice(final Currency currency) {
        return rate;
    }

    @Override
    public boolean isZero() {
        return rate.compareTo(BigDecimal.ZERO) == 0;
    }

}
