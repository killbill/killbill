/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.svcs;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.testng.Assert;
import org.testng.annotations.Test;

//
// Test all possible combinations of input effectiveDate and new BCD:
// (We call ComputedEffectiveDay the original day in the month extracted from the input effectiveDate)
//
// - effectiveDate = {null (present), past, future}
// - newBCD {< ComputedEffectiveDay, equals ComputedEffectiveDay, > ComputedEffectiveDay, > End Of Month}
//
//    => 12 possible tests
//
public class TestEffectiveDateForNewBCD extends SubscriptionTestSuiteNoDB {

    private final DateTime subscriptionStartDate = new DateTime("2010-05-07T00:03:42.000Z");

    @Test(groups = "fast")
    public void testNullEffectiveDateWithBCDPriorComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 3;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = null;

        // newBCD < ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-06-03")));
    }

    @Test(groups = "fast")
    public void testNullEffectiveDateWithBCDEqualsComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 7;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = null;

        // newBCD == ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        final DateTime nowAfterCall = clock.getUTCNow();

        // In that case because we want the event to fire right NOW, we don't use the account reference time but instead use the clock.getUTCNOW(). In case test
        // takes longer than 1 mSec we need to check a (very small) range of dates
        Assert.assertTrue(result.compareTo(now) >= 0);
        Assert.assertTrue(result.compareTo(nowAfterCall) <= 0);
    }

    @Test(groups = "fast")
    public void testNullEffectiveDateWithBCDAfterComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z => New time = 2012-06-07T00:03:42.000Z (and june only has 30 days)
        clock.addMonths(1);

        int newBCD = 31;
        // effectiveDate = 2012-06-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = null;

        // newBCD > 30 (max day in June)
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-06-30")));
    }

    @Test(groups = "fast")
    public void testNullEffectiveDateWithBCDAfterEndOfTheMonth() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 10;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = null;

        // newBCD < ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-05-10")));
    }

    @Test(groups = "fast")
    public void testFutureEffectiveDateWithBCDPriorComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 3;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 7, 7);

        // newBCD < ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-08-03")));
    }

    @Test(groups = "fast")
    public void testFutureEffectiveDateWithBCDEqualsComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 3;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 7, 3);

        // newBCD == ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-07-03")));
    }

    @Test(groups = "fast")
    public void testFutureEffectiveDateWithBCDAfterComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 10;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 7, 3);

        // newBCD > ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-07-10")));
    }

    @Test(groups = "fast")
    public void testFutureEffectiveDateWithBCDAfterEndOfTheMonth() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 31;
        // effectiveDate = 2012-06-03 => ComputedEffectiveDay = 3
        LocalDate effectiveDate = new LocalDate(2012, 6, 3);

        // newBCD > 30 (max day in June)
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-06-30")));
    }

    @Test(groups = "fast")
    public void testPastEffectiveDateWithBCDPriorComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 3;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 2, 7);

        // newBCD < ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-03-03")));
    }

    @Test(groups = "fast")
    public void testPastEffectiveDateWithBCDEqualsComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 3;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 2, 3);

        // newBCD == ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-02-03")));
    }

    @Test(groups = "fast")
    public void testPastEffectiveDateWithBCDAfterComputedEffectiveDay() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 10;
        // effectiveDate = 2012-05-07T00:03:42.000Z => ComputedEffectiveDay = 7
        LocalDate effectiveDate = new LocalDate(2012, 2, 3);

        // newBCD > ComputedEffectiveDay
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-02-10")));
    }

    @Test(groups = "fast")
    public void testPastEffectiveDateWithBCDAfterEndOfTheMonth() throws Exception {

        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();

        int newBCD = 31;
        // effectiveDate = 2012-02-03 => ComputedEffectiveDay = 3
        LocalDate effectiveDate = new LocalDate(2012, 2, 3);

        // newBCD > 30 (max day in June)
        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, subscriptionStartDate, internalCallContext);

        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2012-2-29")));
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/1158")
    public void testNullEffectiveDateWithBCDBeforeSubscriptionStartDate() throws Exception {
        // Set by test as : 2012-05-07T00:03:42.000Z
        final DateTime now = clock.getUTCNow();
        final DateTime startDate = now.plusYears(1);
        final int newBCD = 3;
        final LocalDate effectiveDate = null;

        final DateTime result = ((DefaultSubscriptionInternalApi) subscriptionInternalApi).getEffectiveDateForNewBCD(newBCD, effectiveDate, startDate, internalCallContext);
        Assert.assertTrue(result.compareTo(startDate) >= 0);
        Assert.assertEquals(result, internalCallContext.toUTCDateTime(new LocalDate("2013-06-03")));
    }
}
