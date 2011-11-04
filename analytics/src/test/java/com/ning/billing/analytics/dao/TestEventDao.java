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

package com.ning.billing.analytics.dao;

import com.ning.billing.analytics.BusinessSubscription;
import com.ning.billing.analytics.BusinessSubscriptionEvent;
import com.ning.billing.analytics.BusinessSubscriptionTransition;
import com.ning.billing.analytics.MockDuration;
import com.ning.billing.analytics.MockPhase;
import com.ning.billing.analytics.MockPlan;
import com.ning.billing.analytics.MockProduct;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.user.ISubscription;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public class TestEventDao
{
    private static final String KEY = "12345";

    private final MysqlTestingHelper helper = new MysqlTestingHelper();

    private EventDao dao;
    private BusinessSubscriptionTransition transition;

    @BeforeClass(alwaysRun = true)
    public void startMysql() throws IOException, ClassNotFoundException, SQLException
    {
        final String ddl = IOUtils.toString(EventDao.class.getResourceAsStream("/com/ning/billing/analytics/ddl.sql"));

        helper.startMysql();
        helper.initDb(ddl);

        final IProduct product = new MockProduct("platinium", "subscription", ProductCategory.BASE);
        final IPlan plan = new MockPlan("platinum-monthly", product);
        final IPlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

        final BusinessSubscription prevSubscription = new BusinessSubscription(plan, phase, Currency.USD, new DateTime(DateTimeZone.UTC), ISubscription.SubscriptionState.ACTIVE, UUID.randomUUID(), UUID.randomUUID());
        final BusinessSubscription nextSubscription = new BusinessSubscription(plan, phase, Currency.USD, new DateTime(DateTimeZone.UTC), ISubscription.SubscriptionState.CANCELLED, UUID.randomUUID(), UUID.randomUUID());
        final BusinessSubscriptionEvent event = BusinessSubscriptionEvent.subscriptionCancelled(plan);
        final DateTime requestedTimestamp = new DateTime(DateTimeZone.UTC);

        transition = new BusinessSubscriptionTransition(KEY, requestedTimestamp, event, prevSubscription, nextSubscription);

        final IDBI dbi = helper.getDBI();
        dao = dbi.onDemand(EventDao.class);

        // Healthcheck test to make sure MySQL is setup properly
        try {
            dao.test();
        }
        catch (Throwable t) {
            Assert.fail(t.toString());
        }
    }

    @AfterClass(alwaysRun = true)
    public void stopMysql()
    {
        helper.stopMysql();
    }

    @Test(groups = "slow")
    public void testCreateAndRetrieve()
    {
        dao.createTransition(transition);

        final List<BusinessSubscriptionTransition> transitions = dao.getTransitions(KEY);
        Assert.assertEquals(transitions.size(), 1);
        Assert.assertEquals(transitions.get(0), transition);

        Assert.assertEquals(dao.getTransitions("Doesn't exist").size(), 0);
    }
}
