/* 
 * Copyright 2010-2011 Ning, Inc.
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

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.util.clock.DefaultClock;

public class SubscriptionJsonWithEvents extends SubscriptionJsonSimple {
    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<SubscriptionReadEventJson> events;

    @JsonView(BundleTimelineViews.WriteTimeline.class)
    private final List<SubscriptionDeletedEventJson> deletedEvents;

    @JsonView(BundleTimelineViews.WriteTimeline.class)
    private final List<SubscriptionNewEventJson> newEvents;

    public static class SubscriptionReadEventJson extends SubscriptionBaseEventJson {
        @JsonView(BundleTimelineViews.Timeline.class)
        private final String eventId;

        @JsonView(BundleTimelineViews.Timeline.class)
        private final DateTime effectiveDate;

        public SubscriptionReadEventJson() {
            super();
            this.eventId = null;
            this.effectiveDate = null;
        }

        @JsonCreator
        public SubscriptionReadEventJson(@JsonProperty("eventId") final String eventId,
                                         @JsonProperty("billingPeriod") final String billingPeriod,
                                         @JsonProperty("requestedDt") final DateTime requestedDate,
                                         @JsonProperty("effectiveDt") final DateTime effectiveDate,
                                         @JsonProperty("product") final String product,
                                         @JsonProperty("priceList") final String priceList,
                                         @JsonProperty("eventType") final String eventType,
                                         @JsonProperty("phase") final String phase) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase);
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
        public SubscriptionDeletedEventJson(@JsonProperty("event_id") final String eventId,
                                            @JsonProperty("billing_period") final String billingPeriod,
                                            @JsonProperty("requested_date") final DateTime requestedDate,
                                            @JsonProperty("effective_date") final DateTime effectiveDate,
                                            @JsonProperty("product") final String product,
                                            @JsonProperty("price_list") final String priceList,
                                            @JsonProperty("event_type") final String eventType,
                                            @JsonProperty("phase") final String phase) {
            super(eventId, billingPeriod, requestedDate, effectiveDate, product, priceList, eventType, phase);
        }
    }


    public static class SubscriptionNewEventJson extends SubscriptionBaseEventJson {
        @JsonCreator
        public SubscriptionNewEventJson(@JsonProperty("billing_period") final String billingPeriod,
                                        @JsonProperty("requested_date") final DateTime requestedDate,
                                        @JsonProperty("product") final String product,
                                        @JsonProperty("price_list") final String priceList,
                                        @JsonProperty("event_type") final String eventType,
                                        @JsonProperty("phase") final String phase) {
            super(billingPeriod, requestedDate, product, priceList, eventType, phase);
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

    public static class SubscriptionBaseEventJson {
        @JsonView(BundleTimelineViews.Timeline.class)
        private final String billingPeriod;

        @JsonView(BundleTimelineViews.Timeline.class)
        private final DateTime requestedDate;


        @JsonView(BundleTimelineViews.Timeline.class)
        private final String product;

        @JsonView(BundleTimelineViews.Timeline.class)
        private final String priceList;

        @JsonView(BundleTimelineViews.Timeline.class)
        private final String eventType;

        @JsonView(BundleTimelineViews.Timeline.class)
        private final String phase;

        public SubscriptionBaseEventJson() {
            this.billingPeriod = null;
            this.requestedDate = null;
            this.product = null;
            this.priceList = null;
            this.eventType = null;
            this.phase = null;
        }

        @JsonCreator
        public SubscriptionBaseEventJson(@JsonProperty("billing_period") final String billingPeriod,
                                         @JsonProperty("requested_date") final DateTime requestedDate,
                                         @JsonProperty("product") final String product,
                                         @JsonProperty("price_list") final String priceList,
                                         @JsonProperty("event_type") final String eventType,
                                         @JsonProperty("phase") final String phase) {
            super();
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
    public SubscriptionJsonWithEvents(@JsonProperty("subscription_id") final String subscriptionId,
                                      @JsonProperty("events") final List<SubscriptionReadEventJson> events,
                                      @JsonProperty("new_events") final List<SubscriptionNewEventJson> newEvents,
                                      @JsonProperty("deleted_events") final List<SubscriptionDeletedEventJson> deletedEvents) {
        super(subscriptionId);
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }

    public SubscriptionJsonWithEvents() {
        super(null);
        this.events = null;
        this.deletedEvents = null;
        this.newEvents = null;
    }

    public SubscriptionJsonWithEvents(final Subscription data,
                                      final List<SubscriptionReadEventJson> events, final List<SubscriptionDeletedEventJson> deletedEvents, final List<SubscriptionNewEventJson> newEvents) {
        super(data.getId().toString());
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }

    public SubscriptionJsonWithEvents(final UUID bundleId, final SubscriptionTimeline input) {
        super(input.getId().toString());
        this.events = new LinkedList<SubscriptionReadEventJson>();
        for (final ExistingEvent cur : input.getExistingEvents()) {
            final PlanPhaseSpecifier spec = cur.getPlanPhaseSpecifier();
            this.events.add(new SubscriptionReadEventJson(cur.getEventId().toString(), spec.getBillingPeriod().toString(), cur.getRequestedDate(), cur.getEffectiveDate(),
                                                          spec.getProductName(), spec.getPriceListName(), cur.getSubscriptionTransitionType().toString(), spec.getPhaseType().toString()));
        }
        this.deletedEvents = null;
        this.newEvents = null;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionJsonWithEvents that = (SubscriptionJsonWithEvents) o;

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
