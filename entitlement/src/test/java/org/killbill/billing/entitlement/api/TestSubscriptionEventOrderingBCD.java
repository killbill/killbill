/*
 * Copyright 2024-2026 The Billing Project, LLC
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

package org.killbill.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Regression test for issue #2224: BCD_UPDATE events were silently dropped by
 * {@link SubscriptionEventOrdering} because the {@code BCD_CHANGE} branch was
 * missing from the internal {@code toEventTypes} switch. As a result, BCD
 * updates never appeared in the subscription events returned by the API.
 *
 * <p>The fix surfaces the event as
 * {@link SubscriptionEventType#SERVICE_STATE_CHANGE} (the closest existing
 * value in the public API enum) and tags it with a {@code serviceStateName}
 * of {@code "BCD_CHANGE"} so consumers can distinguish it.
 */
public class TestSubscriptionEventOrderingBCD {

    @Test(groups = "fast")
    public void testBcdChangeTransitionSurfacedAsSubscriptionEvent() {
        final UUID eventId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effective = new DateTime(2026, 5, 15, 0, 0, DateTimeZone.UTC);
        final DateTime created = effective.minusDays(1);

        final SubscriptionBaseTransition tr = Mockito.mock(SubscriptionBaseTransition.class);
        Mockito.when(tr.getId()).thenReturn(eventId);
        Mockito.when(tr.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(tr.getEffectiveTransitionTime()).thenReturn(effective);
        Mockito.when(tr.getCreatedDate()).thenReturn(created);
        Mockito.when(tr.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.BCD_CHANGE);
        // BCD updates do not carry plan/phase/pricelist info — leave them null
        Mockito.when(tr.getPreviousPlan()).thenReturn(null);
        Mockito.when(tr.getPreviousPhase()).thenReturn(null);
        Mockito.when(tr.getPreviousPriceList()).thenReturn(null);
        Mockito.when(tr.getNextPlan()).thenReturn(null);
        Mockito.when(tr.getNextPhase()).thenReturn(null);
        Mockito.when(tr.getNextPriceList()).thenReturn(null);

        final SubscriptionEvent event = SubscriptionEventOrdering.toSubscriptionEvent(
                tr, SubscriptionEventType.SERVICE_STATE_CHANGE, (InternalTenantContext) null);

        assertNotNull(event, "BCD_CHANGE transition must produce a non-null SubscriptionEvent");
        assertEquals(event.getId(), eventId);
        assertEquals(event.getEntitlementId(), subscriptionId);
        assertEquals(event.getEffectiveDate(), effective);
        assertEquals(event.getSubscriptionEventType(), SubscriptionEventType.SERVICE_STATE_CHANGE,
                "BCD updates surface as SERVICE_STATE_CHANGE (closest existing API enum value)");
        assertEquals(event.getServiceStateName(), SubscriptionBaseTransitionType.BCD_CHANGE.toString(),
                "serviceStateName must be 'BCD_CHANGE' so consumers can distinguish BCD updates "
                        + "from other SERVICE_STATE_CHANGE events");
        assertEquals(event.getServiceName(), EntitlementOrderingBase.BILLING_SERVICE_NAME,
                "BCD updates are a billing-side concept, so serviceName must reflect that");
    }

    @Test(groups = "fast")
    public void testNonBcdTransitionUsesDefaultServiceStateName() {
        // Sanity check: regular CHANGE events must not be affected by the BCD branch.
        final UUID eventId = UUID.randomUUID();
        final UUID subscriptionId = UUID.randomUUID();
        final DateTime effective = new DateTime(2026, 5, 15, 0, 0, DateTimeZone.UTC);

        final SubscriptionBaseTransition tr = Mockito.mock(SubscriptionBaseTransition.class);
        Mockito.when(tr.getId()).thenReturn(eventId);
        Mockito.when(tr.getSubscriptionId()).thenReturn(subscriptionId);
        Mockito.when(tr.getEffectiveTransitionTime()).thenReturn(effective);
        Mockito.when(tr.getCreatedDate()).thenReturn(effective);
        Mockito.when(tr.getTransitionType()).thenReturn(SubscriptionBaseTransitionType.CHANGE);
        Mockito.when(tr.getPreviousPlan()).thenReturn(null);
        Mockito.when(tr.getPreviousPhase()).thenReturn(null);
        Mockito.when(tr.getPreviousPriceList()).thenReturn(null);
        Mockito.when(tr.getNextPlan()).thenReturn(null);
        Mockito.when(tr.getNextPhase()).thenReturn(null);
        Mockito.when(tr.getNextPriceList()).thenReturn(null);

        final SubscriptionEvent event = SubscriptionEventOrdering.toSubscriptionEvent(
                tr, SubscriptionEventType.CHANGE, (InternalTenantContext) null);

        assertEquals(event.getSubscriptionEventType(), SubscriptionEventType.CHANGE);
        assertEquals(event.getServiceStateName(), SubscriptionEventType.CHANGE.toString(),
                "Non-BCD events keep the historical serviceStateName == eventType.toString() behavior");
    }
}
