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

package org.killbill.billing.subscription.api.user;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

public interface SubscriptionBaseTransition {

    public UUID getId();

    public UUID getSubscriptionId();

    public UUID getBundleId();

    public EntitlementState getPreviousState();

    public EntitlementState getNextState();

    public UUID getPreviousEventId();

    public DateTime getPreviousEventCreatedDate();

    public Plan getPreviousPlan();

    public Plan getNextPlan();

    public PlanPhase getPreviousPhase();

    public UUID getNextEventId();

    public DateTime getNextEventCreatedDate();

    public PlanPhase getNextPhase();

    public PriceList getPreviousPriceList();

    public PriceList getNextPriceList();

    public DateTime getEffectiveTransitionTime();

    public SubscriptionBaseTransitionType getTransitionType();

    public Integer getPreviousBillingCycleDayLocal();

    public Integer getNextBillingCycleDayLocal();

    public DateTime getCreatedDate();
}
