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

package com.ning.billing.util.notificationq;

import java.util.UUID;

import org.joda.time.DateTime;

public class DefaultNotification implements Notification {

    private final UUID id;
    private final String owner;
    private final String queueName;
    private final DateTime nextAvailableDate;
    private final NotificationLifecycleState lifecycleState;
    private final String notificationKey;
    private final DateTime effectiveDate;


    public DefaultNotification(UUID id, String owner, String queueName, DateTime nextAvailableDate,
            NotificationLifecycleState lifecycleState,
            String notificationKey, DateTime effectiveDate) {
        super();
        this.id = id;
        this.owner = owner;
        this.queueName = queueName;
        this.nextAvailableDate = nextAvailableDate;
        this.lifecycleState = lifecycleState;
        this.notificationKey = notificationKey;
        this.effectiveDate = effectiveDate;
    }

    public DefaultNotification(String queueName, String notificationKey, DateTime effectiveDate) {
        this(UUID.randomUUID(), null, queueName, null, NotificationLifecycleState.AVAILABLE, notificationKey, effectiveDate);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public DateTime getNextAvailableDate() {
        return nextAvailableDate;
    }

    @Override
    public NotificationLifecycleState getProcessingState() {
        return lifecycleState;
    }

    @Override
    public boolean isAvailableForProcessing(DateTime now) {
        switch(lifecycleState) {
        case AVAILABLE:
            break;
        case IN_PROCESSING:
            // Somebody already got the event, not available yet
            if (nextAvailableDate.isAfter(now)) {
                return false;
            }
            break;
        case PROCESSED:
            return false;
        default:
            throw new RuntimeException(String.format("Unkwnon IEvent processing state %s", lifecycleState));
        }
        return effectiveDate.isBefore(now);
    }

    @Override
    public String getNotificationKey() {
        return notificationKey;
    }

    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

	@Override
	public String getQueueName() {
		return queueName;
	}
}
