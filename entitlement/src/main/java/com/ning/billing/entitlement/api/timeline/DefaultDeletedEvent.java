/*
 * Copyright 2010-2013 Ning, Inc.
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
package com.ning.billing.entitlement.api.timeline;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.DeletedEvent;

public class DefaultDeletedEvent implements DeletedEvent {

    private final UUID id;
    private final DateTime effectiveDate;

    public DefaultDeletedEvent(final UUID id, final DateTime effectiveDate) {
        this.id = id;
        this.effectiveDate = effectiveDate;
    }

    @Override
    public UUID getEventId() {
        return id;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }
}
