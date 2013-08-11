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
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.clock.DefaultClock;
import com.ning.billing.entitlement.api.SubscriptionBundleTimeline.SubscriptionEvent;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;
import com.ning.billing.jaxrs.json.EntitlementJsonWithEvents.SubscriptionReadEventJson;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestEntitlementJsonWithEvents extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String accountId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String entitlementId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final DateTime requestedDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final EntitlementJsonWithEvents.SubscriptionReadEventJson newEvent = new EntitlementJsonWithEvents.SubscriptionReadEventJson(eventId.toString(),
                                                                                                                                     BillingPeriod.NO_BILLING_PERIOD.toString(),
                                                                                                                                     requestedDate.toLocalDate(),
                                                                                                                                     effectiveDate.toLocalDate(),
                                                                                                                                     UUID.randomUUID().toString(),
                                                                                                                                     UUID.randomUUID().toString(),
                                                                                                                                     SubscriptionBaseTransitionType.CREATE.toString(),
                                                                                                                                     PhaseType.DISCOUNT.toString(),
                                                                                                                                     auditLogs);
        final SubscriptionEvent event = null;
        final EntitlementJsonWithEvents entitlementJsonWithEvents = new EntitlementJsonWithEvents(accountId, bundleId, entitlementId, externalKey, ImmutableList.<SubscriptionReadEventJson>of(newEvent), null, null, auditLogs);


        final String asJson = mapper.writeValueAsString(entitlementJsonWithEvents);

        final EntitlementJsonWithEvents fromJson = mapper.readValue(asJson, EntitlementJsonWithEvents.class);
        Assert.assertEquals(fromJson, entitlementJsonWithEvents);
    }
}
