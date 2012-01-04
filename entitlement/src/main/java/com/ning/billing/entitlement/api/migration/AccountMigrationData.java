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

package com.ning.billing.entitlement.api.migration;

import java.util.List;

import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.events.EntitlementEvent;

public class AccountMigrationData {

    private final List<BundleMigrationData> data;

    public AccountMigrationData(List<BundleMigrationData> data) {
        super();
        this.data = data;
    }

    public List<BundleMigrationData> getData() {
        return data;
    }

    public static class BundleMigrationData {

        private final SubscriptionBundleData data;
        private final List<SubscriptionMigrationData> subscriptions;

        public BundleMigrationData(SubscriptionBundleData data,
                List<SubscriptionMigrationData> subscriptions) {
            super();
            this.data = data;
            this.subscriptions = subscriptions;
        }

        public SubscriptionBundleData getData() {
            return data;
        }

        public List<SubscriptionMigrationData> getSubscriptions() {
            return subscriptions;
        }
    }

    public static class SubscriptionMigrationData {

        private final SubscriptionData data;
        private final List<EntitlementEvent> initialEvents;

        public SubscriptionMigrationData(SubscriptionData data,

                List<EntitlementEvent> initialEvents) {
            super();
            this.data = data;
            this.initialEvents = initialEvents;
        }

        public SubscriptionData getData() {
            return data;
        }

        public List<EntitlementEvent> getInitialEvents() {
            return initialEvents;
        }
    }
}
