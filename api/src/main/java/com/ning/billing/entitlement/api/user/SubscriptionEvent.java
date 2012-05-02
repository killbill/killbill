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

package com.ning.billing.entitlement.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;

import com.ning.billing.entitlement.api.SubscriptionTransitionType;

import com.ning.billing.catalog.api.PriceList;

import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.util.bus.BusEvent;

public interface SubscriptionEvent extends BusEvent {

    UUID getId();

    SubscriptionTransitionType getTransitionType();

    UUID getBundleId();

    UUID getSubscriptionId();
    
    DateTime getSubscriptionStartDate();

    DateTime getRequestedTransitionTime();

    DateTime getEffectiveTransitionTime();

    SubscriptionState getPreviousState();

    String getPreviousPlan();

    String getPreviousPriceList();

    String getPreviousPhase();

    String getNextPlan();

    String getNextPhase();

    SubscriptionState getNextState();

    String getNextPriceList();
    
    Integer getRemainingEventsForUserOperation();
    
    Long getTotalOrdering();
}
