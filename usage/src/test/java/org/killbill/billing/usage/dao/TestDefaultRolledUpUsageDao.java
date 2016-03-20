/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.usage.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.usage.UsageTestSuiteWithEmbeddedDB;
import org.killbill.billing.util.UUIDs;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestDefaultRolledUpUsageDao extends UsageTestSuiteWithEmbeddedDB {

    @Test(groups = "slow")
    public void testSimple() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType = "foo";
        final LocalDate startDate = new LocalDate(2013, 1, 1);
        final LocalDate endDate = new LocalDate(2013, 2, 1);
        final Long amount1 = 10L;
        final Long amount2 = 5L;

        RolledUpUsageModelDao usage1 = new RolledUpUsageModelDao(subscriptionId, unitType, startDate, amount1, UUID.randomUUID().toString());
        RolledUpUsageModelDao usage2 = new RolledUpUsageModelDao(subscriptionId, unitType, endDate.minusDays(1), amount2, UUID.randomUUID().toString());
        List<RolledUpUsageModelDao> usages = new ArrayList<RolledUpUsageModelDao>();
        usages.add(usage1);
        usages.add(usage2);
        rolledUpUsageDao.record(usages, internalCallContext);

        final List<RolledUpUsageModelDao> result = rolledUpUsageDao.getUsageForSubscription(subscriptionId, startDate, endDate, unitType, internalCallContext);
        assertEquals(result.size(), 2);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getRecordDate().compareTo(startDate), 0);
        assertEquals(result.get(0).getUnitType(), unitType);
        assertEquals(result.get(0).getAmount().compareTo(amount1), 0);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getRecordDate().compareTo(endDate.minusDays(1)), 0);
        assertEquals(result.get(1).getUnitType(), unitType);
        assertEquals(result.get(1).getAmount().compareTo(amount2), 0);
    }

    @Test(groups = "slow")
    public void testMultipleUnits() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType1 = "foo";
        final String unitType2 = "bar";
        final LocalDate startDate = new LocalDate(2013, 1, 1);
        final LocalDate endDate = new LocalDate(2013, 2, 1);
        final Long amount1 = 10L;
        final Long amount2 = 5L;
        final Long amount3 = 13L;

        RolledUpUsageModelDao usage1 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate, amount1, UUID.randomUUID().toString());
        RolledUpUsageModelDao usage2 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate.plusDays(1), amount2, UUID.randomUUID().toString());
        RolledUpUsageModelDao usage3 = new RolledUpUsageModelDao(subscriptionId, unitType2, endDate.minusDays(1), amount3, UUID.randomUUID().toString());
        List<RolledUpUsageModelDao> usages = new ArrayList<RolledUpUsageModelDao>();
        usages.add(usage1);
        usages.add(usage2);
        usages.add(usage3);
        rolledUpUsageDao.record(usages, internalCallContext);

        final List<RolledUpUsageModelDao> result = rolledUpUsageDao.getAllUsageForSubscription(subscriptionId, startDate, endDate, internalCallContext);
        assertEquals(result.size(), 3);
        assertEquals(result.get(0).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(0).getRecordDate().compareTo(startDate), 0);
        assertEquals(result.get(0).getUnitType(), unitType1);
        assertEquals(result.get(0).getAmount().compareTo(amount1), 0);
        assertEquals(result.get(1).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(1).getRecordDate().compareTo(startDate.plusDays(1)), 0);
        assertEquals(result.get(1).getUnitType(), unitType1);
        assertEquals(result.get(1).getAmount().compareTo(amount2), 0);
        assertEquals(result.get(2).getSubscriptionId(), subscriptionId);
        assertEquals(result.get(2).getRecordDate().compareTo(endDate.minusDays(1)), 0);
        assertEquals(result.get(2).getUnitType(), unitType2);
        assertEquals(result.get(2).getAmount().compareTo(amount3), 0);
    }

    @Test(groups = "slow")
    public void testNoEntries() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType = "foo";
        final LocalDate startDate = new LocalDate(2013, 1, 1);
        final LocalDate endDate = new LocalDate(2013, 2, 1);

        RolledUpUsageModelDao usage1 = new RolledUpUsageModelDao(subscriptionId, unitType, endDate, 9L, UUID.randomUUID().toString());
        List<RolledUpUsageModelDao> usages = new ArrayList<RolledUpUsageModelDao>();
        usages.add(usage1);
        rolledUpUsageDao.record(usages, internalCallContext);

        final List<RolledUpUsageModelDao> result = rolledUpUsageDao.getUsageForSubscription(subscriptionId, startDate, endDate, unitType, internalCallContext);
        assertEquals(result.size(), 0);
    }

    @Test(groups = "slow")
    public void testDuplicateRecords() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType1 = "foo";
        final String unitType2 = "bar";
        final LocalDate startDate = new LocalDate(2013, 1, 1);
        final LocalDate endDate = new LocalDate(2013, 2, 1);
        final Long amount1 = 10L;
        final Long amount2 = 5L;
        final Long amount3 = 13L;

        RolledUpUsageModelDao usage1 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate, amount1, UUID.randomUUID().toString());
        RolledUpUsageModelDao usage2 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate.plusDays(1), amount2, UUID.randomUUID().toString());
        RolledUpUsageModelDao usage3 = new RolledUpUsageModelDao(subscriptionId, unitType2, endDate.minusDays(1), amount3, UUID.randomUUID().toString());

        List<RolledUpUsageModelDao> usages = new ArrayList<RolledUpUsageModelDao>();
        usages.add(usage1);
        usages.add(usage2);
        usages.add(usage3);
        rolledUpUsageDao.record(usages, internalCallContext);

        final List<RolledUpUsageModelDao> result = rolledUpUsageDao.getAllUsageForSubscription(subscriptionId, startDate, endDate, internalCallContext);
        assertEquals(result.size(), 3);

        try {
            rolledUpUsageDao.record(usages, internalCallContext);
            fail("duplicate records accepted");
        } catch (UnableToExecuteStatementException e) {
            assertEquals(result.size(), 3);
        }
    }

    @Test(groups = "slow")
    public void testRecordsWithTrackingIdExist() {
        final UUID subscriptionId = UUIDs.randomUUID();
        final String unitType1 = "foo";
        final String unitType2 = "bar";
        final LocalDate startDate = new LocalDate(2013, 1, 1);
        final LocalDate endDate = new LocalDate(2013, 2, 1);
        final Long amount1 = 10L;
        final Long amount2 = 5L;
        final Long amount3 = 13L;

        String trackingId = UUIDs.randomUUID().toString();

        RolledUpUsageModelDao usage1 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate, amount1, trackingId);
        RolledUpUsageModelDao usage2 = new RolledUpUsageModelDao(subscriptionId, unitType1, startDate.plusDays(1), amount2, trackingId);
        RolledUpUsageModelDao usage3 = new RolledUpUsageModelDao(subscriptionId, unitType2, endDate.minusDays(1), amount3, UUID.randomUUID().toString());

        List<RolledUpUsageModelDao> usages = new ArrayList<RolledUpUsageModelDao>();
        usages.add(usage1);
        usages.add(usage2);
        usages.add(usage3);
        rolledUpUsageDao.record(usages, internalCallContext);

        assertEquals(rolledUpUsageDao.recordsWithTrackingIdExist(subscriptionId, trackingId, internalCallContext),
                     Boolean.TRUE);
    }
}