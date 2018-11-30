/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing;

import org.joda.time.LocalDate;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestClockMockSetup extends UtilTestSuiteNoDB {

    @Test(groups = "fast", description = "Verify Mockito setup in GuicyKillbillTestSuite")
    public void testSpy() {
        clock.setDay(new LocalDate("2012-08-01"));
        Assert.assertEquals(clock.getUTCToday().toString(), "2012-08-01");
        Assert.assertEquals(theRealClock.getUTCToday().toString(), "2012-08-01");
        clock.addMonths(1);
        Assert.assertEquals(clock.getUTCToday().toString(), "2012-09-01");
        Assert.assertEquals(theRealClock.getUTCToday().toString(), "2012-09-01");
        clock.addDays(30);
        Assert.assertEquals(clock.getUTCToday().toString(), "2012-10-01");
        Assert.assertEquals(theRealClock.getUTCToday().toString(), "2012-10-01");
    }
}
