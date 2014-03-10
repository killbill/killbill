/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription.api.migration;

import java.util.List;

import org.joda.time.DateTime;

import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;


public class AccountMigrationData {

    private final List<BundleMigrationData> data;

    public AccountMigrationData(final List<BundleMigrationData> data) {
        super();
        this.data = data;
    }

    public List<BundleMigrationData> getData() {
        return data;
    }

    public static class BundleMigrationData {

        private final DefaultSubscriptionBaseBundle data;
        private final List<SubscriptionMigrationData> subscriptions;

        public BundleMigrationData(final DefaultSubscriptionBaseBundle data,
                                   final List<SubscriptionMigrationData> subscriptions) {
            super();
            this.data = data;
            this.subscriptions = subscriptions;
        }

        public DefaultSubscriptionBaseBundle getData() {
            return data;
        }

        public List<SubscriptionMigrationData> getSubscriptions() {
            return subscriptions;
        }
    }

    public static class SubscriptionMigrationData {

        private final DefaultSubscriptionBase data;
        private final List<SubscriptionBaseEvent> initialEvents;

        public SubscriptionMigrationData(final DefaultSubscriptionBase data,
                                         final List<SubscriptionBaseEvent> initialEvents,
                                         final DateTime ctd) {
            super();
            // Set CTD to subscription object from MIGRATION_BILLING event
            final SubscriptionBuilder builder = new SubscriptionBuilder(data);
            if (ctd != null) {
                builder.setChargedThroughDate(ctd);
            }
            this.data = new DefaultSubscriptionBase(builder);
            this.initialEvents = initialEvents;
        }

        public DefaultSubscriptionBase getData() {
            return data;
        }

        public List<SubscriptionBaseEvent> getInitialEvents() {
            return initialEvents;
        }
    }
}
