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

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.util.callcontext.CallContext;

/**
 * The interface {@code SubscriptionBaseMigrationApi} is used to migrate subscription data from third party system
 * in an atomic way.
 */
public interface SubscriptionBaseMigrationApi {


    /**
     * The interface {@code AccountMigration} captures all the {@code SubscriptionBaseBundle} associated with
     * that account.
     */
    public interface AccountMigration {

        /**
         *
         * @return the unique id for the account
         */
        public UUID getAccountKey();

        /**
         *
         * @return an array of {@code BundleMigration}
         */
        public BundleMigration[] getBundles();
    }

    /**
     * The interface {@code BundleMigration} captures all the {@code SubscriptionBase} asociated with a given
     * {@code SubscriptionBaseBundle}
     */
    public interface BundleMigration {

        /**
         *
         * @return the bundle external key
         */
        public String getBundleKey();

        /**
         *
         * @return an array of {@code SubscriptionBase}
         */
        public SubscriptionMigration[] getSubscriptions();
    }

    /**
     * The interface {@code SubscriptionMigration} captures the detail for each {@code SubscriptionBase} to be
     * migrated.
     */
    public interface SubscriptionMigration {

        /**
         *
         * @return the {@code ProductCategory}
         */
        public ProductCategory getCategory();

        /**
         *
         * @return the chargeTroughDate for that {@code SubscriptionBase}
         */
        public DateTime getChargedThroughDate();

        /**
         *
         * @return the various phase information for that {@code SubscriptionBase}
         */
        public SubscriptionMigrationCase[] getSubscriptionCases();
    }

    /**
     * The interface {@code SubscriptionMigrationCase} captures the details of
     * phase for a {@code SubscriptionBase}.
     *
     */
    public interface SubscriptionMigrationCase {
        /**
         *
         * @return the {@code PlanPhaseSpecifier}
         */
        public PlanPhaseSpecifier getPlanPhaseSpecifier();

        /**
         *
         * @return the date at which this phase starts.
         */
        public DateTime getEffectiveDate();

        /**
         *
         * @return the date at which this phase is stopped.
         */
        public DateTime getCancelledDate();
    }


    /**
     * Migrate all the existing entitlements associated with that account.
     * The semantics is 'all or nothing' (atomic operation)
     *
     * @param toBeMigrated all the bundles and associated SubscriptionBase that should be migrated for the account
     * @throws SubscriptionBaseMigrationApiException
     *          an subscription api exception
     */
    public void migrate(AccountMigration toBeMigrated, CallContext context)
            throws SubscriptionBaseMigrationApiException;
}
