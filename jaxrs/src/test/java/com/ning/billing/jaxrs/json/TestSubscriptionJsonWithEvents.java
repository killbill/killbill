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
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.jaxrs.JaxrsTestSuiteNoDB;
import com.ning.billing.clock.DefaultClock;

import com.google.common.collect.ImmutableList;

import static com.ning.billing.jaxrs.JaxrsTestUtils.createAuditLogsJson;

public class TestSubscriptionJsonWithEvents extends JaxrsTestSuiteNoDB {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final String subscriptionId = UUID.randomUUID().toString();
        final DateTime requestedDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final EntitlementJsonWithEvents.SubscriptionReadEventJson newEvent = new EntitlementJsonWithEvents.SubscriptionReadEventJson(eventId.toString(),
                                                                                                                                       BillingPeriod.NO_BILLING_PERIOD.toString(),
                                                                                                                                       requestedDate,
                                                                                                                                       effectiveDate,
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       SubscriptionBaseTransitionType.CREATE.toString(),
                                                                                                                                       PhaseType.DISCOUNT.toString(),
                                                                                                                                       auditLogs);
        final EntitlementJsonWithEvents subscriptionJsonWithEvents = null; /* STEPH_ENT new EntitlementJsonWithEvents(subscriptionId,
                                                                                                     ImmutableList.<EntitlementJsonWithEvents.SubscriptionReadEventJson>of(newEvent),
                                                                                                     null,
                                                                                                     null,
                                                                                                     null);
                                                                                                     */

        final String asJson = mapper.writeValueAsString(subscriptionJsonWithEvents);

        final EntitlementJsonWithEvents fromJson = mapper.readValue(asJson, EntitlementJsonWithEvents.class);
        Assert.assertEquals(fromJson, subscriptionJsonWithEvents);
    }

    @Test(groups = "fast")
    public void testFromSubscription() throws Exception {
        final DateTime requestedDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final EntitlementJsonWithEvents.SubscriptionReadEventJson newEvent = new EntitlementJsonWithEvents.SubscriptionReadEventJson(eventId.toString(),
                                                                                                                                       BillingPeriod.NO_BILLING_PERIOD.toString(),
                                                                                                                                       requestedDate,
                                                                                                                                       effectiveDate,
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       UUID.randomUUID().toString(),
                                                                                                                                       SubscriptionBaseTransitionType.CREATE.toString(),
                                                                                                                                       PhaseType.DISCOUNT.toString(),
                                                                                                                                       auditLogs);

        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());

        final EntitlementJsonWithEvents subscriptionJsonWithEvents = null; /* STEPH_ENT new EntitlementJsonWithEvents(subscription,
                                                                                                     ImmutableList.<EntitlementJsonWithEvents.SubscriptionReadEventJson>of(newEvent),
                                                                                                     null,
                                                                                                     null,
                                                                                                     null); */
        Assert.assertEquals(subscriptionJsonWithEvents.getEntitlementId(), subscription.getId().toString());
        Assert.assertNull(subscriptionJsonWithEvents.getNewEvents());
        Assert.assertNull(subscriptionJsonWithEvents.getDeletedEvents());
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().size(), 1);
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().get(0).getEffectiveDate(), newEvent.getEffectiveDate());
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().get(0).getEventId(), newEvent.getEventId());
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().get(0).getAuditLogs(), auditLogs);
    }

    @Test(groups = "fast")
    public void testFromSubscriptionTimeline() throws Exception {
        final SubscriptionBaseTimeline.ExistingEvent event = Mockito.mock(SubscriptionBaseTimeline.ExistingEvent.class);
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(UUID.randomUUID().toString(), ProductCategory.BASE,
                                                                             BillingPeriod.NO_BILLING_PERIOD, UUID.randomUUID().toString(),
                                                                             PhaseType.EVERGREEN);
        Mockito.when(event.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(event.getEventId()).thenReturn(eventId);
        Mockito.when(event.getSubscriptionTransitionType()).thenReturn(SubscriptionBaseTransitionType.CREATE);
        Mockito.when(event.getPlanPhaseSpecifier()).thenReturn(planPhaseSpecifier);

        final SubscriptionBaseTimeline subscriptionTimeline = Mockito.mock(SubscriptionBaseTimeline.class);
        Mockito.when(subscriptionTimeline.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionTimeline.getExistingEvents()).thenReturn(ImmutableList.<SubscriptionBaseTimeline.ExistingEvent>of(event));

        final UUID bundleId = UUID.randomUUID();

        final EntitlementJsonWithEvents subscriptionJsonWithEvents = null; /* STEPH_ENT new EntitlementJsonWithEvents(bundleId, subscriptionTimeline,
                                                                                                     null, ImmutableMap.<UUID, List<AuditLog>>of()); */
        Assert.assertEquals(subscriptionJsonWithEvents.getEntitlementId(), subscriptionTimeline.getId().toString());
        Assert.assertNull(subscriptionJsonWithEvents.getNewEvents());
        Assert.assertNull(subscriptionJsonWithEvents.getDeletedEvents());
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().size(), 1);
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().get(0).getEffectiveDate(), event.getEffectiveDate());
        Assert.assertEquals(subscriptionJsonWithEvents.getEvents().get(0).getEventId(), event.getEventId().toString());
    }
}
