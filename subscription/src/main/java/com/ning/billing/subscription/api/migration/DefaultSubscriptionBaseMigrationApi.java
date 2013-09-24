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

package com.ning.billing.subscription.api.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.subscription.alignment.MigrationPlanAligner;
import com.ning.billing.subscription.alignment.TimedMigration;
import com.ning.billing.subscription.api.SubscriptionApiBase;
import com.ning.billing.subscription.api.SubscriptionBaseApiService;
import com.ning.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.subscription.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBase;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBuilder;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.events.SubscriptionBaseEvent;
import com.ning.billing.subscription.events.SubscriptionBaseEvent.EventType;
import com.ning.billing.subscription.events.phase.PhaseEvent;
import com.ning.billing.subscription.events.phase.PhaseEventData;
import com.ning.billing.subscription.events.user.ApiEvent;
import com.ning.billing.subscription.events.user.ApiEventBuilder;
import com.ning.billing.subscription.events.user.ApiEventCancel;
import com.ning.billing.subscription.events.user.ApiEventChange;
import com.ning.billing.subscription.events.user.ApiEventMigrateBilling;
import com.ning.billing.subscription.events.user.ApiEventMigrateSubscription;
import com.ning.billing.subscription.events.user.ApiEventType;
import com.ning.billing.subscription.exceptions.SubscriptionBaseError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;

import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class DefaultSubscriptionBaseMigrationApi extends SubscriptionApiBase implements SubscriptionBaseMigrationApi {

    private final MigrationPlanAligner migrationAligner;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultSubscriptionBaseMigrationApi(final MigrationPlanAligner migrationAligner,
                                               final SubscriptionBaseApiService apiService,
                                               final CatalogService catalogService,
                                               final SubscriptionDao dao,
                                               final Clock clock,
                                               final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock, catalogService);
        this.migrationAligner = migrationAligner;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void migrate(final AccountMigration toBeMigrated, final CallContext context)
            throws SubscriptionBaseMigrationApiException {
        final AccountMigrationData accountMigrationData = createAccountMigrationData(toBeMigrated, context);
        dao.migrate(toBeMigrated.getAccountKey(), accountMigrationData, internalCallContextFactory.createInternalCallContext(toBeMigrated.getAccountKey(), context));
    }

    private AccountMigrationData createAccountMigrationData(final AccountMigration toBeMigrated, final CallContext context)
            throws SubscriptionBaseMigrationApiException {
        final UUID accountId = toBeMigrated.getAccountKey();
        final DateTime now = clock.getUTCNow();

        final List<BundleMigrationData> accountBundleData = new LinkedList<BundleMigrationData>();

        for (final BundleMigration curBundle : toBeMigrated.getBundles()) {

            final DefaultSubscriptionBaseBundle bundleData = new DefaultSubscriptionBaseBundle(curBundle.getBundleKey(), accountId, now, now, now, now);
            final List<SubscriptionMigrationData> bundleSubscriptionData = new LinkedList<AccountMigrationData.SubscriptionMigrationData>();

            final List<SubscriptionMigration> sortedSubscriptions = Lists.newArrayList(curBundle.getSubscriptions());
            // Make sure we have first BASE or STANDALONE, then ADDON and for each category order by CED
            Collections.sort(sortedSubscriptions, new Comparator<SubscriptionMigration>() {
                @Override
                public int compare(final SubscriptionMigration o1,
                                   final SubscriptionMigration o2) {
                    if (o1.getCategory().equals(o2.getCategory())) {
                        return o1.getSubscriptionCases()[0].getEffectiveDate().compareTo(o2.getSubscriptionCases()[0].getEffectiveDate());
                    } else {
                        if (!o1.getCategory().name().equalsIgnoreCase("ADD_ON")) {
                            return -1;
                        } else if (o1.getCategory().name().equalsIgnoreCase("ADD_ON")) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }
                }
            });

            DateTime bundleStartDate = null;
            for (final SubscriptionMigration curSub : sortedSubscriptions) {
                SubscriptionMigrationData data = null;
                if (bundleStartDate == null) {
                    data = createInitialSubscription(bundleData.getId(), curSub.getCategory(), curSub.getSubscriptionCases(), now, curSub.getChargedThroughDate(), context);
                    bundleStartDate = data.getInitialEvents().get(0).getEffectiveDate();
                } else {
                    data = createSubscriptionMigrationDataWithBundleDate(bundleData.getId(), curSub.getCategory(), curSub.getSubscriptionCases(), now,
                                                                         bundleStartDate, curSub.getChargedThroughDate(), context);
                }
                if (data != null) {
                    bundleSubscriptionData.add(data);
                }
            }
            final BundleMigrationData bundleMigrationData = new BundleMigrationData(bundleData, bundleSubscriptionData);
            accountBundleData.add(bundleMigrationData);
        }

        return new AccountMigrationData(accountBundleData);
    }

    private SubscriptionMigrationData createInitialSubscription(final UUID bundleId, final ProductCategory productCategory,
                                                                final SubscriptionMigrationCase[] input, final DateTime now, final DateTime ctd, final CallContext context)
            throws SubscriptionBaseMigrationApiException {
        final TimedMigration[] events = migrationAligner.getEventsMigration(input, now);
        final DateTime migrationStartDate = events[0].getEventTime();
        final List<SubscriptionBaseEvent> emptyEvents = Collections.emptyList();
        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionForApiUse(new SubscriptionBuilder()
                                                                                      .setId(UUID.randomUUID())
                                                                                      .setBundleId(bundleId)
                                                                                      .setCategory(productCategory)
                                                                                      .setBundleStartDate(migrationStartDate)
                                                                                      .setAlignStartDate(migrationStartDate),
                                                                              emptyEvents);
        return new SubscriptionMigrationData(defaultSubscriptionBase, toEvents(defaultSubscriptionBase, now, ctd, events, context), ctd);
    }

    private SubscriptionMigrationData createSubscriptionMigrationDataWithBundleDate(final UUID bundleId, final ProductCategory productCategory,
                                                                                    final SubscriptionMigrationCase[] input, final DateTime now, final DateTime bundleStartDate, final DateTime ctd, final CallContext context)
            throws SubscriptionBaseMigrationApiException {
        final TimedMigration[] events = migrationAligner.getEventsMigration(input, now);
        final DateTime migrationStartDate = events[0].getEventTime();
        final List<SubscriptionBaseEvent> emptyEvents = Collections.emptyList();
        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionForApiUse(new SubscriptionBuilder()
                                                                                      .setId(UUID.randomUUID())
                                                                                      .setBundleId(bundleId)
                                                                                      .setCategory(productCategory)
                                                                                      .setBundleStartDate(bundleStartDate)
                                                                                      .setAlignStartDate(migrationStartDate),
                                                                              emptyEvents);
        return new SubscriptionMigrationData(defaultSubscriptionBase, toEvents(defaultSubscriptionBase, now, ctd, events, context), ctd);
    }

    private List<SubscriptionBaseEvent> toEvents(final DefaultSubscriptionBase defaultSubscriptionBase, final DateTime now, final DateTime ctd, final TimedMigration[] migrationEvents, final CallContext context) {


        if (ctd == null) {
            throw new SubscriptionBaseError(String.format("Could not create migration billing event ctd = %s", ctd));
        }

        final List<SubscriptionBaseEvent> events = new ArrayList<SubscriptionBaseEvent>(migrationEvents.length);

        ApiEventMigrateBilling apiEventMigrateBilling = null;

        // The first event date after the MIGRATE_ENTITLEMENT event
        DateTime nextEventDate = null;

        boolean isCancelledSubscriptionPriorOrAtCTD = false;

        for (final TimedMigration cur : migrationEvents) {


            final ApiEventBuilder builder = new ApiEventBuilder()
                    .setSubscriptionId(defaultSubscriptionBase.getId())
                    .setEventPlan((cur.getPlan() != null) ? cur.getPlan().getName() : null)
                    .setEventPlanPhase((cur.getPhase() != null) ? cur.getPhase().getName() : null)
                    .setEventPriceList(cur.getPriceList())
                    .setActiveVersion(defaultSubscriptionBase.getActiveVersion())
                    .setEffectiveDate(cur.getEventTime())
                    .setProcessedDate(now)
                    .setRequestedDate(now)
                    .setFromDisk(true);


            if (cur.getEventType() == EventType.PHASE) {
                nextEventDate = nextEventDate != null && nextEventDate.compareTo(cur.getEventTime()) < 0 ? nextEventDate : cur.getEventTime();
                final PhaseEvent nextPhaseEvent = PhaseEventData.createNextPhaseEvent(cur.getPhase().getName(), defaultSubscriptionBase, now, cur.getEventTime());
                events.add(nextPhaseEvent);


            } else if (cur.getEventType() == EventType.API_USER) {

                switch (cur.getApiEventType()) {
                    case MIGRATE_ENTITLEMENT:
                        ApiEventMigrateSubscription creationEvent = new ApiEventMigrateSubscription(builder);
                        events.add(creationEvent);
                        break;

                    case CHANGE:
                        nextEventDate = nextEventDate != null && nextEventDate.compareTo(cur.getEventTime()) < 0 ? nextEventDate : cur.getEventTime();
                        events.add(new ApiEventChange(builder));
                        break;
                    case CANCEL:
                        isCancelledSubscriptionPriorOrAtCTD = !cur.getEventTime().isAfter(ctd);
                        nextEventDate = nextEventDate != null && nextEventDate.compareTo(cur.getEventTime()) < 0 ? nextEventDate : cur.getEventTime();
                        events.add(new ApiEventCancel(builder));
                        break;
                    default:
                        throw new SubscriptionBaseError(String.format("Unexpected type of api migration event %s", cur.getApiEventType()));
                }
            } else {
                throw new SubscriptionBaseError(String.format("Unexpected type of migration event %s", cur.getEventType()));
            }

            // create the MIGRATE_BILLING based on the current state of the last event.
            if (!cur.getEventTime().isAfter(ctd)) {
                builder.setEffectiveDate(ctd);
                builder.setUuid(UUID.randomUUID());
                apiEventMigrateBilling = new ApiEventMigrateBilling(builder);
            }
        }
        // Always ADD MIGRATE BILLING which is constructed from latest state seen in the stream prior to CTD
        if (apiEventMigrateBilling != null && !isCancelledSubscriptionPriorOrAtCTD) {
            events.add(apiEventMigrateBilling);
        }

        Collections.sort(events, new Comparator<SubscriptionBaseEvent>() {
            int compForApiType(final SubscriptionBaseEvent o1, final SubscriptionBaseEvent o2, final ApiEventType type) {
                ApiEventType apiO1 = null;
                if (o1.getType() == EventType.API_USER) {
                    apiO1 = ((ApiEvent) o1).getEventType();
                }
                ApiEventType apiO2 = null;
                if (o2.getType() == EventType.API_USER) {
                    apiO2 = ((ApiEvent) o2).getEventType();
                }
                if (apiO1 != null && apiO1.equals(type)) {
                    return -1;
                } else if (apiO2 != null && apiO2.equals(type)) {
                    return 1;
                } else {
                    return 0;
                }
            }

            @Override
            public int compare(final SubscriptionBaseEvent o1, final SubscriptionBaseEvent o2) {

                int comp = o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                if (comp == 0) {
                    comp = compForApiType(o1, o2, ApiEventType.MIGRATE_ENTITLEMENT);
                }
                if (comp == 0) {
                    comp = compForApiType(o1, o2, ApiEventType.MIGRATE_BILLING);
                }
                return comp;
            }
        });

        return events;
    }
}
