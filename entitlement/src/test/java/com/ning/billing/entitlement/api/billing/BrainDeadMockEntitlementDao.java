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
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;

class BrainDeadMockEntitlementDao implements EntitlementDao {

	@Override
	public List<SubscriptionBundle> getSubscriptionBundleForAccount(
			final UUID accountId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubscriptionBundle createSubscriptionBundle(
			final SubscriptionBundleData bundle) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Subscription getSubscriptionFromId(final UUID subscriptionId) {
		throw new UnsupportedOperationException();

	}

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        throw new UnsupportedOperationException();
    }

    @Override
	public Subscription getBaseSubscription(final UUID bundleId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Subscription> getSubscriptions(final UUID bundleId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Subscription> getSubscriptionsForKey(final String bundleKey) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateSubscription(final SubscriptionData subscription) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void createNextPhaseEvent(final UUID subscriptionId,
			final EntitlementEvent nextPhase) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EntitlementEvent> getEventsForSubscription(
			final UUID subscriptionId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<EntitlementEvent> getPendingEventsForSubscription(
			final UUID subscriptionId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void createSubscription(final SubscriptionData subscription,
			final List<EntitlementEvent> initialEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void cancelSubscription(final UUID subscriptionId,
			final EntitlementEvent cancelEvent) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void uncancelSubscription(final UUID subscriptionId,
			final List<EntitlementEvent> uncancelEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void changePlan(final UUID subscriptionId,
			final List<EntitlementEvent> changeEvents) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void migrate(final UUID acountId, final AccountMigrationData data) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void undoMigration(final UUID accountId) {
        throw new UnsupportedOperationException();
	}


    @Override
    public EntitlementEvent getEventById(final UUID eventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recreateSubscription(UUID subscriptionId,
            List<EntitlementEvent> recreateEvents) {
    }
}