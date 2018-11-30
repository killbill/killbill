/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

import java.util.UUID;

import org.killbill.billing.notification.plugin.api.ExtBusEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/*
 * Use to communicate back with client after they registered a callback
 */

@ApiModel(value="Notification")
public class NotificationJson {

    private final String eventType;
    private final UUID accountId;
    private final String objectType;
    private final UUID objectId;
    private String metaData;

    @JsonCreator
    public NotificationJson(@JsonProperty("eventType") final String eventType,
                            @JsonProperty("accountId") final UUID accountId,
                            @JsonProperty("objectType") final String objectType,
                            @JsonProperty("objectId") final UUID objectId,
                            @JsonProperty("metaData") final String metaData) {
        this.eventType = eventType;
        this.accountId = accountId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.metaData = metaData;
    }

    public NotificationJson(final ExtBusEvent event) {
        this(event.getEventType().toString(),
             event.getAccountId(),
             event.getObjectType().toString(),
             event.getObjectId(),
             event.getMetaData());
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public String getMetaData() {
        return metaData;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("NotificationJson{");
        sb.append("eventType='").append(eventType).append('\'');
        sb.append(", accountId=").append(accountId);
        sb.append(", objectType='").append(objectType).append('\'');
        sb.append(", objectId=").append(objectId);
        sb.append(", metaData='").append(metaData).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final NotificationJson that = (NotificationJson) o;

        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
            return false;
        }
        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (objectType != null ? !objectType.equals(that.objectType) : that.objectType != null) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }
        return metaData != null ? metaData.equals(that.metaData) : that.metaData == null;
    }

    @Override
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        result = 31 * result + (metaData != null ? metaData.hashCode() : 0);
        return result;
    }
}
