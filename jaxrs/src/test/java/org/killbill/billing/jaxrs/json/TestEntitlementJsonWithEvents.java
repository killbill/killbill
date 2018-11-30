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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementSourceType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.jaxrs.JaxrsTestSuiteNoDB;
import org.killbill.billing.jaxrs.json.SubscriptionJson.EventSubscriptionJson;
import org.killbill.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.killbill.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestEntitlementJsonWithEvents extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String externalKey = UUID.randomUUID().toString();
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());



        final EventSubscriptionJson newEvent = new EventSubscriptionJson(eventId,
                                                                         BillingPeriod.NO_BILLING_PERIOD,
                                                                         effectiveDate.toLocalDate(),
                                                                         UUID.randomUUID().toString(),
                                                                         UUID.randomUUID().toString(),
                                                                         UUID.randomUUID().toString(),
                                                                         SubscriptionEventType.PHASE,
                                                                         false,
                                                                         true,
                                                                         UUID.randomUUID().toString(),
                                                                         UUID.randomUUID().toString(),
                                                                         UUID.randomUUID().toString(),
                                                                         auditLogs);

        final PhasePriceJson priceOverride = new PhasePriceJson("foo", "bar", null, BigDecimal.TEN, BigDecimal.ONE, null);

        final SubscriptionJson entitlementJsonWithEvents = new SubscriptionJson(UUID.randomUUID(),
                                                                   UUID.randomUUID(),
                                                                   UUID.randomUUID(),
                                                                   externalKey,
                                                                   new LocalDate(),
                                                                   UUID.randomUUID().toString(),
                                                                   ProductCategory.BASE,
                                                                   BillingPeriod.MONTHLY,
                                                                   PhaseType.EVERGREEN,
                                                                   UUID.randomUUID().toString(),
                                                                   UUID.randomUUID().toString(),
                                                                   EntitlementState.ACTIVE,
                                                                   EntitlementSourceType.NATIVE,
                                                                   new LocalDate(),
                                                                   new LocalDate(),
                                                                   new LocalDate(),
                                                                   new LocalDate(),
                                                                   null,
                                                                   ImmutableList.<EventSubscriptionJson>of(newEvent),
                                                                   ImmutableList.of(priceOverride),
                                                                   null,
                                                                   auditLogs);
        final String asJson = mapper.writeValueAsString(entitlementJsonWithEvents);

        final SubscriptionJson fromJson = mapper.readValue(asJson, SubscriptionJson.class);
        Assert.assertEquals(fromJson, entitlementJsonWithEvents);
    }
}
