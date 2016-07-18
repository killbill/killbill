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

import org.killbill.billing.notification.plugin.api.ExtBusEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

/*
 * Use to communicate back with client after they registered a callback
 */
public class NotificationJson {

    private final String eventType;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String accountId;
    private final String objectType;
    @ApiModelProperty(dataType = "java.util.UUID")
    private final String objectId;

    @JsonCreator
    public NotificationJson(@JsonProperty("eventType") final String eventType,
                            @JsonProperty("accountId") final String accountId,
                            @JsonProperty("objectType") final String objectType,
                            @JsonProperty("objectId") final String objectId) {
        this.eventType = eventType;
        this.accountId = accountId;
        this.objectType = objectType;
        this.objectId = objectId;
    }

    public NotificationJson(final ExtBusEvent event) {
        this(event.getEventType().toString(),
             event.getAccountId() != null ? event.getAccountId().toString() : null,
             event.getObjectType().toString(),
             event.getObjectId() != null ? event.getObjectId().toString() : null);
    }

    public String getEventType() {
        return eventType;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }
}
