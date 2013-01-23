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
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.entity.dao.EntityModelDao;

public class EntitlementEventModelDao extends EntityBase implements EntityModelDao<EntitlementEvent> {

    private long totalOrdering;
    private EventType eventType;
    private ApiEventType userType;
    private DateTime requestedDate;
    private DateTime effectiveDate;
    private UUID subscriptionId;
    private String planName;
    private String phaseName;
    private String priceListName;
    private long currentVersion;
    private boolean isActive;

    public EntitlementEventModelDao() { /* For the DAO mapper */ }

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

    public EventType getEventType() {
        return eventType;
    }

    public ApiEventType getUserType() {
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

    public static EntitlementEvent toEntitlementEvent(final EntitlementEventModelDao src) {

        if (src == null) {
            return null;
        }

        final EventBaseBuilder<?> base = ((src.getEventType() == EventType.PHASE) ?
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
        if (src.getEventType() == EventType.PHASE) {
            result = new PhaseEventData(new PhaseEventBuilder(base).setPhaseName(src.getPhaseName()));
        } else if (src.getEventType() == EventType.API_USER) {
            final ApiEventBuilder builder = new ApiEventBuilder(base)
                    .setEventPlan(src.getPlanName())
                    .setEventPlanPhase(src.getPhaseName())
                    .setEventPriceList(src.getPriceListName())
                    .setEventType(src.getUserType())
                    .setFromDisk(true);

            if (src.getUserType() == ApiEventType.CREATE) {
                result = new ApiEventCreate(builder);
            } else if (src.getUserType() == ApiEventType.RE_CREATE) {
                result = new ApiEventReCreate(builder);
            } else if (src.getUserType() == ApiEventType.MIGRATE_ENTITLEMENT) {
                result = new ApiEventMigrateEntitlement(builder);
            } else if (src.getUserType() == ApiEventType.MIGRATE_BILLING) {
                result = new ApiEventMigrateBilling(builder);
            } else if (src.getUserType() == ApiEventType.TRANSFER) {
                result = new ApiEventTransfer(builder);
            } else if (src.getUserType() == ApiEventType.CHANGE) {
                result = new ApiEventChange(builder);
            } else if (src.getUserType() == ApiEventType.CANCEL) {
                result = new ApiEventCancel(builder);
            } else if (src.getUserType() == ApiEventType.RE_CREATE) {
                result = new ApiEventReCreate(builder);
            } else if (src.getUserType() == ApiEventType.UNCANCEL) {
                result = new ApiEventUncancel(builder);
            }
        } else {
            throw new EntitlementError(String.format("Can't figure out event %s", src.getEventType()));
        }
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("EntitlementEventModelDao");
        sb.append("{totalOrdering=").append(totalOrdering);
        sb.append(", eventType=").append(eventType);
        sb.append(", userType=").append(userType);
        sb.append(", requestedDate=").append(requestedDate);
        sb.append(", effectiveDate=").append(effectiveDate);
        sb.append(", subscriptionId=").append(subscriptionId);
        sb.append(", planName='").append(planName).append('\'');
        sb.append(", phaseName='").append(phaseName).append('\'');
        sb.append(", priceListName='").append(priceListName).append('\'');
        sb.append(", currentVersion=").append(currentVersion);
        sb.append(", isActive=").append(isActive);
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
        if (!super.equals(o)) {
            return false;
        }

        final EntitlementEventModelDao that = (EntitlementEventModelDao) o;

        if (currentVersion != that.currentVersion) {
            return false;
        }
        if (isActive != that.isActive) {
            return false;
        }
        if (totalOrdering != that.totalOrdering) {
            return false;
        }
        if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
            return false;
        }
        if (eventType != that.eventType) {
            return false;
        }
        if (phaseName != null ? !phaseName.equals(that.phaseName) : that.phaseName != null) {
            return false;
        }
        if (planName != null ? !planName.equals(that.planName) : that.planName != null) {
            return false;
        }
        if (priceListName != null ? !priceListName.equals(that.priceListName) : that.priceListName != null) {
            return false;
        }
        if (requestedDate != null ? !requestedDate.equals(that.requestedDate) : that.requestedDate != null) {
            return false;
        }
        if (subscriptionId != null ? !subscriptionId.equals(that.subscriptionId) : that.subscriptionId != null) {
            return false;
        }
        if (userType != that.userType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (totalOrdering ^ (totalOrdering >>> 32));
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (userType != null ? userType.hashCode() : 0);
        result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (subscriptionId != null ? subscriptionId.hashCode() : 0);
        result = 31 * result + (planName != null ? planName.hashCode() : 0);
        result = 31 * result + (phaseName != null ? phaseName.hashCode() : 0);
        result = 31 * result + (priceListName != null ? priceListName.hashCode() : 0);
        result = 31 * result + (int) (currentVersion ^ (currentVersion >>> 32));
        result = 31 * result + (isActive ? 1 : 0);
        return result;
    }

    @Override
    public TableName getTableName() {
        return TableName.SUBSCRIPTION_EVENTS;
    }

    @Override
    public TableName getHistoryTableName() {
        return null;
    }
}
