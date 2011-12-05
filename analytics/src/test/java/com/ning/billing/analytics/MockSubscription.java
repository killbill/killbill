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

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.List;
import java.util.UUID;

public class MockSubscription implements Subscription
{
    private static final UUID ID = UUID.randomUUID();
    private static final UUID BUNDLE_ID = UUID.randomUUID();
    private static final DateTime START_DATE = new DateTime(DateTimeZone.UTC);

    private final SubscriptionState state;
    private final IPlan plan;
    private final IPlanPhase phase;

    public MockSubscription(final SubscriptionState state, final IPlan plan, final IPlanPhase phase)
    {
        this.state = state;
        this.plan = plan;
        this.phase = phase;
    }

    @Override
    public void cancel(DateTime requestedDate, boolean eot)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void changePlan(final String productName, final BillingPeriod term, final String planSet, DateTime requestedDate)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void pause()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void resume()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public UUID getId()
    {
        return ID;
    }

    @Override
    public UUID getBundleId()
    {
        return BUNDLE_ID;
    }

    @Override
    public SubscriptionState getState()
    {
        return state;
    }

    @Override
    public DateTime getStartDate()
    {
        return START_DATE;
    }

    @Override
    public IPlan getCurrentPlan()
    {
        return plan;
    }

    @Override
    public IPlanPhase getCurrentPhase()
    {
        return phase;
    }


    @Override
    public void uncancel() throws EntitlementUserApiException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentPriceList()
    {
        return null;
    }

    @Override
    public DateTime getEndDate() {
        return null;
    }

    @Override
    public List<SubscriptionTransition> getActiveTransitions() {
        throw new UnsupportedOperationException();
    }
}
