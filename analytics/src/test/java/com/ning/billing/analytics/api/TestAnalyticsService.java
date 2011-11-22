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

package com.ning.billing.analytics.api;

import com.google.inject.Inject;
import com.ning.billing.analytics.AnalyticsTestModule;
import com.ning.billing.analytics.BusinessSubscription;
import com.ning.billing.analytics.BusinessSubscriptionEvent;
import com.ning.billing.analytics.BusinessSubscriptionTransition;
import com.ning.billing.analytics.MockAccount;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockPlan;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.analytics.dao.BusinessSubscriptionTransitionDao;
import com.ning.billing.catalog.api.IPlan;
import com.ning.billing.catalog.api.IPlanPhase;
import com.ning.billing.catalog.api.IProduct;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.IEntitlementUserApi;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.ISubscriptionTransition;
import com.ning.billing.entitlement.api.user.SubscriptionTransition;
import com.ning.billing.entitlement.events.IEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.util.eventbus.IEventBus;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

@Guice(modules = AnalyticsTestModule.class)
public class TestAnalyticsService
{
    private static final String KEY = "1234";

    @Inject
    private IEntitlementUserApi entitlementApi;

    @Inject
    private AnalyticsService service;

    @Inject
    private IEventBus bus;

    @Inject
    private BusinessSubscriptionTransitionDao dao;

    @Inject
    private MysqlTestingHelper helper;

    private ISubscriptionTransition transition;
    private BusinessSubscriptionTransition expectedTransition;

    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException, ClassNotFoundException, SQLException, EntitlementUserApiException
    {
        final String analyticsDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));
        // For bundles
        final String accountDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/account/ddl.sql"));
        final String entitlementDdl = IOUtils.toString(BusinessSubscriptionTransitionDao.class.getResourceAsStream("/com/ning/billing/entitlement/ddl.sql"));

        helper.startMysql();
        helper.initDb(analyticsDdl);
        helper.initDb(accountDdl);
        helper.initDb(entitlementDdl);

        // We need a bundle to retrieve the event key
        final ISubscriptionBundle bundle = entitlementApi.createBundleForAccount(new MockAccount(KEY), KEY);

        // Verify we correctly initialized the account subsystem
        Assert.assertNotNull(bundle);
        Assert.assertEquals(bundle.getKey(), KEY);

        // Create a subscription transition
        final IProduct product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        final IPlan plan = new MockPlan("platinum-monthly", product);
        final IPlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effectiveTransitionTime = new DateTime(DateTimeZone.UTC);
        final DateTime requestedTransitionTime = new DateTime(DateTimeZone.UTC);
        transition = new SubscriptionTransition(
            subscriptionId,
            bundle.getId(),
            IEvent.EventType.API_USER,
            ApiEventType.CREATE,
            requestedTransitionTime,
            effectiveTransitionTime,
            null,
            null,
            null,
            null,
            ISubscription.SubscriptionState.ACTIVE,
            plan,
            phase,
            "something"
        );
        expectedTransition = new BusinessSubscriptionTransition(
            KEY,
            requestedTransitionTime,
            BusinessSubscriptionEvent.subscriptionCreated(plan),
            null,
            new BusinessSubscription(null, plan, phase, null, effectiveTransitionTime, ISubscription.SubscriptionState.ACTIVE, subscriptionId, bundle.getId())
        );
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test(groups = "slow")
    public void testRegisterForNotifications() throws Exception
    {
        // Make sure the service has been instantiated
        Assert.assertEquals(service.getName(), "analytics-service");

        // Test the bus and make sure we can register our service
        try {
            bus.start();
            service.registerForNotifications();
        }
        catch (Throwable t) {
            Assert.fail("Unable to start the bus or service! " + t);
        }

        // Send an event to the bus and make sure our Dao got it
        bus.post(transition);
        Thread.sleep(1000);
        Assert.assertEquals(dao.getTransitions(KEY).size(), 1);
        Assert.assertEquals(dao.getTransitions(KEY).get(0), expectedTransition);

        // Test the shutdown sequence
        try {
            bus.stop();
        }
        catch (Throwable t) {
            Assert.fail("Unable to stop the bus!");
        }
    }
}
