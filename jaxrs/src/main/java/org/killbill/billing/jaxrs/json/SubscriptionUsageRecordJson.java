/*
 * Copyright 2014 Groupon, Inc
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageRecord;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="SubscriptionUsageRecord")
public class SubscriptionUsageRecordJson {

    @ApiModelProperty(required = true)
    private final UUID subscriptionId;
    @ApiModelProperty(required = true)
    private final List<UnitUsageRecordJson> unitUsageRecords;
    @ApiModelProperty(required = false)
    private final String trackingId;

    @JsonCreator
    public SubscriptionUsageRecordJson(@JsonProperty("subscriptionId") final UUID subscriptionId,
                                       @JsonProperty("trackingId") final String trackingId,
                                       @JsonProperty("unitUsageRecords") final List<UnitUsageRecordJson> unitUsageRecords) {
        this.subscriptionId = subscriptionId;
        this.trackingId = trackingId;
        this.unitUsageRecords = unitUsageRecords;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public List<UnitUsageRecordJson> getUnitUsageRecords() {
        return unitUsageRecords;
    }

    public String getTrackingId() {
        return trackingId;
    }

    @ApiModel(value="UnitUsageRecord")
    public static class UnitUsageRecordJson {

        private final String unitType;
        private final List<UsageRecordJson> usageRecords;

        @JsonCreator
        public UnitUsageRecordJson(@JsonProperty("unitType") final String unitType,
                                   @JsonProperty("usageRecords") final List<UsageRecordJson> usageRecords) {
            this.unitType = unitType;
            this.usageRecords = usageRecords;
        }

        public String getUnitType() {
            return unitType;
        }

        public List<UsageRecordJson> getUsageRecords() {
            return usageRecords;
        }

        public UnitUsageRecord toUnitUsageRecord() {
            final List<UsageRecord> tmp = usageRecords.stream()
                    .map(UsageRecordJson::toUsageRecord)
                    .collect(Collectors.toUnmodifiableList());
            return new UnitUsageRecord(unitType, tmp);
        }
    }

    @ApiModel(value="UsageRecord")
    public static class UsageRecordJson {

        private final DateTime recordDate;
        private final BigDecimal amount;

        @JsonCreator
        public UsageRecordJson(@JsonProperty("recordDate") final DateTime recordDate,
                               @JsonProperty("amount") final BigDecimal amount) {
            this.recordDate = recordDate;
            this.amount = amount;
        }

        public DateTime getRecordDate() {
            return recordDate;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public UsageRecord toUsageRecord() {
            return new UsageRecord(recordDate, amount);
        }
    }

    public SubscriptionUsageRecord toSubscriptionUsageRecord() {
        final List<UnitUsageRecord> tmp = unitUsageRecords.stream()
                .map(UnitUsageRecordJson::toUnitUsageRecord)
                .collect(Collectors.toUnmodifiableList());
        return new SubscriptionUsageRecord(subscriptionId, trackingId, tmp);
    }
}
