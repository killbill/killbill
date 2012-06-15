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

package com.ning.billing.entitlement.events.user;

import org.joda.time.DateTime;

public class ApiEventMigrateBilling extends ApiEventBase {
    public ApiEventMigrateBilling(final ApiEventBuilder builder) {
        super(builder.setEventType(ApiEventType.MIGRATE_BILLING));
    }

    public ApiEventMigrateBilling(final ApiEventMigrateEntitlement input, final DateTime ctd) {
        super(new ApiEventBuilder()
                      .setSubscriptionId(input.getSubscriptionId())
                      .setEventPlan(input.getEventPlan())
                      .setEventPlanPhase(input.getEventPlanPhase())
                      .setEventPriceList(input.getPriceList())
                      .setActiveVersion(input.getActiveVersion())
                      .setEffectiveDate(ctd)
                      .setProcessedDate(input.getProcessedDate())
                      .setRequestedDate(input.getRequestedDate())
                      .setFromDisk(true)
                      .setEventType(ApiEventType.MIGRATE_BILLING));
    }

}
