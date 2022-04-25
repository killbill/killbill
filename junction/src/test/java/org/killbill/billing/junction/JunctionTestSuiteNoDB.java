/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.junction;

import java.math.BigDecimal;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.catalog.DefaultPrice;
import org.killbill.billing.catalog.MockInternationalPrice;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.junction.glue.TestJunctionModuleNoDB;
import org.killbill.billing.junction.plumbing.billing.BlockingCalculator;
import org.killbill.billing.junction.plumbing.billing.DefaultBillingEvent;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBillingEvent;
import org.killbill.billing.subscription.api.user.SubscriptionBillingEvent;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.bus.api.PersistentBus;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class JunctionTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BillingInternalApi billingInternalApi;
    @Inject
    protected BlockingCalculator blockingCalculator;
    @Inject
    protected CatalogService catalogService;
    @Inject
    protected CatalogInternalApi catalogInternalApi;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagInternalApi tagInternalApi;
    @Inject
    protected BlockingStateDao blockingStateDao;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestJunctionModuleNoDB(configSource, clock));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        bus.startQueue();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        if (hasFailed()) {
            return;
        }

        bus.stopQueue();
    }


    protected BillingEvent createEvent(final SubscriptionBase sub, final DateTime effectiveDate, final SubscriptionBaseTransitionType type) throws CatalogApiException {
        return createEvent(sub, effectiveDate, type, 1L);
    }

    protected BillingEvent createEvent(final SubscriptionBase sub, final DateTime effectiveDate, final SubscriptionBaseTransitionType type, final long totalOrdering) throws CatalogApiException {
        final int billCycleDay = 1;

        final Plan shotgun = new MockPlan();
        final PlanPhase shotgunMonthly = createMockMonthlyPlanPhase(null, BigDecimal.ZERO, PhaseType.TRIAL);

        final SubscriptionBillingEvent subscriptionBillingEvent = new DefaultSubscriptionBillingEvent(type,
                                                                                                      shotgun,
                                                                                                      shotgunMonthly,
                                                                                                      effectiveDate,
                                                                                                      totalOrdering,
                                                                                                      billCycleDay,
                                                                                                      effectiveDate);
        return new DefaultBillingEvent(subscriptionBillingEvent,
                                       sub,
                                       billCycleDay,
                                       null,
                                       Currency.USD);
    }

    protected MockPlanPhase createMockMonthlyPlanPhase(@Nullable final BigDecimal recurringRate,
                                                       @Nullable final BigDecimal fixedRate,
                                                       final PhaseType phaseType) {
        return new MockPlanPhase(recurringRate == null ? null : new MockInternationalPrice(new DefaultPrice(recurringRate, Currency.USD)),
                                 fixedRate == null ? null : new MockInternationalPrice(new DefaultPrice(fixedRate, Currency.USD)),
                                 BillingPeriod.MONTHLY, phaseType);
    }

    protected SubscriptionBase subscription(final UUID id) {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(id);
        return subscription;
    }

}
