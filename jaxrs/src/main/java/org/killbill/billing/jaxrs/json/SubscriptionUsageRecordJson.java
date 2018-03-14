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

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageRecord;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
            final List<UsageRecord> tmp = ImmutableList.copyOf(Iterables.transform(usageRecords, new Function<UsageRecordJson, UsageRecord>() {
                @Override
                public UsageRecord apply(final UsageRecordJson input) {
                    return input.toUsageRecord();
                }
            }));
            return new UnitUsageRecord(unitType, tmp);
        }
    }

    @ApiModel(value="UsageRecord")
    public static class UsageRecordJson {

        private final LocalDate recordDate;
        private final Long amount;

        @JsonCreator
        public UsageRecordJson(@JsonProperty("recordDate") final LocalDate recordDate,
                               @JsonProperty("amount") final Long amount) {
            this.recordDate = recordDate;
            this.amount = amount;
        }

        public LocalDate getRecordDate() {
            return recordDate;
        }

        public Long getAmount() {
            return amount;
        }

        public UsageRecord toUsageRecord() {
            return new UsageRecord(recordDate, amount);
        }
    }

    public SubscriptionUsageRecord toSubscriptionUsageRecord() {
        final List<UnitUsageRecord> tmp = ImmutableList.copyOf(Iterables.transform(unitUsageRecords, new Function<UnitUsageRecordJson, UnitUsageRecord>() {
            @Override
            public UnitUsageRecord apply(final UnitUsageRecordJson input) {
                return input.toUnitUsageRecord();
            }
        }));
        final SubscriptionUsageRecord result = new SubscriptionUsageRecord(subscriptionId, trackingId, tmp);
        return result;
    }
}
