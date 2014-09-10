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

import java.math.BigDecimal;

import org.joda.time.DateTime;
import org.killbill.billing.usage.api.RolledUpUsage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UsageJson {

    private final String subscriptionId;
    private final String unitType;
    private final DateTime startTime;
    private final DateTime endTime;
    private final BigDecimal amount;

    @JsonCreator
    public UsageJson(@JsonProperty("subscriptionId") final String subscriptionId,
                     @JsonProperty("unitType") final String unitType,
                     @JsonProperty("startTime") final DateTime startTime,
                     @JsonProperty("endTime") final DateTime endTime,
                     @JsonProperty("amount") final BigDecimal amount) {
        this.subscriptionId = subscriptionId;
        this.unitType = unitType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.amount = amount;
    }

    public UsageJson(final RolledUpUsage usage) {
        this(usage.getSubscriptionId().toString(), usage.getUnitType(), usage.getStartTime(), usage.getEndTime(), usage.getAmount());
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getUnitType() {
        return unitType;
    }

    public DateTime getStartTime() {
        return startTime;
    }

    public DateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UsageJson)) {
            return false;
        }

        final UsageJson usageJson = (UsageJson) o;

        if (amount != null ? !amount.equals(usageJson.amount) : usageJson.amount != null) {
            return false;
        }
        if (endTime != null ? !endTime.equals(usageJson.endTime) : usageJson.endTime != null) {
            return false;
        }
        if (startTime != null ? !startTime.equals(usageJson.startTime) : usageJson.startTime != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(usageJson.subscriptionId) : usageJson.subscriptionId != null) {
            return false;
        }
        if (unitType != null ? !unitType.equals(usageJson.unitType) : usageJson.unitType != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = subscriptionId != null ? subscriptionId.hashCode() : 0;
        result = 31 * result + (unitType != null ? unitType.hashCode() : 0);
        result = 31 * result + (startTime != null ? startTime.hashCode() : 0);
        result = 31 * result + (endTime != null ? endTime.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        return result;
    }
}
