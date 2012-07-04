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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.testng.Assert;

import com.google.common.collect.ImmutableList;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionBinder;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionSqlDao;
import com.ning.billing.analytics.model.BusinessSubscriptionTransition;

public class MockBusinessSubscriptionTransitionSqlDao implements BusinessSubscriptionTransitionSqlDao {
    private final Map<String, List<BusinessSubscriptionTransition>> content = new HashMap<String, List<BusinessSubscriptionTransition>>();
    private final Map<String, String> keyForBundleId = new HashMap<String, String>();

    @Override
    public List<BusinessSubscriptionTransition> getTransitions(@Bind("event_key") final String key) {
        return content.get(key);
    }

    @Override
    public List<BusinessSubscriptionTransition> getTransitionForSubscription(@Bind("subscription_id") final String subscriptionId) {
        return ImmutableList.<BusinessSubscriptionTransition>of();
    }

    @Override
    public int createTransition(@BusinessSubscriptionTransitionBinder final BusinessSubscriptionTransition transition) {
        if (content.get(transition.getExternalKey()) == null) {
            content.put(transition.getExternalKey(), new ArrayList<BusinessSubscriptionTransition>());
        }
        content.get(transition.getExternalKey()).add(transition);
        keyForBundleId.put(transition.getBundleId().toString(), transition.getExternalKey());
        return 1;
    }

    @Override
    public void deleteTransitionsForBundle(@Bind("bundle_id") final String bundleId) {
        content.put(keyForBundleId.get(bundleId), new ArrayList<BusinessSubscriptionTransition>());
    }

    @Override
    public void test() {
    }

    @Override
    public void begin() {
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
    }

    @Override
    public void checkpoint(final String name) {
    }

    @Override
    public void release(final String name) {
    }

    @Override
    public void rollback(final String name) {
    }

    @Override
    public <ReturnType> ReturnType inTransaction(final Transaction<ReturnType, BusinessSubscriptionTransitionSqlDao> func) {
        try {
            return func.inTransaction(this, null);
        } catch (Exception e) {
            Assert.fail(e.toString());
            return null;
        }
    }
}
