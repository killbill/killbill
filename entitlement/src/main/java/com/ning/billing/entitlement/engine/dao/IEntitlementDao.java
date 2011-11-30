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

package com.ning.billing.entitlement.engine.dao;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.events.IEvent;

public interface IEntitlementDao {


    // Bundle apis
    public List<ISubscriptionBundle> getSubscriptionBundleForAccount(UUID accountId);

    public ISubscriptionBundle getSubscriptionBundleFromId(UUID bundleId);

    public ISubscriptionBundle createSubscriptionBundle(SubscriptionBundle bundle);

    public ISubscription getSubscriptionFromId(UUID subscriptionId);


    // Subscription retrieval
    public ISubscription getBaseSubscription(UUID bundleId);

    public List<ISubscription> getSubscriptions(UUID bundleId);

    public List<ISubscription> getSubscriptionsForKey(String bundleKey);

    // Update
    public void updateSubscription(Subscription subscription);

    // Event apis
    public void createNextPhaseEvent(UUID subscriptionId, IEvent nextPhase);

    public List<IEvent> getEventsForSubscription(UUID subscriptionId);

    public List<IEvent> getPendingEventsForSubscription(UUID subscriptionId);

    public List<IEvent> getEventsReady(UUID ownerId, int sequenceId);

    public void clearEventsReady(UUID ownerId, Collection<IEvent> cleared);

    // Subscription creation, cancellation, changePlan apis
    public ISubscription createSubscription(Subscription subscription, List<IEvent> initialEvents);

    public void cancelSubscription(UUID subscriptionId, IEvent cancelEvent);

    public void uncancelSubscription(UUID subscriptionId, List<IEvent> uncancelEvents);

    public void changePlan(UUID subscriptionId, List<IEvent> changeEvents);
}
