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

package com.ning.billing.osgi.bundles.analytics.dao.model;

import javax.annotation.Nullable;

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;

/**
 * Describe an event associated with a transition between two BusinessSubscription
 */
public class BusinessSubscriptionEvent {

    private static final String MISC = "MISC";

    public enum EventType {
        MIGRATE,
        ADD,
        CANCEL,
        UN_CANCEL,
        RE_ADD,
        TRANSFER,
        CHANGE,
        SYSTEM_CHANGE
    }

    private final EventType eventType;
    private final ProductCategory category;

    public static BusinessSubscriptionEvent valueOf(final String eventString) {
        for (final EventType possibleEventType : EventType.values()) {
            if (!eventString.startsWith(possibleEventType.toString().toUpperCase())) {
                continue;
            }

            final String categoryString = eventString.substring(possibleEventType.toString().length() + 1, eventString.length());

            if (categoryString.equals(MISC)) {
                return new BusinessSubscriptionEvent(possibleEventType, null);
            } else {
                return new BusinessSubscriptionEvent(possibleEventType, ProductCategory.valueOf(categoryString));
            }
        }

        throw new IllegalArgumentException("Unable to parse event string: " + eventString);
    }

    private BusinessSubscriptionEvent(final EventType eventType, final ProductCategory category) {
        this.eventType = eventType;
        this.category = category;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public EventType getEventType() {
        return eventType;
    }

    public static BusinessSubscriptionEvent fromTransition(final SubscriptionTransition transition) {
        switch (transition.getTransitionType()) {
            // A subscription enters either through migration or as newly created subscription
            case MIGRATE_ENTITLEMENT:
                return subscriptionMigrated(transition.getNextPlan());
            case CREATE:
                return subscriptionCreated(transition.getNextPlan());
            case RE_CREATE:
                return subscriptionRecreated(transition.getNextPlan());
            case TRANSFER:
                return subscriptionTransfered(transition.getNextPlan());
            case CANCEL:
                // Need to take the previous plan (the next one is null)
                return subscriptionCancelled(transition.getPreviousPlan());
            case UNCANCEL:
                return subscriptionUnCancelled(transition.getNextPlan());
            case CHANGE:
                return subscriptionChanged(transition.getNextPlan());
            case PHASE:
                return subscriptionPhaseChanged(transition.getNextPlan());
            case MIGRATE_BILLING: // Has no impact on the subscription
            case START_BILLING_DISABLED: // See BOS
            case END_BILLING_DISABLED: // See BOS
            default:
                return null;
        }
    }

    private static BusinessSubscriptionEvent subscriptionMigrated(final Plan plan) {
        return eventFromType(EventType.MIGRATE, plan);
    }

    private static BusinessSubscriptionEvent subscriptionCreated(final Plan plan) {
        return eventFromType(EventType.ADD, plan);
    }

    private static BusinessSubscriptionEvent subscriptionCancelled(final Plan plan) {
        return eventFromType(EventType.CANCEL, plan);
    }

    private static BusinessSubscriptionEvent subscriptionUnCancelled(final Plan plan) {
        return eventFromType(EventType.UN_CANCEL, plan);
    }

    private static BusinessSubscriptionEvent subscriptionChanged(final Plan plan) {
        return eventFromType(EventType.CHANGE, plan);
    }

    private static BusinessSubscriptionEvent subscriptionRecreated(final Plan plan) {
        return eventFromType(EventType.RE_ADD, plan);
    }

    private static BusinessSubscriptionEvent subscriptionTransfered(final Plan plan) {
        return eventFromType(EventType.TRANSFER, plan);
    }

    private static BusinessSubscriptionEvent subscriptionPhaseChanged(final Plan plan) {
        return eventFromType(EventType.SYSTEM_CHANGE, plan);
    }

    private static BusinessSubscriptionEvent eventFromType(final EventType eventType, final Plan plan) {
        final ProductCategory category = getTypeFromSubscription(plan);
        return new BusinessSubscriptionEvent(eventType, category);
    }

    private static ProductCategory getTypeFromSubscription(@Nullable final Plan plan) {
        if (plan != null && plan.getProduct() != null) {
            final Product product = plan.getProduct();
            if (product.getCatalogName() != null && product.getCategory() != null) {
                return product.getCategory();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return eventType.toString() + "_" + (category == null ? MISC : category.toString().toUpperCase());
    }

    @Override
    public boolean equals(final Object o) {
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
    public int hashCode() {
        int result = eventType != null ? eventType.hashCode() : 0;
        result = 31 * result + (category != null ? category.hashCode() : 0);
        return result;
    }
}
