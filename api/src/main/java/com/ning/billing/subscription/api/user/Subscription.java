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

package com.ning.billing.subscription.api.user;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;

public interface Subscription extends Entity, Blockable {

    public boolean cancel(final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean cancelWithPolicy(final DateTime requestedDate, final ActionPolicy policy, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean uncancel(final CallContext context)
            throws SubscriptionUserApiException;

    public boolean changePlan(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate,
                                        final ActionPolicy policy, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public UUID getBundleId();

    public SubscriptionState getState();

    public SubscriptionSourceType getSourceType();

    public DateTime getStartDate();

    public DateTime getEndDate();

    public DateTime getFutureEndDate();

    public Plan getCurrentPlan();

    public Plan getLastActivePlan();

    public PriceList getCurrentPriceList();

    public PlanPhase getCurrentPhase();

    public String getLastActiveProductName();

    public String getLastActivePriceListName();

    public String getLastActiveCategoryName();

    public String getLastActiveBillingPeriod();

    public DateTime getChargedThroughDate();

    public DateTime getPaidThroughDate();

    public ProductCategory getCategory();

    public SubscriptionTransition getPendingTransition();

    public SubscriptionTransition getPreviousTransition();

    public List<SubscriptionTransition> getAllTransitions();
}
