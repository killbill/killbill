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

package org.killbill.billing.server.notifications;

import java.util.UUID;

import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PushNotificationKey implements NotificationEvent {

    private final UUID tenantId;
    private final UUID accountId;
    private final String eventType;
    private final String objectType;
    private final UUID objectId;
    private final int attemptNumber;
    private final String url;
    private final String metaData;

    @JsonCreator
    public PushNotificationKey(@JsonProperty("tenantId") final UUID tenantId,
                               @JsonProperty("accountId") final UUID accountId,
                               @JsonProperty("eventType") final String eventType,
                               @JsonProperty("objectType") final String objectType,
                               @JsonProperty("objectId") final UUID objectId,
                               @JsonProperty("attemptNumber")  final int attemptNumber,
                               @JsonProperty("metaData")  final String metaData,
                               @JsonProperty("url") final String url) {
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.eventType = eventType;
        this.objectType = objectType;
        this.objectId = objectId;
        this.attemptNumber = attemptNumber;
        this.metaData = metaData;
        this.url = url;
    }

    public PushNotificationKey(final PushNotificationKey key, final int attemptNumber) {
        this(key.getTenantId(), key.getAccountId(), key.getEventType(), key.getObjectType(), key.getObjectId(),
             attemptNumber, key.getMetaData(), key.getUrl());
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public String getUrl() {
        return url;
    }

    public String getMetaData() {
        return metaData;
    }

    @Override
    public String toString() {
        return "PushNotificationKey{" +
               "tenantId=" + tenantId +
               ", accountId=" + accountId +
               ", eventType='" + eventType + '\'' +
               ", objectType='" + objectType + '\'' +
               ", objectId=" + objectId +
               ", metaData=" + metaData +
               ", attemptNumber=" + attemptNumber +
               ", url='" + url + '\'' +
               '}';
    }
}
