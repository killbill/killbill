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

package org.killbill.billing.entitlement.api.svcs;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.entitlement.AccountEventsStreams;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DefaultAccountEventsStreams implements AccountEventsStreams {

    private final ImmutableAccountData account;
    private final Map<UUID, Collection<EventsStream>> eventsStreams;
    private final Map<UUID, Collection<SubscriptionBase>> subscriptionsPerBundle;
    private final Map<UUID, SubscriptionBaseBundle> bundles = new HashMap<UUID, SubscriptionBaseBundle>();

    public DefaultAccountEventsStreams(final ImmutableAccountData account,
                                       final Iterable<SubscriptionBaseBundle> bundles,
                                       final Map<UUID, Collection<SubscriptionBase>> subscriptionsPerBundle,
                                       final Map<UUID, Collection<EventsStream>> eventsStreams) {
        this.account = account;
        this.subscriptionsPerBundle = subscriptionsPerBundle;
        this.eventsStreams = eventsStreams;
        for (final SubscriptionBaseBundle baseBundle : bundles) {
            this.bundles.put(baseBundle.getId(), baseBundle);
        }
    }

    public DefaultAccountEventsStreams(final ImmutableAccountData account) {
        this(account, ImmutableList.<SubscriptionBaseBundle>of(), ImmutableMap.<UUID, Collection<SubscriptionBase>>of(), ImmutableMap.<UUID, Collection<EventsStream>>of());
    }

    @Override
    public ImmutableAccountData getAccount() {
        return account;
    }

    @Override
    public Map<UUID, SubscriptionBaseBundle> getBundles() {
        return bundles;
    }

    @Override
    public Map<UUID, Collection<SubscriptionBase>> getSubscriptions() {
        return subscriptionsPerBundle;
    }

    @Override
    public Map<UUID, Collection<EventsStream>> getEventsStreams() {
        return eventsStreams;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultAccountEventsStreams{");
        sb.append("account=").append(account);
        sb.append(", eventsStreams=").append(eventsStreams);
        sb.append(", subscriptionsPerBundle=").append(subscriptionsPerBundle);
        sb.append(", bundles=").append(bundles);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultAccountEventsStreams that = (DefaultAccountEventsStreams) o;

        if (account != null ? !account.equals(that.account) : that.account != null) {
            return false;
        }
        if (bundles != null ? !bundles.equals(that.bundles) : that.bundles != null) {
            return false;
        }
        if (subscriptionsPerBundle != null ? !subscriptionsPerBundle.equals(that.subscriptionsPerBundle) : that.subscriptionsPerBundle != null) {
            return false;
        }
        if (eventsStreams != null ? !eventsStreams.equals(that.eventsStreams) : that.eventsStreams != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = account != null ? account.hashCode() : 0;
        result = 31 * result + (eventsStreams != null ? eventsStreams.hashCode() : 0);
        result = 31 * result + (subscriptionsPerBundle != null ? subscriptionsPerBundle.hashCode() : 0);
        result = 31 * result + (bundles != null ? bundles.hashCode() : 0);
        return result;
    }
}
