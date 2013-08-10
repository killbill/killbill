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
        final String externalKey = UUID.randomUUID().toString();
        final EntitlementJsonWithEvents subscription = null; // STEPH_ENT new EntitlementJsonWithEvents(bundleId, subscriptionTimeline, null, ImmutableMap.<UUID, List<AuditLog>>of());
        final List<AuditLogJson> auditLogs = createAuditLogsJson(clock.getUTCNow());
        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundleId.toString(), externalKey, ImmutableList.<EntitlementJsonWithEvents>of(subscription), auditLogs);
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

        final BundleBaseTimeline bundleBaseTimeline = Mockito.mock(BundleBaseTimeline.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        Mockito.when(bundleBaseTimeline.getId()).thenReturn(bundleId);
        Mockito.when(bundleBaseTimeline.getExternalKey()).thenReturn(externalKey);
        Mockito.when(bundleBaseTimeline.getSubscriptions()).thenReturn(ImmutableList.<SubscriptionBaseTimeline>of(subscriptionTimeline));

        final BundleJsonWithSubscriptions bundleJsonWithSubscriptions = new BundleJsonWithSubscriptions(bundleBaseTimeline, null,
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of(),
                                                                                                        ImmutableMap.<UUID, List<AuditLog>>of());
        Assert.assertEquals(bundleJsonWithSubscriptions.getBundleId(), bundleId.toString());
        Assert.assertEquals(bundleJsonWithSubscriptions.getExternalKey(), externalKey);
        Assert.assertEquals(bundleJsonWithSubscriptions.getSubscriptions().size(), 1);
        final EntitlementJsonWithEvents events = bundleJsonWithSubscriptions.getSubscriptions().get(0);
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
        final BundleBaseTimeline bundle = Mockito.mock(BundleBaseTimeline.class);
        final UUID bundleId = UUID.randomUUID();
        final String externalKey = UUID.randomUUID().toString();
        Mockito.when(bundle.getId()).thenReturn(bundleId);
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
