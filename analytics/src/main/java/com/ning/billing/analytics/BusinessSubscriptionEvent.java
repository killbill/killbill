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

package com.ning.billing.analytics;

import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.ISubscription;

/**
 * Describe an event associated with a transition between two BusinessSubscription
 */
public class BusinessSubscriptionEvent
{
    private static final String MISC = "MISC";

    public enum EventType
    {
        ADD,
        CANCEL,
        CHANGE,
        PAUSE,
        RESUME,
        SYSTEM_CANCEL,
        SYSTEM_CHANGE
    }

    private final EventType eventType;
    private final ProductCategory category;

    public static BusinessSubscriptionEvent valueOf(final String eventString)
    {
        for (final EventType possibleEventType : EventType.values()) {
            if (!eventString.startsWith(possibleEventType.toString().toUpperCase())) {
                continue;
            }

            final String categoryString = eventString.substring(possibleEventType.toString().length() + 1, eventString.length());

            if (categoryString.equals(MISC)) {
                return new BusinessSubscriptionEvent(possibleEventType, null);
            }
            else {
                return new BusinessSubscriptionEvent(possibleEventType, ProductCategory.valueOf(categoryString));
            }
        }

        throw new IllegalArgumentException("Unable to parse event string: " + eventString);
    }

    public BusinessSubscriptionEvent(final EventType eventType, final ProductCategory category)
    {
        this.eventType = eventType;
        this.category = category;
    }

    public ProductCategory getCategory()
    {
        return category;
    }

    public EventType getEventType()
    {
        return eventType;
    }

    public static BusinessSubscriptionEvent subscriptionCreated(final ISubscription subscription)
    {
        return eventFromType(EventType.ADD, subscription);
    }

    public static BusinessSubscriptionEvent subscriptionCancelled(final ISubscription subscription)
    {
        return eventFromType(EventType.CANCEL, subscription);
    }

    public static BusinessSubscriptionEvent subscriptionChanged(final ISubscription subscription)
    {
        return eventFromType(EventType.CHANGE, subscription);
    }

    public static BusinessSubscriptionEvent subscriptionPaused(final ISubscription subscription)
    {
        return eventFromType(EventType.PAUSE, subscription);
    }

    public static BusinessSubscriptionEvent subscriptionResumed(final ISubscription subscription)
    {
        return eventFromType(EventType.RESUME, subscription);
    }

    public static BusinessSubscriptionEvent subscriptionPhaseChanged(final ISubscription subscription)
    {
        if (subscription.getState() != null && subscription.getState().equals(ISubscription.SubscriptionState.CANCELLED)) {
            return eventFromType(EventType.SYSTEM_CANCEL, subscription);
        }
        else {
            return eventFromType(EventType.SYSTEM_CHANGE, subscription);
        }
    }

    private static BusinessSubscriptionEvent eventFromType(final EventType eventType, final ISubscription subscription)
    {
        final ProductCategory category = getTypeFromSubscription(subscription);
        return new BusinessSubscriptionEvent(eventType, category);
    }

    private static ProductCategory getTypeFromSubscription(final ISubscription subscription)
    {
        if (subscription == null) {
            return null;
        }

        final IPlan currentPlan = subscription.getCurrentPlan();
        if (currentPlan != null && currentPlan.getProduct() != null) {
            final IProduct product = currentPlan.getProduct();
            if (product.getType() != null && product.getCategory() != null) {
                return product.getCategory();
            }
        }

        return null;
    }

    @Override
    public String toString()
    {
        return eventType.toString() + "_" + (category == null ? MISC : category.toString().toUpperCase());
    }

    @Override
    public boolean equals(final Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final BusinessSubscriptionEvent that = (BusinessSubscriptionEvent) o;

        if (category != that.category) {
            return false;
        }
        if (eventType != null ? !eventType.equals(that.eventType) : that.eventType != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (category != null ? category.hashCode() : 0);
        return result;
    }
}