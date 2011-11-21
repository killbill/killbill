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

import com.ning.billing.account.api.IAccount;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MockIEntitlementUserApi implements IEntitlementUserApi
{
    private final Map<UUID, String> subscriptionBundles = new HashMap<UUID, String>();

    public MockIEntitlementUserApi(final UUID bundleUUID, final String key)
    {
        subscriptionBundles.put(bundleUUID, key);
    }

    @Override
    public ISubscriptionBundle getBundleFromId(final UUID id)
    {
        final String key = subscriptionBundles.get(id);
        if (key == null) {
            return null;
        }

        return new ISubscriptionBundle()
        {
            @Override
            public UUID getAccountId()
            {
                return UUID.randomUUID();
            }

            @Override
            public UUID getId()
            {
                return id;
            }

            @Override
            public DateTime getStartDate()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getKey()
            {
                return key;
            }

            @Override
            public void setPrivate(final String name, final String value)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getPrivate(final String name)
            {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public ISubscription getSubscriptionFromId(final UUID id)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ISubscriptionBundle> getBundlesForAccount(final UUID accountId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ISubscription> getSubscriptionsForBundle(final UUID bundleId)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISubscriptionBundle createBundleForAccount(final IAccount account, final String bundleKey) throws EntitlementUserApiException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ISubscription createSubscription(final UUID bundleId, final String productName, final BillingPeriod term, final String planSet, final DateTime requestedDate) throws EntitlementUserApiException
    {
        throw new UnsupportedOperationException();
    }
}
