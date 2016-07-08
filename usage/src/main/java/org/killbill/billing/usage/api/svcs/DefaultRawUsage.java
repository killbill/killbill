/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.usage.api.svcs;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.usage.RawUsage;

public class DefaultRawUsage implements RawUsage {

    private final UUID subscriptionId;
    private final LocalDate recordDate;
    private final String unitType;
    private final Long amount;

    public DefaultRawUsage(final UUID subscriptionId, final LocalDate recordDate, final String unitType, final Long amount) {
        this.subscriptionId = subscriptionId;
        this.recordDate = recordDate;
        this.unitType = unitType;
        this.amount = amount;
    }

    @Override
    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    @Override
    public LocalDate getDate() {
        return recordDate;
    }

    @Override
    public String getUnitType() {
        return unitType;
    }

    @Override
    public Long getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultRawUsage{");
        sb.append("subscriptionId=").append(subscriptionId);
        sb.append(", recordDate=").append(recordDate);
        sb.append(", unitType='").append(unitType).append('\'');
        sb.append(", amount=").append(amount);
        sb.append('}');
        return sb.toString();
    }
}
