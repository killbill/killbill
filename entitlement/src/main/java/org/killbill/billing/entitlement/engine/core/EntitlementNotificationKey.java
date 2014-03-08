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

package org.killbill.billing.entitlement.engine.core;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.notificationq.api.NotificationEvent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EntitlementNotificationKey implements NotificationEvent {

    private final UUID entitlementId;
    private final UUID bundleId;
    private final EntitlementNotificationKeyAction entitlementNotificationKeyAction;
    private final DateTime effectiveDate;

    @JsonCreator
    public EntitlementNotificationKey(@JsonProperty("entitlementId") final UUID entitlementId,
                                      @JsonProperty("bundleId") final UUID bundleId,
                                      @JsonProperty("entitlementNotificationKeyAction") final EntitlementNotificationKeyAction entitlementNotificationKeyAction,
                                      @JsonProperty("effectiveDate") final DateTime effectiveDate) {
        this.entitlementId = entitlementId;
        this.bundleId = bundleId;
        this.entitlementNotificationKeyAction = entitlementNotificationKeyAction;
        this.effectiveDate = effectiveDate;
    }

    public UUID getEntitlementId() {
        return entitlementId;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public EntitlementNotificationKeyAction getEntitlementNotificationKeyAction() {
        return entitlementNotificationKeyAction;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EntitlementNotificationKey{");
        sb.append("entitlementId=").append(entitlementId);
        sb.append(", bundleId=").append(bundleId);
        sb.append(", entitlementNotificationKeyAction=").append(entitlementNotificationKeyAction);
        sb.append(", effectiveDate=").append(effectiveDate);
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

        final EntitlementNotificationKey that = (EntitlementNotificationKey) o;

        if (entitlementId != null ? !entitlementId.equals(that.entitlementId) : that.entitlementId != null) {
            return false;
        }
        if (bundleId != null ? !bundleId.equals(that.bundleId) : that.bundleId != null) {
            return false;
        }
        if (entitlementNotificationKeyAction != that.entitlementNotificationKeyAction) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = entitlementId != null ? entitlementId.hashCode() : 0;
        result = 31 * result + (bundleId != null ? bundleId.hashCode() : 0);
        result = 31 * result + (entitlementNotificationKeyAction != null ? entitlementNotificationKeyAction.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        return result;
    }
}
