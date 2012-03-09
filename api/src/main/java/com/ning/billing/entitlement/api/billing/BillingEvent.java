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

package com.ning.billing.entitlement.api.billing;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition.SubscriptionTransitionType;

public interface BillingEvent extends Comparable<BillingEvent> {

    /**
     *
     * @return the billCycleDay as seen for that subscription at that time
     *
     * Note: The billCycleDay may come from the Account, or the bundle or the subscription itself
     */
    public int getBillCycleDay();

    /**
     *
     * @return the subscription
     */
    public Subscription getSubscription();

    /**
     *
     * @return the date for when that event became effective
     */
    public DateTime getEffectiveDate();

    /**
     *
     * @return the plan phase
     */
    public PlanPhase getPlanPhase();


    /**
     *
     * @return the plan
     */
    public Plan getPlan();

    /**
     *
     * @return the billing period for the active phase
     */
    public BillingPeriod getBillingPeriod();

    /**
     *
     * @return the billing mode for the current event
     */
    public BillingModeType getBillingMode();

    /**
     *
     * @return the description of the billing event
     */
    public String getDescription();

    /**
     *
     * @return the fixed price for the phase
     */
    public InternationalPrice getFixedPrice();

    /**
     *
     * @return the recurring price for the phase
     */
    public InternationalPrice getRecurringPrice();

	/**
	 * @return the transition type of the underlying subscription event that triggered this
	 */
	public SubscriptionTransitionType getTransitionType();

	/**
	 * @return a unique long indicating the ordering on which events got inserted on disk-- used for sorting only
	 */
	public long getTotalOrdering();

}
