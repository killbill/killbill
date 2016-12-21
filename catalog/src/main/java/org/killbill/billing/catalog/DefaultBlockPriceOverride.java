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

import org.killbill.billing.catalog.api.BlockPriceOverride;
import org.killbill.billing.catalog.api.Currency;

public class DefaultBlockPriceOverride implements BlockPriceOverride {

    private String unitName;

    private Double size;

    private BigDecimal price;

    private Currency currency;

    @Override
    public String getUnitName() {
        return unitName;
    }

    @Override
    public Double getSize() {
        return size;
    }

    @Override
    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    public DefaultBlockPriceOverride( String unitName, Double size, BigDecimal price, Currency currency) {
        this.unitName = unitName;
        this.size = size;
        this.price = price;
        this.currency = currency;
    }
}
