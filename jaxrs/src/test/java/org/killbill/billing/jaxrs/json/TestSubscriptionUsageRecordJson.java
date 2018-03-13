/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.jaxrs.json;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UnitUsageRecordJson;
import org.killbill.billing.jaxrs.json.SubscriptionUsageRecordJson.UsageRecordJson;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestSubscriptionUsageRecordJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final LocalDate localDate = new LocalDate();
        final UUID subscriptionId = UUID.randomUUID();
        final String trackingId = UUID.randomUUID().toString();
        final List<UnitUsageRecordJson> unitUsageRecords = new ArrayList<UnitUsageRecordJson>();
        final List<UsageRecordJson> usageRecords = new ArrayList<UsageRecordJson>();
        final UsageRecordJson usageRecordJson = new UsageRecordJson(localDate, 5L);
        usageRecords.add(usageRecordJson);
        final UnitUsageRecordJson unitUsageRecordJson = new UnitUsageRecordJson("foo", usageRecords);
        unitUsageRecords.add(unitUsageRecordJson);

        final SubscriptionUsageRecordJson subscriptionUsageRecordJson = new SubscriptionUsageRecordJson(subscriptionId, trackingId, unitUsageRecords);
        Assert.assertEquals(subscriptionUsageRecordJson.getSubscriptionId(), subscriptionId);
        Assert.assertEquals(subscriptionUsageRecordJson.getTrackingId(), trackingId);
        Assert.assertEquals(subscriptionUsageRecordJson.getUnitUsageRecords().size(), 1);
        Assert.assertEquals(subscriptionUsageRecordJson.getUnitUsageRecords().get(0).getUnitType(), "foo");
        Assert.assertEquals(subscriptionUsageRecordJson.getUnitUsageRecords().get(0).getUsageRecords().size(), 1);
        Assert.assertEquals(subscriptionUsageRecordJson.getUnitUsageRecords().get(0).getUsageRecords().get(0).getAmount(), new Long(5L));
        Assert.assertEquals(subscriptionUsageRecordJson.getUnitUsageRecords().get(0).getUsageRecords().get(0).getRecordDate(), localDate);

        final SubscriptionUsageRecord subscriptionUsageRecord = subscriptionUsageRecordJson.toSubscriptionUsageRecord();
        Assert.assertEquals(subscriptionUsageRecord.getSubscriptionId(), subscriptionId);
        Assert.assertEquals(subscriptionUsageRecord.getTrackingId(), trackingId);
        Assert.assertEquals(subscriptionUsageRecord.getUnitUsageRecord().size(), 1);
        Assert.assertEquals(subscriptionUsageRecord.getUnitUsageRecord().get(0).getUnitType(), "foo");
        Assert.assertEquals(subscriptionUsageRecord.getUnitUsageRecord().get(0).getDailyAmount().size(), 1);
        Assert.assertEquals(subscriptionUsageRecord.getUnitUsageRecord().get(0).getDailyAmount().get(0).getAmount(), new Long(5L));
        Assert.assertEquals(subscriptionUsageRecord.getUnitUsageRecord().get(0).getDailyAmount().get(0).getDate(), localDate);
    }
}