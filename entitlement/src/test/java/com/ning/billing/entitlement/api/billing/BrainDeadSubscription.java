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

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;

public class BrainDeadSubscription implements Subscription {

	@Override
	public void cancel(DateTime requestedDate, boolean eot)
			throws EntitlementUserApiException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void uncancel() throws EntitlementUserApiException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void changePlan(String productName, BillingPeriod term,
			String planSet, DateTime requestedDate)
			throws EntitlementUserApiException {
		throw new UnsupportedOperationException();


	}

	@Override
	public void pause() throws EntitlementUserApiException {
		throw new UnsupportedOperationException();


	}

	@Override
	public void resume() throws EntitlementUserApiException {
		throw new UnsupportedOperationException();

	}

	@Override
	public UUID getId() {
		throw new UnsupportedOperationException();

	}

	@Override
	public UUID getBundleId() {
		throw new UnsupportedOperationException();

	}

	@Override
	public SubscriptionState getState() {
		throw new UnsupportedOperationException();

	}

	@Override
	public DateTime getStartDate() {
		throw new UnsupportedOperationException();

	}

	@Override
	public DateTime getEndDate() {
		throw new UnsupportedOperationException();

	}

	@Override
	public Plan getCurrentPlan() {
		throw new UnsupportedOperationException();

	}

	@Override
	public String getCurrentPriceList() {
		throw new UnsupportedOperationException();

	}

	@Override
	public PlanPhase getCurrentPhase() {
		throw new UnsupportedOperationException();

	}

	@Override
	public DateTime getChargedThroughDate() {
		throw new UnsupportedOperationException();

	}

	@Override
	public DateTime getPaidThroughDate() {
		throw new UnsupportedOperationException();

	}

	@Override
	public List<SubscriptionTransition> getActiveTransitions() {
		throw new UnsupportedOperationException();

	}

	@Override
	public List<SubscriptionTransition> getAllTransitions() {
		throw new UnsupportedOperationException();

	}

	@Override
	public SubscriptionTransition getPendingTransition() {
		throw new UnsupportedOperationException();

	}

    @Override
    public SubscriptionTransition getPreviousTransition() {
        return null;
    }

}
