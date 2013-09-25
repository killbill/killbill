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

package com.ning.billing.subscription.api;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBuilder;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.callcontext.InternalCallContext;

public interface SubscriptionBaseApiService {

    public DefaultSubscriptionBase createPlan(SubscriptionBuilder builder, Plan plan, PhaseType initialPhase,
                                              String realPriceList, DateTime requestedDate, DateTime effectiveDate, DateTime processedDate,
                                              CallContext context)
            throws SubscriptionBaseApiException;

    @Deprecated
    public boolean recreatePlan(final DefaultSubscriptionBase subscription, final PlanPhaseSpecifier spec, final DateTime requestedDateWithMs, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithRequestedDate(DefaultSubscriptionBase subscription, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(DefaultSubscriptionBase subscription, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(DefaultSubscriptionBase subscription, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean changePlan(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                              String priceList, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean changePlanWithRequestedDate(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                                               String priceList, DateTime requestedDate, CallContext context)
            throws SubscriptionBaseApiException;

    public boolean changePlanWithPolicy(DefaultSubscriptionBase subscription, String productName, BillingPeriod term,
                                        String priceList, BillingActionPolicy policy, CallContext context)
            throws SubscriptionBaseApiException;

    public int cancelAddOnsIfRequired(final DefaultSubscriptionBase baseSubscription, final DateTime effectiveDate, final InternalCallContext context);
}
