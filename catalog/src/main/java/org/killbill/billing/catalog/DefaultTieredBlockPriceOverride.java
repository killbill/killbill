/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.catalog;

import java.math.BigDecimal;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.TieredBlockPriceOverride;

public class DefaultTieredBlockPriceOverride extends DefaultBlockPriceOverride implements TieredBlockPriceOverride {

    private Double max;

    @Override
    public Double getMax() {
        return max;
    }

    public DefaultTieredBlockPriceOverride(String unitName, Double size, BigDecimal price, Currency currency, Double max) {
        super(unitName, size, price, currency);
        this.max = max;
    }
}
