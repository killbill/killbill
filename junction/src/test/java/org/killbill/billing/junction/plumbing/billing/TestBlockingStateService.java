/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.junction.plumbing.billing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestBlockingStateService extends JunctionTestSuiteNoDB {


    private UUID accountId;
    private UUID bundleId;
    private UUID subscriptionId;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
       super.beforeMethod();
        this.accountId = UUID.randomUUID();
        this.bundleId = UUID.randomUUID();
        this.subscriptionId = UUID.randomUUID();
    }

    //
    // In all tests:
    // * Events are B(locked) or U(nblocked)
    // * Types are (A(ccount), B(undle), S(ubscription))

    //             B    B     U     U
    //             |----|-----|-----|
    //             A    B     A     B
    //
    //  Expected:  B----------------U
    //
    @Test(groups = "fast")
    public void testInterlaceTypes() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, true, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(2)));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, false, testInit.plusDays(3)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(2)),
                                                                 new DisabledDuration(testInit.plusDays(1), testInit.plusDays(3)));

        verify(result, expected);
    }


    //               B    B     U
    //               |----|-----|-----
    //               A    B     A
    //
    // Expected:     B-------------------
    //
    @Test(groups = "fast")
    public void testInterlaceTypesWithNoEnd() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, true, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(2)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(2)),
                                                                 new DisabledDuration(testInit.plusDays(1), null));

        verify(result, expected);
    }

    //             B    U     B     U
    //             |----|-----|-----|
    //             A    A     A     A
    //
    //  Expected:  B----------------U
    //
    @Test(groups = "fast")
    public void testMultipleDisabledDurations() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit.plusDays(2)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(3)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(1)),
                                                                 new DisabledDuration(testInit.plusDays(2), testInit.plusDays(3)));

        verify(result, expected);
    }


    //             B    U     U
    //             |----|-----|
    //             AB   B     A
    //
    //  Expected:  B----------U
    //
    @Test(groups = "fast")
    public void testSameBlockingDates() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, false, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(2)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(1)),
                                                                 new DisabledDuration(testInit, testInit.plusDays(2)));

        verify(result, expected);
    }


    //             BU
    //             |
    //             AA
    //
    //  Expected:  None
    //
    @Test(groups = "fast")
    public void testSameBlockingUnblockingDates() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of();

        verify(result, expected);
    }


    //             B U
    //             |-|
    //             A A
    //
    //  Expected:  None
    //
    @Test(groups = "fast")
    public void testBlockingUnblockingDatesLessThanADay1() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusHours(10)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of();

        verify(result, expected);
    }


    //              B       BU
    //              |-------|
    //              A       AA
    //
    //  Expected:   B--------
    //
    @Test(groups = "fast")
    public void testBlockingUnblockingDatesLessThanADay2() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit.plusDays(1)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(1)),
                                                                 new DisabledDuration(testInit.plusDays(1), null));

        verify(result, expected);
    }


    //              B       BU
    //              |-------|
    //              A       AA
    //
    //  Expected:   B--------
    //
    @Test(groups = "fast")
    public void testBlockingUnblockingDatesLessThanADay3() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(1)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(1)));

        verify(result, expected);
    }


    //              B       UB
    //              |-------|
    //              A       AA
    //
    //  Expected:   B--------
    //
    @Test(groups = "fast")
    public void testBlockingUnblockingDatesLessThanADay4() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, false, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit.plusDays(1)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit, testInit.plusDays(1)),
                                                                 new DisabledDuration(testInit.plusDays(1), null));

        verify(result, expected);
    }


    //              U       B    B
    //              |-------|----|
    //              B       A    B
    //
    //  Expected:           B--------
    //
    @Test(groups = "fast")
    public void testStartingWithUnblock() throws Exception {

        final List<BlockingState> input = new ArrayList<BlockingState>();

        final DateTimeZone tz = DateTimeZone.forID("America/Los_Angeles");
        final DateTime testInit = new DateTime(2017, 04, 29, 14, 15, 53, tz);
        clock.setTime(testInit);
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, false, testInit));
        input.add(createBillingBlockingState(BlockingStateType.ACCOUNT, true, testInit.plusDays(1)));
        input.add(createBillingBlockingState(BlockingStateType.SUBSCRIPTION_BUNDLE, true, testInit.plusDays(2)));

        final BlockingStateService test = new BlockingStateService();
        for (BlockingState cur : input) {
            test.addBlockingState(cur);
        }
        final List<DisabledDuration> result = test.build();

        final List<DisabledDuration> expected = ImmutableList.of(new DisabledDuration(testInit.plusDays(2), null),
                                                                 new DisabledDuration(testInit.plusDays(1), null));

        verify(result, expected);
    }




    private void verify(final List<DisabledDuration> actual, final List<DisabledDuration> expected) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < actual.size(); i++) {
            boolean found = false;
            for (int j = 0; j < expected.size(); j++) {
                if (actual.get(i).equals(expected.get(j))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    private BlockingState createBillingBlockingState(final BlockingStateType type, final boolean blockBilling, final DateTime effectiveDate) {
        final UUID blockedId;
        switch(type) {
            case ACCOUNT:
                blockedId = accountId;
                break;
            case SUBSCRIPTION_BUNDLE:
                blockedId = bundleId;
                break;
            case SUBSCRIPTION:
                blockedId = subscriptionId;
                break;
            default:
                throw new IllegalStateException("Unexpexted type");
        }
        return new DefaultBlockingState(blockedId, type, UUID.randomUUID().toString(), "SVC", false, false, blockBilling, effectiveDate);
    }

}