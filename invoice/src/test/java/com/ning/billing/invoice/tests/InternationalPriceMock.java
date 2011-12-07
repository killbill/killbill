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

package com.ning.billing.invoice.tests;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Price;

import java.math.BigDecimal;
import java.util.Date;

import static org.testng.Assert.fail;

public class InternationalPriceMock implements InternationalPrice {
    private final BigDecimal rate;

    public InternationalPriceMock(BigDecimal rate) {
        this.rate = rate;
    }

    @Override
    public Price[] getPrices() {
        fail();

        return null;
    }

    @Override
    public BigDecimal getPrice(Currency currency) {
        return rate;
    }

	@Override
	public Date getEffectiveDateForExistingSubscriptons() {
		return new Date();
	}
}
