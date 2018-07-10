/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.entitlement;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;

// Wrapper object to save on DAO calls
public interface AccountEventsStreams {

    public ImmutableAccountData getAccount();

    // Map bundle id -> bundle
    public Map<UUID, SubscriptionBaseBundle> getBundles();

    // Map bundle id -> subscriptions
    public Map<UUID, Collection<SubscriptionBase>> getSubscriptions();

    // Map bundle id -> events streams
    public Map<UUID, Collection<EventsStream>> getEventsStreams();
}
