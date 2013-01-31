/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.entitlement.api.transfer;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.MockCatalogService;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventTransfer;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableList;

// Simple unit tests for DefaultEntitlementTransferApi, see TestTransfer for more advanced tests with dao
public class TestDefaultEntitlementTransferApi extends EntitlementTestSuiteNoDB {

    private DefaultEntitlementTransferApi transferApi;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
        final EntitlementDao dao = Mockito.mock(EntitlementDao.class);
        final CatalogService catalogService = new MockCatalogService(new MockCatalog());
        final SubscriptionApiService apiService = Mockito.mock(SubscriptionApiService.class);
        final EntitlementTimelineApi timelineApi = Mockito.mock(EntitlementTimelineApi.class);
        final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(clock, nonEntityDao, new CacheControllerDispatcher());
        transferApi = new DefaultEntitlementTransferApi(clock, dao, timelineApi, catalogService, apiService, internalCallContextFactory);
    }

    @Test(groups = "fast")
    public void testEventsForCancelledSubscriptionBeforeTransfer() throws Exception {
        final DateTime subscriptionStartTime = clock.getUTCNow();
        final DateTime subscriptionCancelTime = subscriptionStartTime.plusDays(1);
        final ImmutableList<ExistingEvent> existingEvents = ImmutableList.<ExistingEvent>of(createEvent(subscriptionStartTime, SubscriptionTransitionType.CREATE),
                                                                                            createEvent(subscriptionCancelTime, SubscriptionTransitionType.CANCEL));
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final SubscriptionData subscription = new SubscriptionData(subscriptionBuilder);

        final DateTime transferDate = subscriptionStartTime.plusDays(10);
        final List<EntitlementEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, callContext);

        Assert.assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testEventsForCancelledSubscriptionAfterTransfer() throws Exception {
        final DateTime subscriptionStartTime = clock.getUTCNow();
        final DateTime subscriptionCancelTime = subscriptionStartTime.plusDays(1);
        final ImmutableList<ExistingEvent> existingEvents = ImmutableList.<ExistingEvent>of(createEvent(subscriptionStartTime, SubscriptionTransitionType.CREATE),
                                                                                            createEvent(subscriptionCancelTime, SubscriptionTransitionType.CANCEL));
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final SubscriptionData subscription = new SubscriptionData(subscriptionBuilder);

        final DateTime transferDate = subscriptionStartTime.plusHours(1);
        final List<EntitlementEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, callContext);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle1() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (both in the past)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateEntitlementEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateEntitlementEventEffectiveDate.plusDays(1);
        final List<EntitlementEvent> events = transferBundle(migrateEntitlementEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle2() throws Exception {
        // MIGRATE_ENTITLEMENT and MIGRATE_BILLING at the same time (both in the past)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateEntitlementEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateEntitlementEventEffectiveDate;
        final List<EntitlementEvent> events = transferBundle(migrateEntitlementEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle3() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (the latter in the future)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateEntitlementEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateEntitlementEventEffectiveDate.plusDays(20);
        final List<EntitlementEvent> events = transferBundle(migrateEntitlementEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle4() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (both in the future)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateEntitlementEventEffectiveDate = transferDate.plusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateEntitlementEventEffectiveDate.plusDays(20);
        final List<EntitlementEvent> events = transferBundle(migrateEntitlementEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), migrateEntitlementEventEffectiveDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    private List<EntitlementEvent> transferBundle(final DateTime migrateEntitlementEventEffectiveDate, final DateTime migrateBillingEventEffectiveDate,
                                                  final DateTime transferDate) throws EntitlementTransferApiException {
        final ImmutableList<ExistingEvent> existingEvents = createMigrateEvents(migrateEntitlementEventEffectiveDate, migrateBillingEventEffectiveDate);
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final SubscriptionData subscription = new SubscriptionData(subscriptionBuilder);

        return transferApi.toEvents(existingEvents, subscription, transferDate, callContext);
    }

    private ExistingEvent createEvent(final DateTime eventEffectiveDate, final SubscriptionTransitionType subscriptionTransitionType) {
        return new ExistingEvent() {
            @Override
            public DateTime getEffectiveDate() {
                return eventEffectiveDate;
            }

            @Override
            public String getPlanPhaseName() {
                return SubscriptionTransitionType.CANCEL.equals(subscriptionTransitionType) ? null : "BicycleTrialEvergreen1USD-trial";
            }

            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return SubscriptionTransitionType.CANCEL.equals(subscriptionTransitionType) ? null :
                       new PlanPhaseSpecifier("BicycleTrialEvergreen1USD", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD,
                                              PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.FIXEDTERM);
            }

            @Override
            public DateTime getRequestedDate() {
                return getEffectiveDate();
            }

            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return subscriptionTransitionType;
            }
        };
    }

    private ImmutableList<ExistingEvent> createMigrateEvents(final DateTime migrateEntitlementEventEffectiveDate, final DateTime migrateBillingEventEffectiveDate) {
        final ExistingEvent migrateEntitlementEvent = new ExistingEvent() {
            @Override
            public DateTime getEffectiveDate() {
                return migrateEntitlementEventEffectiveDate;
            }

            @Override
            public String getPlanPhaseName() {
                return "BicycleTrialEvergreen1USD-trial";
            }

            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return new PlanPhaseSpecifier("BicycleTrialEvergreen1USD", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD,
                                              PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.FIXEDTERM);
            }

            @Override
            public DateTime getRequestedDate() {
                return getEffectiveDate();
            }

            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return SubscriptionTransitionType.MIGRATE_ENTITLEMENT;
            }
        };

        final ExistingEvent migrateBillingEvent = new ExistingEvent() {

            @Override
            public DateTime getEffectiveDate() {
                return migrateBillingEventEffectiveDate;
            }

            @Override
            public String getPlanPhaseName() {
                return migrateEntitlementEvent.getPlanPhaseName();
            }

            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return migrateEntitlementEvent.getPlanPhaseSpecifier();
            }

            @Override
            public DateTime getRequestedDate() {
                return migrateEntitlementEvent.getRequestedDate();
            }

            @Override
            public SubscriptionTransitionType getSubscriptionTransitionType() {
                return SubscriptionTransitionType.MIGRATE_BILLING;
            }
        };

        return ImmutableList.<ExistingEvent>of(migrateEntitlementEvent, migrateBillingEvent);
    }
}
