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
}
