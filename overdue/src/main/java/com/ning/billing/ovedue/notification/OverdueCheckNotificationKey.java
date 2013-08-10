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

package com.ning.billing.ovedue.notification;

import java.util.UUID;

import com.ning.billing.entitlement.api.Type;
import com.ning.billing.notificationq.DefaultUUIDNotificationKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OverdueCheckNotificationKey extends DefaultUUIDNotificationKey {

    private final Type type;

    @JsonCreator
    public OverdueCheckNotificationKey(@JsonProperty("uuidKey") final UUID uuidKey,
                                       @JsonProperty("type") final Type type) {
        super(uuidKey);
        this.type = type;
    }

    // Hack : We default to SubscriptionBaseBundle which is the only one supported at the time
    public Type getType() {
        return type == null ? Type.SUBSCRIPTION_BUNDLE : type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        final OverdueCheckNotificationKey that = (OverdueCheckNotificationKey) o;

        if (type != that.type) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("OverdueCheckNotificationKey");
        sb.append("{type=").append(type);
        sb.append(", uuidKey=").append(getUuidKey());
        sb.append('}');
        return sb.toString();
    }
}
