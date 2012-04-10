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

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;
import org.joda.time.DateTime;

import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.util.clock.DefaultClock;

public class SubscriptionJson {

    @JsonView(BundleTimelineViews.Base.class)
    private final String subscriptionId;

    @JsonView(BundleTimelineViews.Base.class)
    private final String bundleId;

    @JsonView(BundleTimelineViews.Base.class)
    private final String productName;

    @JsonView(BundleTimelineViews.Base.class)
    private final String productCategory;

    @JsonView(BundleTimelineViews.Base.class)
    private final String billingPeriod;

    @JsonView(BundleTimelineViews.Base.class)
    private final String priceList;

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
    public SubscriptionJson(@JsonProperty("subscription_id") String subscriptionId,
            @JsonProperty("bundle_id") String bundleId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("product_category") String productCategory,
            @JsonProperty("billing_period") String billingPeriod,
            @JsonProperty("price_list") String priceList,
            @JsonProperty("events") List<SubscriptionReadEventJson> events,
            @JsonProperty("new_events") List<SubscriptionNewEventJson> newEvents,
            @JsonProperty("deleted_events") List<SubscriptionDeletedEventJson> deletedEvents) {
        super();
        this.subscriptionId = subscriptionId;
        this.bundleId = bundleId;
        this.productName = productName;
        this.productCategory = productCategory;
        this.billingPeriod = billingPeriod;
        this.priceList = priceList;
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }
    
    public SubscriptionJson() {
        this.subscriptionId = null;
        this.bundleId = null;
        this.productName = null;
        this.productCategory = null;
        this.billingPeriod = null;
        this.priceList = null;
        this.events = null;
        this.deletedEvents = null;
        this.newEvents = null;
    }
    
    public SubscriptionJson(final Subscription data,
    		List<SubscriptionReadEventJson> events, List<SubscriptionDeletedEventJson> deletedEvents, List<SubscriptionNewEventJson> newEvents) {
        this.subscriptionId = data.getId().toString();
        this.bundleId = data.getBundleId().toString();
        this.productName = data.getCurrentPlan().getProduct().getName();
        this.productCategory = data.getCurrentPlan().getProduct().getCategory().toString();
        this.billingPeriod = data.getCurrentPlan().getBillingPeriod().toString();
        this.priceList = data.getCurrentPriceList();
        this.events = events;
        this.deletedEvents = deletedEvents;
        this.newEvents = newEvents;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getProductName() {
        return productName;
    }

    public String getProductCategory() {
        return productCategory;
    }

    public String getBillingPeriod() {
        return billingPeriod;
    }

    public String getPriceList() {
        return priceList;
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
    public String toString() {
        return "SubscriptionJson [subscriptionId=" + subscriptionId
                + ", bundleId=" + bundleId + ", productName=" + productName
                + ", productCategory=" + productCategory + ", billingPeriod="
                + billingPeriod + ", priceList=" + priceList + ", events="
                + events + ", deletedEvents=" + deletedEvents + ", newEvents="
                + newEvents + "]";
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((billingPeriod == null) ? 0 : billingPeriod.hashCode());
		result = prime * result
				+ ((bundleId == null) ? 0 : bundleId.hashCode());
		result = prime * result
				+ ((priceList == null) ? 0 : priceList.hashCode());
		result = prime * result
				+ ((productCategory == null) ? 0 : productCategory.hashCode());
		result = prime * result
				+ ((productName == null) ? 0 : productName.hashCode());
		result = prime * result
				+ ((subscriptionId == null) ? 0 : subscriptionId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (equalsNoId(obj) == false) {
			return false;
		}
		SubscriptionJson other = (SubscriptionJson) obj;
		if (subscriptionId == null) {
			if (other.subscriptionId != null)
				return false;
		} else if (!subscriptionId.equals(other.subscriptionId))
			return false;
		return true;
	}

	public boolean equalsNoId(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SubscriptionJson other = (SubscriptionJson) obj;
		if (billingPeriod == null) {
			if (other.billingPeriod != null)
				return false;
		} else if (!billingPeriod.equals(other.billingPeriod))
			return false;
		if (bundleId == null) {
			if (other.bundleId != null)
				return false;
		} else if (!bundleId.equals(other.bundleId))
			return false;
		if (priceList == null) {
			if (other.priceList != null)
				return false;
		} else if (!priceList.equals(other.priceList))
			return false;
		if (productCategory == null) {
			if (other.productCategory != null)
				return false;
		} else if (!productCategory.equals(other.productCategory))
			return false;
		if (productName == null) {
			if (other.productName != null)
				return false;
		} else if (!productName.equals(other.productName))
			return false;
		return true;
	}
    
    
}
