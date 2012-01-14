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

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;

class BrainDeadMockEntitlementDao implements EntitlementDao {

	@Override
	public List<SubscriptionBundle> getSubscriptionBundleForAccount(
			UUID accountId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle getSubscriptionBundleFromKey(String bundleKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle createSubscriptionBundle(
			SubscriptionBundleData bundle) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Subscription getSubscriptionFromId(UUID subscriptionId) {
		throw new UnsupportedOperationException();

	}

	@Override
	public Subscription getBaseSubscription(UUID bundleId) {
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Subscription> getSubscriptions(UUID bundleId) {
		throw new UnsupportedOperationException();

	}

	@Override
	public List<Subscription> getSubscriptionsForKey(String bundleKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateSubscription(SubscriptionData subscription) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void createNextPhaseEvent(UUID subscriptionId,
			EntitlementEvent nextPhase) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EntitlementEvent> getEventsForSubscription(
			UUID subscriptionId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EntitlementEvent> getPendingEventsForSubscription(
			UUID subscriptionId) {
		throw new UnsupportedOperationException();
	}


	@Override
	public void createSubscription(SubscriptionData subscription,
			List<EntitlementEvent> initialEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void cancelSubscription(UUID subscriptionId,
			EntitlementEvent cancelEvent) {
		throw new UnsupportedOperationException();

	}

	@Override
	public void uncancelSubscription(UUID subscriptionId,
			List<EntitlementEvent> uncancelEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void changePlan(UUID subscriptionId,
			List<EntitlementEvent> changeEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void migrate(UUID acountId, AccountMigrationData data) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void undoMigration(UUID accountId) {
        throw new UnsupportedOperationException();
	}

    @Override
    public EntitlementEvent getEventById(UUID eventId) {
        throw new UnsupportedOperationException();
    }
}