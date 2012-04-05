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

public interface EntitlementMigrationApi {


    public interface EntitlementAccountMigration {
        public UUID getAccountKey();
        public EntitlementBundleMigration [] getBundles();
    }

    public interface EntitlementBundleMigration {
        public String getBundleKey();
        public EntitlementSubscriptionMigration [] getSubscriptions();
    }

    public interface EntitlementSubscriptionMigration {
        public ProductCategory getCategory();
        public DateTime getChargedThroughDate();
        public EntitlementSubscriptionMigrationCase [] getSubscriptionCases();
    }

    /**
     *
     * Each case is either a PHASE or a different PlanSpecifier
     */
    public interface EntitlementSubscriptionMigrationCase {
        public PlanPhaseSpecifier getPlanPhaseSpecifier();
        public DateTime getEffectiveDate();
        public DateTime getCancelledDate();
    }


    /**
     * Migrate all the existing entitlements associated with that account.
     * The semantics is 'all or nothing' (atomic operation)
     *
     * @param toBeMigrated all the bundles and associated subscription that should be migrated for the account
     * @throws EntitlementMigrationApiException an entitlement api exception
     *
     */
    public void migrate(EntitlementAccountMigration toBeMigrated)
        throws EntitlementMigrationApiException;
}
