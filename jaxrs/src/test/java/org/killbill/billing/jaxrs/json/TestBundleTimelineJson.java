/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionJson.EventSubscriptionJson;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestBundleTimelineJson extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final EventSubscriptionJson event = new EventSubscriptionJson(UUID.randomUUID(),
                                                                      BillingPeriod.NO_BILLING_PERIOD,
                                                                      new LocalDate(),
                                                                      UUID.randomUUID().toString(),
                                                                      UUID.randomUUID().toString(),
                                                                      UUID.randomUUID().toString(),
                                                                      SubscriptionEventType.PHASE,
                                                                      true,
                                                                      false,
                                                                      UUID.randomUUID().toString(),
                                                                      UUID.randomUUID().toString(),
                                                                      UUID.randomUUID().toString(),
                                                                      null);
        final BundleTimelineJson bundleTimelineJson = new BundleTimelineJson(UUID.randomUUID(),
                                                                             UUID.randomUUID(),
                                                                             UUID.randomUUID().toString(),
                                                                             ImmutableList.<EventSubscriptionJson>of(event),
                                                                             null);

        final String asJson = mapper.writeValueAsString(bundleTimelineJson);
        final BundleTimelineJson fromJson = mapper.readValue(asJson, BundleTimelineJson.class);
        Assert.assertEquals(fromJson, bundleTimelineJson);
    }
}
