/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.resources;

import java.math.BigDecimal;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UnitUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UsageRecordJson;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestUsageResource extends JaxrsTestSuiteNoDB {

    private UsageResource createUsageResource() {
        final UsageResource result = new UsageResource(
                null, // uriBuilder
                null, // tagUserApi
                null, // customFieldUserApi
                null, // auditUserApi
                null, // accountUserApi
                null, // usageUserApi
                null, // paymentApi
                null, // invoicePaymentApi
                null, // entitlementApi
                null, // clock
                null // context
        );
        return Mockito.spy(result);
    }

    @Test(groups = "fast")
    public void testGetHighestRecordDate() {
        final UsageResource usageResource = createUsageResource();

        final List<UsageRecordJson> fooRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 03, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 05).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 03, 01).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)),
                new UsageRecordJson(new LocalDate(2018, 04, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordFoo = new UnitUsageRecordJson("foo", fooRecords);

        final List<UsageRecordJson> barRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 02, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 04, 18).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)), // Highest date point
                new UsageRecordJson(new LocalDate(2018, 04, 13).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordBar = new UnitUsageRecordJson("bar", barRecords);

        final List<UsageRecordJson> zooRecords = List.of(
                new UsageRecordJson(new LocalDate(2018, 02, 04).toDateTimeAtStartOfDay(), BigDecimal.valueOf(28L)),
                new UsageRecordJson(new LocalDate(2018, 03, 06).toDateTimeAtStartOfDay(), BigDecimal.valueOf(2L)),
                new UsageRecordJson(new LocalDate(2018, 04, 17).toDateTimeAtStartOfDay(), BigDecimal.valueOf(1L)),
                new UsageRecordJson(new LocalDate(2018, 04, 12).toDateTimeAtStartOfDay(), BigDecimal.valueOf(24L)));

        final UnitUsageRecordJson unitRecordZoo = new UnitUsageRecordJson("zoo", zooRecords);

        final List<UnitUsageRecordJson> input = List.of(unitRecordFoo, unitRecordBar, unitRecordZoo);
        final DateTime result = usageResource.getHighestRecordDate(input);

        Assert.assertTrue(result.compareTo(new LocalDate(2018, 04, 18).toDateTimeAtStartOfDay()) == 0);
    }
}
