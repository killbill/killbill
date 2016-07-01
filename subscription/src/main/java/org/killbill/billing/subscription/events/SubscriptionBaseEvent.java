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

package org.killbill.billing.subscription.events;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.util.entity.Entity;


public interface SubscriptionBaseEvent extends Comparable<SubscriptionBaseEvent>, Entity {

    public enum EventType {
        API_USER,
        PHASE,
        BCD_UPDATE
    }

    public EventType getType();

    public long getTotalOrdering();

    public boolean isActive();

    public DateTime getEffectiveDate();

    public UUID getSubscriptionId();
}
