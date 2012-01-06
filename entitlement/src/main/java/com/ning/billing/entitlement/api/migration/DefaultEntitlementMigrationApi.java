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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.inject.Inject;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Duration;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.alignment.MigrationPlanAligner;
import com.ning.billing.entitlement.alignment.TimedMigration;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventMigrate;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class DefaultEntitlementMigrationApi implements EntitlementMigrationApi {


    private final EntitlementDao dao;
    private final MigrationPlanAligner migrationAligner;
    private final SubscriptionFactory factory;
    private final CatalogService catalogService;
    private final Clock clock;

    @Inject
    public DefaultEntitlementMigrationApi(MigrationPlanAligner migrationAligner,
            SubscriptionFactory factory,
            CatalogService catalogService,
            EntitlementDao dao,
            Clock clock) {
        this.dao = dao;
        this.migrationAligner = migrationAligner;
        this.factory = factory;
        this.catalogService = catalogService;
        this.clock = clock;
    }

    @Override
    public void migrate(EntitlementAccountMigration toBeMigrated)
    throws EntitlementMigrationApiException {
        AccountMigrationData accountMigrationData = createAccountMigrationData(toBeMigrated);
        dao.migrate(toBeMigrated.getAccountKey(), accountMigrationData);
    }

    @Override
    public void undoMigration(UUID accountId) {
        dao.undoMigration(accountId);
    }

    private AccountMigrationData createAccountMigrationData(EntitlementAccountMigration toBeMigrated)
    throws EntitlementMigrationApiException  {

        final UUID accountId = toBeMigrated.getAccountKey();
        final DateTime now = clock.getUTCNow();

        List<BundleMigrationData> accountBundleData = new LinkedList<BundleMigrationData>();

        for (final EntitlementBundleMigration curBundle : toBeMigrated.getBundles()) {

            SubscriptionBundleData bundleData = new SubscriptionBundleData(curBundle.getBundleKey(), accountId);
            List<SubscriptionMigrationData> bundleSubscriptionData = new LinkedList<AccountMigrationData.SubscriptionMigrationData>();

            for (EntitlementSubscriptionMigration curSub : curBundle.getSubscriptions()) {
                SubscriptionMigrationData data = null;
                switch (curSub.getCategory()) {
                case BASE:
                    data = createBaseSubscriptionMigrationData(bundleData.getId(), curSub.getCategory(), curSub.getSubscriptionCases(), now);
                    break;
                case ADD_ON:
                    // Not implemented yet
                    break;
                case STANDALONE:
                    // Not implemented yet
                    break;
                default:
                    throw new EntitlementMigrationApiException(String.format("Unkown product type ", curSub.getCategory()));
                }
                if (data != null) {
                    bundleSubscriptionData.add(data);
                }
            }
            BundleMigrationData bundleMigrationData = new BundleMigrationData(bundleData, bundleSubscriptionData);
            accountBundleData.add(bundleMigrationData);
        }
        AccountMigrationData accountMigrationData = new AccountMigrationData(accountBundleData);
        return accountMigrationData;
    }

    private SubscriptionMigrationData createBaseSubscriptionMigrationData(UUID bundleId, ProductCategory productCategory,
            EntitlementSubscriptionMigrationCase [] input, DateTime now)
        throws EntitlementMigrationApiException {

        TimedMigration [] events = migrationAligner.getEventsMigration(input, now);
        DateTime migrationStartDate= events[0].getEventTime();
        List<EntitlementEvent> emptyEvents =  Collections.emptyList();
        SubscriptionData subscriptionData = factory.createSubscription(new SubscriptionBuilder()
            .setId(UUID.randomUUID())
            .setBundleId(bundleId)
            .setCategory(productCategory)
            .setBundleStartDate(migrationStartDate)
            .setStartDate(migrationStartDate),
            emptyEvents);
        return new SubscriptionMigrationData(subscriptionData, toEvents(subscriptionData, now, events));
    }

    private List<EntitlementEvent> toEvents(SubscriptionData subscriptionData, DateTime now, TimedMigration [] migrationEvents) {

        List<EntitlementEvent> events = new ArrayList<EntitlementEvent>(migrationEvents.length);
        for (TimedMigration cur : migrationEvents) {

            if (cur.getEventType() == EventType.PHASE) {
                PhaseEvent nextPhaseEvent = PhaseEventData.getNextPhaseEvent(cur.getPhase().getName(), subscriptionData, now, cur.getEventTime());
                events.add(nextPhaseEvent);

            } else if (cur.getEventType() == EventType.API_USER) {

                ApiEventBuilder builder = new ApiEventBuilder()
                .setSubscriptionId(subscriptionData.getId())
                .setEventPlan((cur.getPlan() != null) ? cur.getPlan().getName() : null)
                .setEventPlanPhase((cur.getPhase() != null) ? cur.getPhase().getName() : null)
                .setEventPriceList(cur.getPriceList())
                .setActiveVersion(subscriptionData.getActiveVersion())
                .setEffectiveDate(cur.getEventTime())
                .setProcessedDate(now)
                .setRequestedDate(now);

                switch(cur.getApiEventType()) {
                case MIGRATE_ENTITLEMENT:
                    events.add(new ApiEventMigrate(builder));
                    break;

                case CHANGE:
                    events.add(new ApiEventChange(builder));
                    break;
                case CANCEL:
                    events.add(new ApiEventCancel(builder));
                    break;
                default:
                    throw new EntitlementError(String.format("Unexpected type of api migration event %s", cur.getApiEventType()));
                }
            } else {
                throw new EntitlementError(String.format("Unexpected type of migration event %s", cur.getEventType()));
            }
        }
        return events;
    }
}
