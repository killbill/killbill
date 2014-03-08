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

package org.killbill.billing.overdue.notification;

import java.util.UUID;

import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OverdueAsyncBusNotificationKey extends OverdueCheckNotificationKey implements NotificationEvent {

    private final OverdueAsyncBusNotificationAction action;

    public enum OverdueAsyncBusNotificationAction {
        REFRESH,
        CLEAR
    }

    @JsonCreator
    public OverdueAsyncBusNotificationKey(@JsonProperty("uuidKey") final UUID uuidKey,
                                          @JsonProperty("action") final OverdueAsyncBusNotificationAction action) {
        super(uuidKey);
        this.action = action;
    }


    public OverdueAsyncBusNotificationAction getAction() {
        return action;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverdueAsyncBusNotificationKey)) {
            return false;
        }

        final OverdueAsyncBusNotificationKey that = (OverdueAsyncBusNotificationKey) o;

        if (action != that.action) {
            return false;
        }
        if (getUuidKey() != null ? !getUuidKey().equals(that.getUuidKey()) : that.getUuidKey() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getUuidKey() != null ? getUuidKey().hashCode() : 0;
        result = 31 * result + (action != null ? action.hashCode() : 0);
        return result;
    }
}
