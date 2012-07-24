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

package com.ning.billing.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.analytics.AnalyticsTestSuite;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.DefaultClock;

public class TestBusinessAccount extends AnalyticsTestSuite {

    private final Clock clock = new DefaultClock();

    private BusinessAccount account;

    @BeforeMethod(groups = "fast")
    public void setUp() throws Exception {
        account = new BusinessAccount(UUID.randomUUID(), "pierre", UUID.randomUUID().toString(), BigDecimal.ONE, clock.getUTCToday(),
                                      BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "");
    }

    @Test(groups = "fast")
    public void testEquals() throws Exception {
        Assert.assertSame(account, account);
        Assert.assertEquals(account, account);
        Assert.assertTrue(account.equals(account));

        final BusinessAccount otherAccount = new BusinessAccount(UUID.randomUUID(), "pierre cardin", UUID.randomUUID().toString(), BigDecimal.ONE, clock.getUTCToday(),
                                                                 BigDecimal.TEN, "ERROR_NOT_ENOUGH_FUNDS", "CreditCard", "Visa", "");
        Assert.assertFalse(account.equals(otherAccount));
    }

    @Test(groups = "fast")
    public void testDefaultBigDecimalValues() throws Exception {
        final BusinessAccount bac = new BusinessAccount(UUID.randomUUID());
        Assert.assertEquals(bac.getBalance(), BigDecimal.ZERO);
        Assert.assertEquals(bac.getTotalInvoiceBalance(), BigDecimal.ZERO);

        bac.setBalance(BigDecimal.ONE);
        bac.setTotalInvoiceBalance(BigDecimal.TEN);
        Assert.assertEquals(bac.getBalance(), BigDecimal.ONE);
        Assert.assertEquals(bac.getTotalInvoiceBalance(), BigDecimal.TEN);
    }
}
