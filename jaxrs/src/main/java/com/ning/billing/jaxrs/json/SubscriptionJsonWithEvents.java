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

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;
import org.joda.time.DateTime;

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

        @JsonCreator
        public SubscriptionReadEventJson(@JsonProperty("event_id") String eventId,
                @JsonProperty("billing_period") String billingPeriod,
                @JsonProperty("requested_dt") DateTime requestedDate,
                @JsonProperty("effective_dt") DateTime effectiveDate,
                @JsonProperty("product") String product,
                @JsonProperty("price_list") String priceList,
                @JsonProperty("event_type") String eventType,
                @JsonProperty("phase") String phase) {
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
    }

    public static class SubscriptionDeletedEventJson extends SubscriptionReadEventJson {
        @JsonCreator
        public SubscriptionDeletedEventJson(@JsonProperty("event_id") String eventId,
                @JsonProperty("billing_period") String billingPeriod,
                @JsonProperty("requested_dt") DateTime requestedDate,
                @JsonProperty("effective_dt") DateTime effectiveDate,
                @JsonProperty("product") String product,
                @JsonProperty("price_list") String priceList,
                @JsonProperty("event_type") String eventType,
                @JsonProperty("phase") String phase) {
            super(eventId, billingPeriod, requestedDate, effectiveDate, product, priceList, eventType, phase);

        }
    }


    public static class SubscriptionNewEventJson extends SubscriptionBaseEventJson {
        @JsonCreator
        public SubscriptionNewEventJson(@JsonProperty("billing_period") String billingPeriod,
                @JsonProperty("requested_dt") DateTime requestedDate,
                @JsonProperty("product") String product,
                @JsonProperty("price_list") String priceList,
                @JsonProperty("event_type") String eventType,
                @JsonProperty("phase") String phase) {
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

        @JsonCreator
        public SubscriptionBaseEventJson(@JsonProperty("billing_period") String billingPeriod,
                @JsonProperty("requested_dt") DateTime requestedDate,
                @JsonProperty("product") String product,
                @JsonProperty("price_list") String priceList,
                @JsonProperty("event_type") String eventType,
                @JsonProperty("phase") String phase) {
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
    }


    @JsonCreator
    public SubscriptionJsonWithEvents(@JsonProperty("subscription_id") String subscriptionId,
            @JsonProperty("events") List<SubscriptionReadEventJson> events,
            @JsonProperty("new_events") List<SubscriptionNewEventJson> newEvents,
            @JsonProperty("deleted_events") List<SubscriptionDeletedEventJson> deletedEvents) {
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
            List<SubscriptionReadEventJson> events, List<SubscriptionDeletedEventJson> deletedEvents, List<SubscriptionNewEventJson> newEvents) {
        super(data.getId().toString());
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }
    
    public SubscriptionJsonWithEvents(final UUID bundleId, final SubscriptionTimeline input) {
        super(input.getId().toString());
        this.events = new LinkedList<SubscriptionReadEventJson>();
        for (ExistingEvent cur : input.getExistingEvents()) {
            PlanPhaseSpecifier spec = cur.getPlanPhaseSpecifier();
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
}
