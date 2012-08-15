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
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.jaxrs.JaxrsTestSuite;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.clock.DefaultClock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestBundleJsonWithSubscriptions extends JaxrsTestSuite {

    @Test(groups = "fast")
    public void testJson() throws Exception {
        final SubscriptionTimeline.ExistingEvent event = Mockito.mock(SubscriptionTimeline.ExistingEvent.class);
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(UUID.randomUUID().toString(), ProductCategory.BASE,
                                                                             BillingPeriod.NO_BILLING_PERIOD, UUID.randomUUID().toString(),
                                                                             PhaseType.EVERGREEN);
        Mockito.when(event.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(event.getEventId()).thenReturn(eventId);
        Mockito.when(event.getSubscriptionTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(event.getPlanPhaseSpecifier()).thenReturn(planPhaseSpecifier);

        final SubscriptionTimeline subscriptionTimeline = Mockito.mock(SubscriptionTimeline.class);
        Mockito.when(subscriptionTimeline.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionTimeline.getExistingEvents()).thenReturn(ImmutableList.<SubscriptionTimeline.ExistingEvent>of(event));

        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        final SubscriptionJsonWithEvents subscription = new SubscriptionJsonWithEvents(bundleId, subscriptionTimeline, null, ImmutableMap.<UUID, List<AuditLog>>of());
        final List<AuditLogJson> auditLogs = createAuditLogsJson();
        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundleId.toString(), externalKey, ImmutableList.<SubscriptionJsonWithEvents>of(subscription), auditLogs);
        Assert.assertEquals(bundleJsonWithSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonWithSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonWithSubscriptions.getSubscriptions().size(), 1);
        Assert.assertEquals(bundleJsonWithSubscriptions.getAuditLogs(), auditLogs);

        final String asJson = mapper.writeValueAsString(bundleJsonWithSubscriptions);
        final BundleJsonWithSubscriptions fromJson = mapper.readValue(asJson, BundleJsonWithSubscriptions.class);
        Assert.assertEquals(fromJson, bundleJsonWithSubscriptions);
    }

    @Test(groups = "fast")
    public void testFromBundleTimeline() throws Exception {
        final SubscriptionTimeline.ExistingEvent event = Mockito.mock(SubscriptionTimeline.ExistingEvent.class);
        final DateTime effectiveDate = DefaultClock.toUTCDateTime(new DateTime(DateTimeZone.UTC));
        final UUID eventId = UUID.randomUUID();
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier(UUID.randomUUID().toString(), ProductCategory.BASE,
                                                                             BillingPeriod.NO_BILLING_PERIOD, UUID.randomUUID().toString(),
                                                                             PhaseType.EVERGREEN);
        Mockito.when(event.getEffectiveDate()).thenReturn(effectiveDate);
        Mockito.when(event.getEventId()).thenReturn(eventId);
        Mockito.when(event.getSubscriptionTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(event.getPlanPhaseSpecifier()).thenReturn(planPhaseSpecifier);

        final SubscriptionTimeline subscriptionTimeline = Mockito.mock(SubscriptionTimeline.class);
        Mockito.when(subscriptionTimeline.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscriptionTimeline.getExistingEvents()).thenReturn(ImmutableList.<SubscriptionTimeline.ExistingEvent>of(event));

        final BundleTimeline bundleTimeline = Mockito.mock(BundleTimeline.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        Mockito.when(bundleTimeline.getBundleId()).thenReturn(bundleId);
        Mockito.when(bundleTimeline.getExternalKey()).thenReturn(externalKey);
        Mockito.when(bundleTimeline.getSubscriptions()).thenReturn(ImmutableList.<SubscriptionTimeline>of(subscriptionTimeline));

        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundleTimeline, null,
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of(),
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of());
        Assert.assertEquals(bundleJsonWithSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonWithSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonWithSubscriptions.getSubscriptions().size(), 1);
        final SubscriptionJsonWithEvents events = bundleJsonWithSubscriptions.getSubscriptions().get(0);
        Assert.assertNull(events.getDeletedEvents());
        Assert.assertNull(events.getNewEvents());
        Assert.assertEquals(events.getEvents().size(), 1);
        // Note - ms are truncated
        Assert.assertEquals(events.getEvents().get(0).getEffectiveDate(), DefaultClock.toUTCDateTime(effectiveDate));
        Assert.assertEquals(events.getEvents().get(0).getEventId(), eventId.toString());
        Assert.assertNull(bundleJsonWithSubscriptions.getAuditLogs());
    }

    @Test(groups = "fast")
    public void testFromSubscriptionBundle() throws Exception {
        final BundleTimeline bundle = Mockito.mock(BundleTimeline.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        Mockito.when(bundle.getBundleId()).thenReturn(bundleId);
        Mockito.when(bundle.getExternalKey()).thenReturn(externalKey);

        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundle, null,
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of(),
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of());
        Assert.assertEquals(bundleJsonWithSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonWithSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonWithSubscriptions.getSubscriptions().size(), 0);
        Assert.assertNull(bundleJsonWithSubscriptions.getAuditLogs());
    }
}
