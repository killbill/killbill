/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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
import org.killbill.billing.catalog.DefaultVersionedCatalog;
import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.MockCatalogService;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.DefaultCatalogInternalApi;
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
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

// Simple unit tests for DefaultSubscriptionBaseTransferApi, see TestTransfer for more advanced tests with dao
public class TestDefaultSubscriptionTransferApi extends SubscriptionTestSuiteNoDB {

    private DefaultSubscriptionBaseTransferApi transferApi;

    @Override
    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final SubscriptionDao dao = Mockito.mock(SubscriptionDao.class);
        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog();
        final MockCatalog mockCatalog = new MockCatalog();
        versionedCatalog.add(mockCatalog);
        final CatalogService catalogService = new MockCatalogService(versionedCatalog, cacheControllerDispatcher);
        final CatalogInternalApi catalogInternalApiWithMockCatalogService = new DefaultCatalogInternalApi(catalogService);
        final SubscriptionBaseApiService apiService = Mockito.mock(SubscriptionBaseApiService.class);
        final SubscriptionBaseTimelineApi timelineApi = Mockito.mock(SubscriptionBaseTimelineApi.class);
        transferApi = new DefaultSubscriptionBaseTransferApi(clock, dao, timelineApi, catalogInternalApiWithMockCatalogService, subscriptionInternalApi, apiService, internalCallContextFactory);
        // Overrride catalog with our MockCatalog
        this.catalog = mockCatalog;
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
        final List<SubscriptionBaseEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, catalog, internalCallContext);

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
        final List<SubscriptionBaseEvent> events = transferApi.toEvents(existingEvents, subscription, transferDate, catalog, internalCallContext);

        Assert.assertEquals(events.size(), 1);
        Assert.assertEquals(events.get(0).getType(), EventType.API_USER);
        Assert.assertEquals(events.get(0).getEffectiveDate(), transferDate);
        Assert.assertEquals(((ApiEventTransfer) events.get(0)).getApiEventType(), ApiEventType.TRANSFER);
    }

    private ExistingEvent createEvent(final DateTime eventEffectiveDate, final SubscriptionBaseTransitionType subscriptionTransitionType) {
        return new ExistingEvent() {
            @Override
            public DateTime getEffectiveDate() {
                return eventEffectiveDate;
            }

            @Override
            public String getPlanName() {
                return "1-BicycleTrialEvergreen1USD";
            }

            @Override
            public String getPlanPhaseName() {
                return SubscriptionBaseTransitionType.CANCEL.equals(subscriptionTransitionType) ? null : "1-BicycleTrialEvergreen1USD-trial";
            }

            @Override
            public Integer getBillCycleDayLocal() {
                return null;
            }

            @Override
            public UUID getEventId() {
                return UUID.randomUUID();
            }

            @Override
            public PlanPhaseSpecifier getPlanPhaseSpecifier() {
                return SubscriptionBaseTransitionType.CANCEL.equals(subscriptionTransitionType) ? null :
                       new PlanPhaseSpecifier("1-BicycleTrialEvergreen1USD",  BillingPeriod.NO_BILLING_PERIOD,
                                              PriceListSet.DEFAULT_PRICELIST_NAME, PhaseType.FIXEDTERM);
            }

            @Override
            public SubscriptionBaseTransitionType getSubscriptionTransitionType() {
                return subscriptionTransitionType;
            }

            @Override
            public ProductCategory getProductCategory() {
                return ProductCategory.BASE;
            }
        };
    }
}
