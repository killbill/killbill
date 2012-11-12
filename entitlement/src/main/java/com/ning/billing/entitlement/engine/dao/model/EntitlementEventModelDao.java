/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.entitlement.engine.dao.model;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.EventBaseBuilder;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventBuilder;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventMigrateBilling;
import com.ning.billing.entitlement.events.user.ApiEventMigrateEntitlement;
import com.ning.billing.entitlement.events.user.ApiEventReCreate;
import com.ning.billing.entitlement.events.user.ApiEventTransfer;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.ApiEventUncancel;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.entity.EntityBase;

public class EntitlementEventModelDao extends EntityBase {

    private final long totalOrdering;
    private final EventType eventType;
    private final ApiEventType userType;
    private final DateTime requestedDate;
    private final DateTime effectiveDate;
    private final UUID subscriptionId;
    private final String planName;
    private final String phaseName;
    private final String priceListName;
    private final long currentVersion;
    private final boolean isActive;

    public EntitlementEventModelDao(final UUID id, final long totalOrdering, final EventType eventType, final ApiEventType userType,
                                    final DateTime requestedDate, final DateTime effectiveDate, final UUID subscriptionId,
                                    final String planName, final String phaseName, final String priceListName, final long currentVersion,
                                    final boolean active, final DateTime createDate, final DateTime updateDate) {
        super(id, createDate, updateDate);
        this.totalOrdering = totalOrdering;
        this.eventType = eventType;
        this.userType = userType;
        this.requestedDate = requestedDate;
        this.effectiveDate = effectiveDate;
        this.subscriptionId = subscriptionId;
        this.planName = planName;
        this.phaseName = phaseName;
        this.priceListName = priceListName;
        this.currentVersion = currentVersion;
        this.isActive = active;
    }

    public EntitlementEventModelDao(final EntitlementEvent src) {
        super(src.getId(), src.getCreatedDate(), src.getUpdatedDate());
        this.totalOrdering = src.getTotalOrdering();
        this.eventType = src.getType();
        this.userType = eventType == EventType.API_USER ? ((ApiEvent) src).getEventType() : null;
        this.requestedDate = src.getRequestedDate();
        this.effectiveDate = src.getEffectiveDate();
        this.subscriptionId = src.getSubscriptionId();
        this.planName = eventType == EventType.API_USER ? ((ApiEvent) src).getEventPlan() : null;
        this.phaseName = eventType == EventType.API_USER ? ((ApiEvent) src).getEventPlanPhase() : ((PhaseEvent) src).getPhase();
        this.priceListName = eventType == EventType.API_USER ? ((ApiEvent) src).getPriceList() : null;
        this.currentVersion = src.getActiveVersion();
        this.isActive = src.isActive();
    }

    public long getTotalOrdering() {
        return totalOrdering;
    }

    public String getEventType() {
        return eventType != null ? eventType.toString() : null;
    }

    // TODO required for bindings
    public String getUserType() {
        return userType != null ? userType.toString() : null;
    }


    public EventType getEventTypeX() {
        return eventType;
    }


    public ApiEventType getUserTypeX() {
        return userType;
    }

    public DateTime getRequestedDate() {
        return requestedDate;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getPriceListName() {
        return priceListName;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }

    // TODO required for jdbi binder
    public boolean getIsActive() {
        return isActive;
    }

    public boolean isActive() {
        return isActive;
    }

    public static EntitlementEvent toEntitlementEvent(EntitlementEventModelDao src) {

        final EventBaseBuilder<?> base = ((src.getEventTypeX() == EventType.PHASE) ?
                                          new PhaseEventBuilder() :
                                          new ApiEventBuilder())
                .setTotalOrdering(src.getTotalOrdering())
                .setUuid(src.getId())
                .setSubscriptionId(src.getSubscriptionId())
                .setCreatedDate(src.getCreatedDate())
                .setUpdatedDate(src.getUpdatedDate())
                .setRequestedDate(src.getRequestedDate())
                .setEffectiveDate(src.getEffectiveDate())
                .setProcessedDate(src.getCreatedDate())
                .setActiveVersion(src.getCurrentVersion())
                .setActive(src.isActive());

        EntitlementEvent result = null;
        if (src.getEventTypeX() == EventType.PHASE) {
            result = new PhaseEventData(new PhaseEventBuilder(base).setPhaseName(src.getPhaseName()));
        } else if (src.getEventTypeX() == EventType.API_USER) {
            final ApiEventBuilder builder = new ApiEventBuilder(base)
                    .setEventPlan(src.getPlanName())
                    .setEventPlanPhase(src.getPhaseName())
                    .setEventPriceList(src.getPriceListName())
                    .setEventType(src.getUserTypeX())
                    .setFromDisk(true);

            if (src.getUserTypeX() == ApiEventType.CREATE) {
                result = new ApiEventCreate(builder);
            } else if (src.getUserTypeX() == ApiEventType.RE_CREATE) {
                result = new ApiEventReCreate(builder);
            } else if (src.getUserTypeX() == ApiEventType.MIGRATE_ENTITLEMENT) {
                result = new ApiEventMigrateEntitlement(builder);
            } else if (src.getUserTypeX() == ApiEventType.MIGRATE_BILLING) {
                result = new ApiEventMigrateBilling(builder);
            } else if (src.getUserTypeX() == ApiEventType.TRANSFER) {
                result = new ApiEventTransfer(builder);
            } else if (src.getUserTypeX() == ApiEventType.CHANGE) {
                result = new ApiEventChange(builder);
            } else if (src.getUserTypeX() == ApiEventType.CANCEL) {
                result = new ApiEventCancel(builder);
            } else if (src.getUserTypeX() == ApiEventType.RE_CREATE) {
                result = new ApiEventReCreate(builder);
            } else if (src.getUserTypeX() == ApiEventType.UNCANCEL) {
                result = new ApiEventUncancel(builder);
            }
        } else {
            throw new EntitlementError(String.format("Can't figure out event %s", src.getEventTypeX()));
        }
        return result;
    }
}
