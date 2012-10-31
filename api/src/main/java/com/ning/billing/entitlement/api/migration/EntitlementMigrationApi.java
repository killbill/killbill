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

import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.util.callcontext.CallContext;

/**
 * The interface {@code EntitlementMigrationApi} is used to migrate entitlement data from third party system
 * in an atomic way.
 */
public interface EntitlementMigrationApi {


    /**
     * The interface {@code EntitlementAccountMigration} captures all the {@code SubscriptionBundle} associated with
     * that account.
     */
    public interface EntitlementAccountMigration {

        /**
         *
         * @return the unique id for the account
         */
        public UUID getAccountKey();

        /**
         *
         * @return an array of {@code EntitlementBundleMigration}
         */
        public EntitlementBundleMigration[] getBundles();
    }

    /**
     * The interface {@code EntitlementBundleMigration} captures all the {@code Subscription} asociated with a given
     * {@code SubscriptionBundle}
     */
    public interface EntitlementBundleMigration {

        /**
         *
         * @return the bundle external key
         */
        public String getBundleKey();

        /**
         *
         * @return an array of {@code Subscription}
         */
        public EntitlementSubscriptionMigration[] getSubscriptions();
    }

    /**
     * The interface {@code EntitlementSubscriptionMigration} captures the detail for each {@code Subscription} to be
     * migrated.
     */
    public interface EntitlementSubscriptionMigration {

        /**
         *
         * @return the {@code ProductCategory}
         */
        public ProductCategory getCategory();

        /**
         *
         * @return the chargeTroughDate for that {@code Subscription}
         */
        public DateTime getChargedThroughDate();

        /**
         *
         * @return the various phase information for that {@code Subscription}
         */
        public EntitlementSubscriptionMigrationCase[] getSubscriptionCases();
    }

    /**
     * The interface {@code EntitlementSubscriptionMigrationCase} captures the details of
     * phase for a {@code Subscription}.
     *
     */
    public interface EntitlementSubscriptionMigrationCase {
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
     * @param toBeMigrated all the bundles and associated subscription that should be migrated for the account
     * @throws EntitlementMigrationApiException
     *          an entitlement api exception
     */
    public void migrate(EntitlementAccountMigration toBeMigrated, CallContext context)
            throws EntitlementMigrationApiException;
}
