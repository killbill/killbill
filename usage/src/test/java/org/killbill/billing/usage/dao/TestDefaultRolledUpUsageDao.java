package org.killbill.billing.usage.dao;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.usage.UsageTestSuiteWithEmbeddedDB;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDefaultRolledUpUsageDao extends UsageTestSuiteWithEmbeddedDB {

    @BeforeMethod(groups = "slow")
    public void setUp() throws Exception {
    }

    @Test(groups = "slow")
    public void testSimple() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType = "foo";
        final DateTime startDate = new DateTime(2013, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime endDate = new DateTime(2013, 2, 1, 0, 0, DateTimeZone.UTC);
        final BigDecimal amount1 = BigDecimal.TEN;
        final BigDecimal amount2 = BigDecimal.TEN;

        rolledUpUsageDao.record(subscriptionId, unitType, startDate, endDate, amount1, internalCallContext);
        rolledUpUsageDao.record(subscriptionId, unitType, startDate, endDate, amount2, internalCallContext);

        final RolledUpUsageModelDao result = rolledUpUsageDao.getUsageForSubscription(subscriptionId, startDate, endDate, unitType, internalCallContext);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getStartTime().compareTo(startDate), 0);
        assertEquals(result.getEndTime().compareTo(endDate), 0);
        assertEquals(result.getUnitType(), unitType);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getAmount().compareTo(amount1.add(amount2)), 0);
    }

    @Test(groups = "slow")
    public void testNoEntries() {
        final UUID subscriptionId = UUID.randomUUID();
        final String unitType = "foo";
        final DateTime startDate = new DateTime(2013, 1, 1, 0, 0, DateTimeZone.UTC);
        final DateTime endDate = new DateTime(2013, 2, 1, 0, 0, DateTimeZone.UTC);

        final RolledUpUsageModelDao result = rolledUpUsageDao.getUsageForSubscription(subscriptionId, startDate, endDate, unitType, internalCallContext);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getStartTime().compareTo(startDate), 0);
        assertEquals(result.getEndTime().compareTo(endDate), 0);
        assertEquals(result.getUnitType(), unitType);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getSubscriptionId(), subscriptionId);
        assertEquals(result.getAmount().compareTo(BigDecimal.ZERO), 0);
    }

}
