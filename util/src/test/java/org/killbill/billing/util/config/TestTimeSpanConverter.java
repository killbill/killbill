/*
 * Copyright 2020-2023 Equinix, Inc
 * Copyright 2014-2023 The Billing Project, LLC
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

package org.killbill.billing.util.config;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.queue.QueueRetryException;
import org.skife.config.ConfigSource;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.TimeSpan;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestTimeSpanConverter {

    @Test(groups = "fast")
    public void testToPeriod() {
        // Original date
        final DateTime dt = new DateTime("2023-06-30T23:33:26.000+0000");

        final Period p1 = TimeSpanConverter.toPeriod(new TimeSpan("3s"));
        Assert.assertEquals(dt.plus(p1), dt.plusSeconds(3));

        final Period p2 = TimeSpanConverter.toPeriod(new TimeSpan("5m"));
        Assert.assertEquals(dt.plus(p2), dt.plusMinutes(5));

        final Period p3 = TimeSpanConverter.toPeriod(new TimeSpan("1h"));
        Assert.assertEquals(dt.plus(p3), dt.plusHours(1));
    }

    @Test(groups = "fast")
    public void testToListPeriod() {
        // Original date that will be updated
        DateTime dt = new DateTime("2023-06-30T21:49:55.000+0000");

        final List<TimeSpan> input = List.of(new TimeSpan("5s"), new TimeSpan("10m"), new TimeSpan("2h"));
        final List<Period> periods = TimeSpanConverter.toListPeriod(input);
        Assert.assertEquals(periods.size(), input.size());

        for (Period p : periods) {
            dt = dt.plus(p);
        }
        Assert.assertEquals(dt, new DateTime("2023-07-01T00:00:00.000+0000"));
    }

    @Test(groups = "fast")
    public void testToListPeriodEmptyOrNull() {
        Assert.assertTrue(TimeSpanConverter.toListPeriod(null).isEmpty());
        Assert.assertTrue(TimeSpanConverter.toListPeriod(List.of()).isEmpty());
    }


    @Test(groups = "fast")
    public void testEmptyRescheduleIntervalOnLock() {

        // Simulate a case where we don't want any retry schedule
        final ConfigSource configSource = new ConfigSource() {
            @Override
            public String getString(final String propertyName) {
                if ("org.killbill.rescheduleIntervalOnLock".equals(propertyName)) {
                    return "";
                } else {
                    return null;
                }
            }
        };
        final InvoiceConfig invoiceConfig = new AugmentedConfigurationObjectFactory(configSource).build(InvoiceConfig.class);

        final List<TimeSpan> retryIntervals = invoiceConfig.getRescheduleIntervalOnLock();
        Assert.assertEquals(0, retryIntervals.size());

        final List<Period> periods = TimeSpanConverter.toListPeriod(retryIntervals);
        final QueueRetryException e = new QueueRetryException(null, periods);
        Assert.assertEquals(0, e.getRetrySchedule().size());
    }

}