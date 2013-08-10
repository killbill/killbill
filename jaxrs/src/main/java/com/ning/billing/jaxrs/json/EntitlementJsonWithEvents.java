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

package com.ning.billing.jaxrs.json;

import java.util.List;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.clock.DefaultClock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EntitlementJsonWithEvents extends EntitlementJsonSimple {

    private final List<SubscriptionReadEventJson> events;

    private final List<SubscriptionDeletedEventJson> deletedEvents;

    private final List<SubscriptionNewEventJson> newEvents;

    public static class SubscriptionReadEventJson extends SubscriptionBaseEventJson {

        private final String eventId;
        private final DateTime effectiveDate;

        @JsonCreator
        public SubscriptionReadEventJson(@JsonProperty("eventId") final String eventId,
                                         @JsonProperty("billingPeriod") final String billingPeriod,
                                         @JsonProperty("requestedDt") final DateTime requestedDate,
                                         @JsonProperty("effectiveDt") final DateTime effectiveDate,
                                         @JsonProperty("product") final String product,
                                         @JsonProperty("priceList") final String priceList,
                                         @JsonProperty("eventType") final String eventType,
                                         @JsonProperty("phase") final String phase,
                                         @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase, auditLogs);
            this.eventId = eventId;
            this.effectiveDate = effectiveDate;
        }

        public String getEventId() {
            return eventId;
        }

        public DateTime getEffectiveDate() {
            return DefaultClock.toUTCDateTime(effectiveDate);
        }

        @Override
        public String toString() {
            return "SubscriptionReadEventJson [eventId=" + eventId
                   + ", effectiveDate=" + effectiveDate
                   + ", getBillingPeriod()=" + getBillingPeriod()
                   + ", getRequestedDate()=" + getRequestedDate()
                   + ", getProduct()=" + getProduct() + ", getPriceList()="
                   + getPriceList() + ", getEventType()=" + getEventType()
                   + ", getPhase()=" + getPhase() + ", getClass()="
                   + getClass() + ", hashCode()=" + hashCode()
                   + ", toString()=" + super.toString() + "]";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final SubscriptionReadEventJson that = (SubscriptionReadEventJson) o;

            if (effectiveDate != null ? !effectiveDate.equals(that.effectiveDate) : that.effectiveDate != null) {
                return false;
            }
            if (eventId != null ? !eventId.equals(that.eventId) : that.eventId != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = eventId != null ? eventId.hashCode() : 0;
            result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
            return result;
        }
    }

    public static class SubscriptionDeletedEventJson extends SubscriptionReadEventJson {

        @JsonCreator
        public SubscriptionDeletedEventJson(@JsonProperty("eventId") final String eventId,
                                            @JsonProperty("billingPeriod") final String billingPeriod,
                                            @JsonProperty("requestedDate") final DateTime requestedDate,
                                            @JsonProperty("effectiveDate") final DateTime effectiveDate,
                                            @JsonProperty("product") final String product,
                                            @JsonProperty("priceList") final String priceList,
                                            @JsonProperty("eventType") final String eventType,
                                            @JsonProperty("phase") final String phase,
                                            @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(eventId, billingPeriod, requestedDate, effectiveDate, product, priceList, eventType, phase, auditLogs);
        }
    }

    public static class SubscriptionNewEventJson extends SubscriptionBaseEventJson {

        @JsonCreator
        public SubscriptionNewEventJson(@JsonProperty("billingPeriod") final String billingPeriod,
                                        @JsonProperty("requestedDate") final DateTime requestedDate,
                                        @JsonProperty("product") final String product,
                                        @JsonProperty("priceList") final String priceList,
                                        @JsonProperty("eventType") final String eventType,
                                        @JsonProperty("phase") final String phase,
                                        @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase, auditLogs);
        }

        @Override
        public String toString() {
            return "SubscriptionNewEventJson [getBillingPeriod()="
                   + getBillingPeriod() + ", getRequestedDate()="
                   + getRequestedDate() + ", getProduct()=" + getProduct()
                   + ", getPriceList()=" + getPriceList()
                   + ", getEventType()=" + getEventType() + ", getPhase()="
                   + getPhase() + ", getClass()=" + getClass()
                   + ", hashCode()=" + hashCode() + ", toString()="
                   + super.toString() + "]";
        }
    }

    public abstract static class SubscriptionBaseEventJson extends JsonBase {

        private final String billingPeriod;
        private final DateTime requestedDate;
        private final String product;
        private final String priceList;
        private final String eventType;
        private final String phase;

        @JsonCreator
        public SubscriptionBaseEventJson(@JsonProperty("billingPeriod") final String billingPeriod,
                                         @JsonProperty("requestedDate") final DateTime requestedDate,
                                         @JsonProperty("product") final String product,
                                         @JsonProperty("priceList") final String priceList,
                                         @JsonProperty("eventType") final String eventType,
                                         @JsonProperty("phase") final String phase,
                                         @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
            super(auditLogs);
            this.billingPeriod = billingPeriod;
            this.requestedDate = DefaultClock.toUTCDateTime(requestedDate);
            this.product = product;
            this.priceList = priceList;
            this.eventType = eventType;
            this.phase = phase;
        }

        public String getBillingPeriod() {
            return billingPeriod;
        }

        public DateTime getRequestedDate() {
            return DefaultClock.toUTCDateTime(requestedDate);
        }

        public String getProduct() {
            return product;
        }

        public String getPriceList() {
            return priceList;
        }

        public String getEventType() {
            return eventType;
        }

        public String getPhase() {
            return phase;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("SubscriptionBaseEventJson");
            sb.append("{billingPeriod='").append(billingPeriod).append('\'');
            sb.append(", requestedDate=").append(requestedDate);
            sb.append(", product='").append(product).append('\'');
            sb.append(", priceList='").append(priceList).append('\'');
            sb.append(", eventType='").append(eventType).append('\'');
            sb.append(", phase='").append(phase).append('\'');
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

            final SubscriptionBaseEventJson that = (SubscriptionBaseEventJson) o;

            if (billingPeriod != null ? !billingPeriod.equals(that.billingPeriod) : that.billingPeriod != null) {
                return false;
            }
            if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
                return false;
            }
            if (phase != null ? !phase.equals(that.phase) : that.phase != null) {
                return false;
            }
            if (priceList != null ? !priceList.equals(that.priceList) : that.priceList != null) {
                return false;
            }
            if (product != null ? !product.equals(that.product) : that.product != null) {
                return false;
            }
            if (requestedDate != null ? !requestedDate.equals(that.requestedDate) : that.requestedDate != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = billingPeriod != null ? billingPeriod.hashCode() : 0;
            result = 31 * result + (requestedDate != null ? requestedDate.hashCode() : 0);
            result = 31 * result + (product != null ? product.hashCode() : 0);
            result = 31 * result + (priceList != null ? priceList.hashCode() : 0);
            result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
            result = 31 * result + (phase != null ? phase.hashCode() : 0);
            return result;
        }
    }

    @JsonCreator
    public EntitlementJsonWithEvents(@JsonProperty("accountId") @Nullable final String accountId,
                                     @JsonProperty("bundleId") @Nullable final String bundleId,
                                     @JsonProperty("entitlementId") @Nullable final String entitlementId,
                                     @JsonProperty("externalKey") @Nullable final String externalKey,
                                     @JsonProperty("events") @Nullable final List<SubscriptionReadEventJson> events,
                                     @JsonProperty("newEvents") @Nullable final List<SubscriptionNewEventJson> newEvents,
                                     @JsonProperty("deletedEvents") @Nullable final List<SubscriptionDeletedEventJson> deletedEvents,
                                     @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(accountId, bundleId, entitlementId, externalKey, auditLogs);
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }

    /*

    STEPH_ENT

    public EntitlementJsonWithEvents(final SubscriptionBase data,
                                      @Nullable final List<SubscriptionReadEventJson> events,
                                      @Nullable final List<SubscriptionNewEventJson> newEvents,
                                      @Nullable final List<SubscriptionDeletedEventJson> deletedEvents,
                                      @Nullable final List<AuditLog> auditLogs) {
        this(data.getId().toString(), events, newEvents, deletedEvents, toAuditLogJson(auditLogs));
    }

    public EntitlementJsonWithEvents(final UUID accountId, final UUID bundleId, final UUID entitlementId, final SubscriptionBaseTimeline input,
                                      final List<AuditLog> bundleAuditLogs, final Map<UUID, List<AuditLog>> subscriptionEventsAuditLogs) {
        super(input. input.getId().toString(), toAuditLogJson(bundleAuditLogs));

        this.events = new LinkedList<SubscriptionReadEventJson>();
        for (final ExistingEvent event : input.getExistingEvents()) {
            final PlanPhaseSpecifier spec = event.getPlanPhaseSpecifier();
            this.events.add(new SubscriptionReadEventJson(event.getEventId().toString(), spec.getBillingPeriod().toString(), event.getRequestedDate(), event.getEffectiveDate(),
                                                          spec.getProductName(), spec.getPriceListName(), event.getSubscriptionTransitionType().toString(), spec.getPhaseType().toString(),
                                                          toAuditLogJson(subscriptionEventsAuditLogs.get(event.getEventId()))));
        }
        this.newEvents = null;
        this.deletedEvents = null;
    }
    */


    public List<SubscriptionReadEventJson> getEvents() {
        return events;
    }

    public List<SubscriptionNewEventJson> getNewEvents() {
        return newEvents;
    }

    public List<SubscriptionDeletedEventJson> getDeletedEvents() {
        return deletedEvents;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("EntitlementJsonWithEvents");
        sb.append("{events=").append(events);
        sb.append(", deletedEvents=").append(deletedEvents);
        sb.append(", newEvents=").append(newEvents);
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

        final EntitlementJsonWithEvents that = (EntitlementJsonWithEvents) o;

        if (deletedEvents != null ? !deletedEvents.equals(that.deletedEvents) : that.deletedEvents != null) {
            return false;
        }
        if (events != null ? !events.equals(that.events) : that.events != null) {
            return false;
        }
        if (newEvents != null ? !newEvents.equals(that.newEvents) : that.newEvents != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = events != null ? events.hashCode() : 0;
        result = 31 * result + (deletedEvents != null ? deletedEvents.hashCode() : 0);
        result = 31 * result + (newEvents != null ? newEvents.hashCode() : 0);
        return result;
    }
}
