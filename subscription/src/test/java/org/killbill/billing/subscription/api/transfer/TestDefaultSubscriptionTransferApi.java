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

package org.killbill.billing.subscription.api.transfer;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.MockCatalogService;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.ExistingEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.user.ApiEventTransfer;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;

import com.google.common.collect.ImmutableList;

// Simple unit tests for DefaultSubscriptionBaseTransferApi, see TestTransfer for more advanced tests with dao
public class TestDefaultSubscriptionTransferApi extends SubscriptionTestSuiteNoDB {

    private DefaultSubscriptionBaseTransferApi transferApi;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final NonEntityDao nonEntityDao = Mockito.mock(NonEntityDao.class);
        final SubscriptionDao dao = Mockito.mock(SubscriptionDao.class);
        final CatalogService catalogService = new MockCatalogService(new MockCatalog());
        final SubscriptionBaseApiService apiService = Mockito.mock(SubscriptionBaseApiService.class);
        final SubscriptionBaseTimelineApi timelineApi = Mockito.mock(SubscriptionBaseTimelineApi.class);
        final InternalCallContextFactory internalCallContextFactory = new InternalCallContextFactory(clock, nonEntityDao, new CacheControllerDispatcher());
        transferApi = new DefaultSubscriptionBaseTransferApi(clock, dao, timelineApi, catalogService, apiService, internalCallContextFactory);
    }

    @Test(groups = "fast")
    public void testEventsForCancelledSubscriptionBeforeTransfer() throws Exception {
        final DateTime subscriptionStartTime = clock.getUTCNow();
        final DateTime subscriptionCancelTime = subscriptionStartTime.plusDays(1);
        final ImmutableList<ExistingEvent> existingEvents = ImmutableList.<ExistingEvent>of(createEvent(subscriptionStartTime, SubscriptionBaseTransitionType.CREATE),
                                                                                            createEvent(subscriptionCancelTime, SubscriptionBaseTransitionType.CANCEL));
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(subscriptionBuilder);

        final DateTime transferDate = subscriptionStartTime.plusDays(10);
        final List<SubscriptionBaseEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, internalCallContext);

        Assert.assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testEventsForCancelledSubscriptionAfterTransfer() throws Exception {
        final DateTime subscriptionStartTime = clock.getUTCNow();
        final DateTime subscriptionCancelTime = subscriptionStartTime.plusDays(1);
        final ImmutableList<ExistingEvent> existingEvents = ImmutableList.<ExistingEvent>of(createEvent(subscriptionStartTime, SubscriptionBaseTransitionType.CREATE),
                                                                                            createEvent(subscriptionCancelTime, SubscriptionBaseTransitionType.CANCEL));
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(subscriptionBuilder);

        final DateTime transferDate = subscriptionStartTime.plusHours(1);
        final List<SubscriptionBaseEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, internalCallContext);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle1() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (both in the past)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateSubscriptionEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateSubscriptionEventEffectiveDate.plusDays(1);
        final List<SubscriptionBaseEvent> events = transferBundle(migrateSubscriptionEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle2() throws Exception {
        // MIGRATE_ENTITLEMENT and MIGRATE_BILLING at the same time (both in the past)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateSubscriptionEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateSubscriptionEventEffectiveDate;
        final List<SubscriptionBaseEvent> events = transferBundle(migrateSubscriptionEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle3() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (the latter in the future)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateSubscriptionEventEffectiveDate = transferDate.minusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateSubscriptionEventEffectiveDate.plusDays(20);
        final List<SubscriptionBaseEvent> events = transferBundle(migrateSubscriptionEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    @Test(groups = "fast")
    public void testEventsAfterTransferForMigratedBundle4() throws Exception {
        // MIGRATE_ENTITLEMENT then MIGRATE_BILLING (both in the future)
        final DateTime transferDate = clock.getUTCNow();
        final DateTime migrateSubscriptionEventEffectiveDate = transferDate.plusDays(10);
        final DateTime migrateBillingEventEffectiveDate = migrateSubscriptionEventEffectiveDate.plusDays(20);
        final List<SubscriptionBaseEvent> events = transferBundle(migrateSubscriptionEventEffectiveDate, migrateBillingEventEffectiveDate, transferDate);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), migrateSubscriptionEventEffectiveDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getEventType(), ApiEventType.TRANSFER);
    }

    private List<SubscriptionBaseEvent> transferBundle(final DateTime migrateSubscriptionEventEffectiveDate, final DateTime migrateBillingEventEffectiveDate,
                                                       final DateTime transferDate) throws SubscriptionBaseTransferApiException {
        final ImmutableList<ExistingEvent> existingEvents = createMigrateEvents(migrateSubscriptionEventEffectiveDate, migrateBillingEventEffectiveDate);
        final SubscriptionBuilder subscriptionBuilder = new SubscriptionBuilder();
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(subscriptionBuilder);

        return transferApi.toEvents(existingEvents, subscription, transferDate, internalCallContext);
    }

    private ExistingEvent createEvent(final DateTime eventEffectiveDate, final SubscriptionBaseTransitionType subscriptionTransitionType) {
        return new ExistingEvent() {
            @Override
            public DateTime getEffectiveDate() {
                return eventEffectiveDate;
            }

            @Override
            public String getPlanPhaseName() {
                return SubscriptionBaseTransitionType.CANCEL.equals(subscriptionTransitionType) ? null : "BicycleTrialEvergreen1USD-trial";
            }

            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return SubscriptionBaseTransitionType.CANCEL.equals(subscriptionTransitionType) ? null :
                       new PlanPhaseSpecifier("BicycleTrialEvergreen1USD", ProductCategory.BASE, BillingPeriod.NO_BILLING_PERIOD,
                                              PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.FIXEDTERM);
            }

            @Override
            public DateTime getRequestedDate() {
                return getEffectiveDate();
            }

            @Override
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
                return subscriptionTransitionType;
            }
        };
    }

    private ImmutableList<ExistingEvent> createMigrateEvents(final DateTime migrateSubscriptionEventEffectiveDate, final DateTime migrateBillingEventEffectiveDate) {
        final ExistingEvent migrateEntitlementEvent = new ExistingEvent() {
            @Override
            public DateTime getEffectiveDate() {
                return migrateSubscriptionEventEffectiveDate;
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
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
                return SubscriptionBaseTransitionType.MIGRATE_ENTITLEMENT;
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
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
                return SubscriptionBaseTransitionType.MIGRATE_BILLING;
            }
        };

        return ImmutableList.<ExistingEvent>of(migrateEntitlementEvent, migrateBillingEvent);
    }
}
