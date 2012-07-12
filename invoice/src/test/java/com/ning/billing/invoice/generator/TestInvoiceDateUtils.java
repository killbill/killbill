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

package com.ning.billing.invoice.generator;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestInvoiceDateUtils {
    @Test(groups = "fast")
    public void testBeforeBCDWithAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-02T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-03-03T00:03:47.000Z", DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-03T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-04-03T00:03:47.000Z", DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-04T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-04-03T00:03:47.000Z", DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testBeforeBCDWithOnOrAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-02T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-03-03T00:03:47.000Z", DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testEqualBCDWithOnOrAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-03T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-03-03T00:03:47.000Z", DateTimeZone.UTC));
    }

    @Test(groups = "fast")
    public void testAfterBCDWithOnOrAfter() throws Exception {
        final DateTime from = new DateTime("2012-03-04T00:03:47.000Z", DateTimeZone.UTC);
        final DateTime to = InvoiceDateUtils.calculateBillingCycleDateOnOrAfter(from, 3);
        Assert.assertEquals(to, new DateTime("2012-04-03T00:03:47.000Z", DateTimeZone.UTC));
    }
}
