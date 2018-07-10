/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.invoice.usage.details;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UsageConsumableInArrearTierUnitAggregate extends UsageInArrearTierUnitDetail {

    private final int tierBlockSize;
    private BigDecimal amount;

    public UsageConsumableInArrearTierUnitAggregate(int tier, String tierUnit, BigDecimal tierPrice, Integer tierBlockSize, Integer quantity) {
        this(tier, tierUnit, tierPrice, tierBlockSize, quantity, computeAmount(tierPrice, quantity));
    }

    @JsonCreator
    public UsageConsumableInArrearTierUnitAggregate(@JsonProperty("tier") int tier,
                                                    @JsonProperty("tierUnit") String tierUnit,
                                                    @JsonProperty("tierPrice") BigDecimal tierPrice,
                                                    @JsonProperty("tierBlockSize") Integer tierBlockSize,
                                                    @JsonProperty("quantity") Integer quantity,
                                                    @JsonProperty("amount") BigDecimal amount) {
        super(tier, tierUnit, tierPrice, quantity);
        this.amount = amount;
        this.tierBlockSize = tierBlockSize;
    }

    public int getTier() {
        return tier;
    }

    public String getTierUnit() {
        return tierUnit;
    }

    public BigDecimal getTierPrice() {
        return tierPrice;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getTierBlockSize() {
        return tierBlockSize;
    }

    public void updateQuantityAndAmount(final Integer addionalQuantity) {
        this.quantity = quantity + addionalQuantity;
        this.amount = computeAmount(tierPrice, quantity);
    }

    private static BigDecimal computeAmount(final BigDecimal targetTierPrice, final Integer targetQuantity) {
        return targetTierPrice.multiply(BigDecimal.valueOf(targetQuantity));
    }
}
