/*
 * Copyright 2016 Groupon, Inc
 * Copyright 2016 The Billing Project, LLC
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

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestDefaultBlockingState extends JunctionTestSuiteNoDB {

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/174")
    public void testComparisonSameEffectiveDate() throws Exception {
        final DateTime effectiveDate = clock.getUTCNow();
        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(),
                                                           UUID.randomUUID(),
                                                           BlockingStateType.ACCOUNT,
                                                           "OD3",
                                                           "test",
                                                           true,
                                                           true,
                                                           true,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           3L);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(),
                                                           UUID.randomUUID(),
                                                           BlockingStateType.ACCOUNT,
                                                           "OD2",
                                                           "test",
                                                           false,
                                                           false,
                                                           false,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           4L);
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(),
                                                           UUID.randomUUID(),
                                                           BlockingStateType.ACCOUNT,
                                                           "OD1",
                                                           "test",
                                                           true,
                                                           true,
                                                           true,
                                                           effectiveDate.plusMillis(1),
                                                           effectiveDate,
                                                           effectiveDate,
                                                           5L);
        Assert.assertTrue(bs1.compareTo(bs2) < 0);
        Assert.assertTrue(bs1.compareTo(bs3) < 0);
        Assert.assertTrue(bs2.compareTo(bs1) > 0);
        Assert.assertTrue(bs2.compareTo(bs3) < 0);
        Assert.assertTrue(bs3.compareTo(bs2) > 0);
        Assert.assertTrue(bs3.compareTo(bs1) > 0);
    }
}
