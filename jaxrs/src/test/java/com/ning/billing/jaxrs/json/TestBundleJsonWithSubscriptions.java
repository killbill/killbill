/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.jaxrs.json.EntitlementJsonWithEvents.SubscriptionReadEventJson;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.timeline.BundleBaseTimeline;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.clock.DefaultClock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestBundleJsonWithSubscriptions extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {

        final String someUUID = UUID.randomUUID().toString();
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());

        SubscriptionReadEventJson event = new SubscriptionReadEventJson(someUUID, BillingPeriod.NO_BILLING_PERIOD.toString(), new LocalDate(), new LocalDate(), "product", "priceList", "eventType", "phase", null);
        final EntitlementJsonWithEvents subscription = new EntitlementJsonWithEvents(someUUID, someUUID, someUUID, externalKey, ImmutableList.<SubscriptionReadEventJson>of(event), null, null, auditLogs);

        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundleId.toString(), externalKey, ImmutableList.<EntitlementJsonWithEvents>of(subscription), auditLogs);
        Assert.assertEquals(bundleJsonWithSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonWithSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonWithSubscriptions.getSubscriptions().size(), 1);
        Assert.assertEquals(bundleJsonWithSubscriptions.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(bundleJsonWithSubscriptions);
        final BundleJsonWithSubscriptions fromJson = mapper.readValue(asJson, BundleJsonWithSubscriptions.class);
        Assert.assertEquals(fromJson, bundleJsonWithSubscriptions);
    }
}
