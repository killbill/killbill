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

package org.killbill.billing.entitlement.dao;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.entitlement.EntitlementTestSuiteNoDB;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestProxyBlockingStateDao extends EntitlementTestSuiteNoDB {

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/174")
    public void testComparisonSameEffectiveDate() throws Exception {
        final UUID blockedId = UUID.randomUUID();
        final BlockingStateType blockingStateType = BlockingStateType.ACCOUNT;
        final String service = "test";
        final DateTime effectiveDate = clock.getUTCNow();

        final BlockingState bs1 = new DefaultBlockingState(UUID.randomUUID(),
                                                           blockedId,
                                                           blockingStateType,
                                                           "OD1",
                                                           service,
                                                           false,
                                                           false,
                                                           false,
                                                           effectiveDate.minusDays(10),
                                                           effectiveDate,
                                                           effectiveDate,
                                                           1L);
        final BlockingState bs2 = new DefaultBlockingState(UUID.randomUUID(),
                                                           blockedId,
                                                           blockingStateType,
                                                           "OD2",
                                                           service,
                                                           true,
                                                           true,
                                                           true,
                                                           effectiveDate.minusDays(5),
                                                           effectiveDate,
                                                           effectiveDate,
                                                           2L);
        final BlockingState bs3 = new DefaultBlockingState(UUID.randomUUID(),
                                                           blockedId,
                                                           blockingStateType,
                                                           "OD3",
                                                           service,
                                                           true,
                                                           true,
                                                           true,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           3L);
        final BlockingState bs4 = new DefaultBlockingState(UUID.randomUUID(),
                                                           blockedId,
                                                           blockingStateType,
                                                           "OD4",
                                                           service,
                                                           false,
                                                           false,
                                                           false,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           effectiveDate,
                                                           4L);

        verifySortedCopy(bs1, bs2, bs3, bs4, bs1, bs2, bs3, bs4);
        verifySortedCopy(bs1, bs2, bs3, bs4, bs1, bs3, bs2, bs4);
        verifySortedCopy(bs1, bs2, bs3, bs4, bs2, bs3, bs1, bs4);
        verifySortedCopy(bs1, bs2, bs3, bs4, bs2, bs1, bs3, bs4);
        verifySortedCopy(bs1, bs2, bs3, bs4, bs3, bs1, bs2, bs4);
        verifySortedCopy(bs1, bs2, bs3, bs4, bs3, bs2, bs1, bs4);
    }

    private void verifySortedCopy(final BlockingState bs1, final BlockingState bs2, final BlockingState bs3, final BlockingState bs4,
                                  final BlockingState a, final BlockingState b, final BlockingState c, final BlockingState d) {
        final List<BlockingState> sortedCopy = ProxyBlockingStateDao.sortedCopy(ImmutableList.<BlockingState>of(a, b, c, d));
        Assert.assertEquals(sortedCopy.get(0).getStateName(), bs1.getStateName());
        Assert.assertEquals(sortedCopy.get(1).getStateName(), bs2.getStateName());
        Assert.assertEquals(sortedCopy.get(2).getStateName(), bs3.getStateName());
        Assert.assertEquals(sortedCopy.get(3).getStateName(), bs4.getStateName());
    }
}
