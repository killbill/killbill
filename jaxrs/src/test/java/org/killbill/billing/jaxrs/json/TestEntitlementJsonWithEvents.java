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

package org.killbill.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.clock.DefaultClock;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionJson.EventSubscriptionJson;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import static org.killbill.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestEntitlementJsonWithEvents extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String someUUID = UUID.randomUUID().toString();
        final String accountId = UUID.randomUUID().toString();
        final String bundleId = UUID.randomUUID().toString();
        final String subscriptionId = UUID.randomUUID().toString();
        final String externalKey = UUID.randomUUID().toString();
        final DateTime requestedDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final EventSubscriptionJson newEvent = new EventSubscriptionJson(eventId.toString(),
                                                                                                                                       BillingPeriod.NO_BILLING_PERIOD.toString(),
                                                                                                                                       requestedDate.toLocalDate(),
                                                                                                                                       effectiveDate.toLocalDate(),
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       SubscriptionBaseTransitionType.CREATE.toString(),
                                                                                                                                       PhaseType.DISCOUNT.toString(),
                                                                                                                                       auditLogs);
        final SubscriptionJson entitlementJsonWithEvents = new SubscriptionJson(accountId, bundleId, subscriptionId, externalKey,
                                                                                                    new LocalDate(), someUUID, someUUID, someUUID, someUUID,
                                                                                                    new LocalDate(), new LocalDate(), new LocalDate(), new LocalDate(),
                                                                                                    null, null, null, null);


        final String asJson = mapper.writeValueAsString(entitlementJsonWithEvents);

        final SubscriptionJson fromJson = mapper.readValue(asJson, SubscriptionJson.class);
        Assert.assertEquals(fromJson, entitlementJsonWithEvents);
    }
}
