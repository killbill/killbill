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
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UsageCapacityInArrearAggregate implements UsageInArrearAggregate {

    private final List<UsageInArrearTierUnitDetail> tierDetails;
    private BigDecimal amount;

    @JsonCreator
    public UsageCapacityInArrearAggregate(@JsonProperty("tierDetails") List<UsageInArrearTierUnitDetail> tierDetails,
                                          @JsonProperty("amount") BigDecimal amount) {
        this.tierDetails = tierDetails;
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public List<UsageInArrearTierUnitDetail> getTierDetails() {
        return tierDetails;
    }
}
