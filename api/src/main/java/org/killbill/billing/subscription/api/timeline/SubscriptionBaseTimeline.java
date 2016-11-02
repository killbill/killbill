/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.subscription.api.timeline;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.util.entity.Entity;

/**
 * The interface {@code} shows a view of all the events for a particular {@code SubscriptionBase}.
 * <p/>
 */
public interface SubscriptionBaseTimeline extends Entity {


    /**
     * @return the current list of events for that {@code SubscriptionBase}
     */
    public List<ExistingEvent> getExistingEvents();


    public interface ExistingEvent {

        /**
         * @return the unique if for the event to delete
         */
        public UUID getEventId();

        /**
         *
         * @return the description for the event to be added
         */
        public PlanPhaseSpecifier getPlanPhaseSpecifier();


        /**
         * @return the {@code SubscriptionBaseTransitionType} for the event
         */
        public SubscriptionBaseTransitionType getSubscriptionTransitionType();

        /**
         *
         * @return the product category
         */
        public ProductCategory getProductCategory();

        /**
         * @return the date at which this event was effective
         */
        public DateTime getEffectiveDate();

        /**
         * @return the name of the plan
         */
        public String getPlanName();

        /**
         * @return the name of the phase
         */
        public String getPlanPhaseName();

        /**
         *
         * @return the new billCycleDayLocal
         */
        public Integer getBillCycleDayLocal();
    }
}
