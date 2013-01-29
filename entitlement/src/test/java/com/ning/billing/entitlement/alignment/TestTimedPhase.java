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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuite;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;

public class TestTimedPhase extends EntitlementTestSuiteNoDB {
    @Test(groups = "fast")
    public void testConstructor() throws Exception {
        final PlanPhase planPhase = Mockito.mock(PlanPhase.class);
        final DateTime startPhase = new DateTime(DateTimeZone.UTC);
        final TimedPhase timedPhase = new TimedPhase(planPhase, startPhase);
        final TimedPhase otherTimedPhase = new TimedPhase(planPhase, startPhase);

        Assert.assertEquals(otherTimedPhase, timedPhase);
        Assert.assertEquals(timedPhase.getPhase(), planPhase);
        Assert.assertEquals(timedPhase.getStartPhase(), startPhase);
    }
}
