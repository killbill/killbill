/*
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

package org.killbill.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.usage.api.RolledUpUnit;
import org.killbill.billing.usage.api.RolledUpUsage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="RolledUpUsage")
public class RolledUpUsageJson {

    private final UUID subscriptionId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<RolledUpUnitJson> rolledUpUnits;

    @JsonCreator
    public RolledUpUsageJson(@JsonProperty("subscriptionId") final UUID subscriptionId,
                             @JsonProperty("startDate") final LocalDate startDate,
                             @JsonProperty("endDate") final LocalDate endDate,
                             @JsonProperty("rolledUpUnits") final List<RolledUpUnitJson> rolledUpUnits) {
        this.subscriptionId = subscriptionId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.rolledUpUnits = rolledUpUnits;
    }

    public RolledUpUsageJson(final RolledUpUsage input) {
        this(input.getSubscriptionId(), input.getStart(), input.getEnd(), ImmutableList.copyOf(Iterables.transform(input.getRolledUpUnits(), new Function<RolledUpUnit, RolledUpUnitJson>() {
            @Override
            public RolledUpUnitJson apply(final RolledUpUnit input) {
                return new RolledUpUnitJson(input);
            }
        })));
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<RolledUpUnitJson> getRolledUpUnits() {
        return rolledUpUnits;
    }

    @ApiModel(value="RolledUpUnit")
    public static class RolledUpUnitJson {

        private final String unitType;
        private final Long amount;

        @JsonCreator
        public RolledUpUnitJson(@JsonProperty("unitType") final String unitType,
                                @JsonProperty("amount") final Long amount) {
            this.unitType = unitType;
            this.amount = amount;
        }

        public RolledUpUnitJson(final RolledUpUnit input) {
            this(input.getUnitType(), input.getAmount());
        }

        public String getUnitType() {
            return unitType;
        }

        public Long getAmount() {
            return amount;
        }
    }
}
