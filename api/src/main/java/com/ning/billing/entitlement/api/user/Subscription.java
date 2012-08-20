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
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;

public interface Subscription extends Entity, Blockable {

    public enum SubscriptionState {
        ACTIVE,
        CANCELLED
    }

    public enum SubscriptionSourceType {
        NATIVE,
        MIGRATED,
        TRANSFERED
    }

    public boolean cancel(DateTime requestedDate, boolean eot, CallContext context)
            throws EntitlementUserApiException;

    public boolean uncancel(CallContext context)
            throws EntitlementUserApiException;

    public boolean changePlan(String productName, BillingPeriod term, String priceList, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException;

    public boolean changePlanWithPolicy(String productName, BillingPeriod term, String priceList, DateTime requestedDate,
                                        ActionPolicy policy, CallContext context)
            throws EntitlementUserApiException;

    public boolean recreate(PlanPhaseSpecifier spec, DateTime requestedDate, CallContext context)
            throws EntitlementUserApiException;

    public UUID getBundleId();

    public SubscriptionState getState();

    public SubscriptionSourceType getSourceType();

    public DateTime getStartDate();

    public DateTime getEndDate();

    public DateTime getFutureEndDate();

    public Plan getCurrentPlan();

    public PriceList getCurrentPriceList();

    public PlanPhase getCurrentPhase();

    public String getLastActiveProductName();

    public String getLastActivePriceListName();

    public String getLastActiveCategoryName();

    public String getLastActiveBillingPeriod();

    public DateTime getChargedThroughDate();

    public DateTime getPaidThroughDate();

    public ProductCategory getCategory();

    public EffectiveSubscriptionEvent getPendingTransition();

    public EffectiveSubscriptionEvent getPreviousTransition();

    public List<EffectiveSubscriptionEvent> getBillingTransitions();

    public List<EffectiveSubscriptionEvent> getAllTransitions();

}
