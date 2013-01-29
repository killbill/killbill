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

package com.ning.billing.entitlement.alignment;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuite;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;

public class TestTimedMigration extends EntitlementTestSuiteNoDB {
    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final DateTime eventTime = new DateTime(DateTimeZone.UTC);
        final EntitlementEvent.EventType eventType = EntitlementEvent.EventType.API_USER;
        final ApiEventType apiEventType = ApiEventType.CREATE;
        final Plan plan = Mockito.mock(Plan.class);
        final PlanPhase phase = Mockito.mock(PlanPhase.class);
        final String priceList = UUID.randomUUID().toString();
        final TimedMigration timedMigration = new TimedMigration(eventTime, eventType, apiEventType, plan, phase, priceList);
        final TimedMigration otherTimedMigration = new TimedMigration(eventTime, eventType, apiEventType, plan, phase, priceList);

        Assert.assertEquals(otherTimedMigration, timedMigration);
        Assert.assertEquals(timedMigration.getEventTime(), eventTime);
        Assert.assertEquals(timedMigration.getEventType(), eventType);
        Assert.assertEquals(timedMigration.getApiEventType(), apiEventType);
        Assert.assertEquals(timedMigration.getPlan(), plan);
        Assert.assertEquals(timedMigration.getPhase(), phase);
        Assert.assertEquals(timedMigration.getPriceList(), priceList);
    }
}
