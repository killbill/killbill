/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.subscription.api.user;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventBuilder;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.subscription.events.user.ApiEventType.CREATE;

public class TestDefaultSubscriptionBase extends SubscriptionTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCancelSOT() throws Exception {
        final DateTime startDate = new DateTime(2012, 5, 1, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(startDate));

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("laser-scope-monthly")
                                                                .setEventPlanPhase("laser-scope-monthly-discount")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(startDate)
                                                                .setUpdatedDate(startDate)
                                                                .setEffectiveDate(startDate)
                                                                .setTotalOrdering(3)
                                                                .setActive(true)));
        inputEvents.add(new ApiEventCancel(new ApiEventBuilder().setApiEventType(ApiEventType.CANCEL)
                                                                .setEventPlan(null)
                                                                .setEventPlanPhase(null)
                                                                .setEventPriceList(null)
                                                                .setFromDisk(false)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(startDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(startDate)
                                                                .setTotalOrdering(0) // In-memory event
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);

        Assert.assertEquals(subscriptionBase.getAllTransitions().size(), 2);
        Assert.assertNull(subscriptionBase.getAllTransitions().get(0).getPreviousState());
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(0).getNextState(), EntitlementState.ACTIVE);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(0).getEffectiveTransitionTime(), startDate);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getPreviousState(), EntitlementState.ACTIVE);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getNextState(), EntitlementState.CANCELLED);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getEffectiveTransitionTime(), startDate);
    }

    @Test(groups = "fast", description = "https://github.com/killbill/killbill/issues/897")
    public void testFutureCancelBeforePhase() throws Exception {
        final DateTime startDate = new DateTime(2012, 5, 1, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(startDate));

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("laser-scope-monthly")
                                                                .setEventPlanPhase("laser-scope-monthly-discount")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(startDate)
                                                                .setUpdatedDate(startDate)
                                                                .setEffectiveDate(startDate)
                                                                .setTotalOrdering(3)
                                                                .setActive(true)));
        inputEvents.add(new PhaseEventData(new PhaseEventBuilder().setPhaseName("laser-scope-monthly-evergreen")
                                                                  .setUuid(UUID.randomUUID())
                                                                  .setSubscriptionId(subscriptionId)
                                                                  .setCreatedDate(startDate)
                                                                  .setUpdatedDate(startDate)
                                                                  .setEffectiveDate(new DateTime(2012, 6, 1, 0, 0, DateTimeZone.UTC))
                                                                  .setTotalOrdering(4)
                                                                  .setActive(true)));
        inputEvents.add(new ApiEventCancel(new ApiEventBuilder().setApiEventType(ApiEventType.CANCEL)
                                                                .setEventPlan(null)
                                                                .setEventPlanPhase(null)
                                                                .setEventPriceList(null)
                                                                .setFromDisk(false)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(startDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(new DateTime(2012, 6, 1, 0, 0, DateTimeZone.UTC))
                                                                .setTotalOrdering(0) // In-memory event
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);

        Assert.assertEquals(subscriptionBase.getAllTransitions().size(), 2);
        Assert.assertNull(subscriptionBase.getAllTransitions().get(0).getPreviousState());
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(0).getNextState(), EntitlementState.ACTIVE);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(0).getEffectiveTransitionTime(), startDate);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getPreviousState(), EntitlementState.ACTIVE);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getNextState(), EntitlementState.CANCELLED);
        Assert.assertEquals(subscriptionBase.getAllTransitions().get(1).getEffectiveTransitionTime(), new DateTime(2012, 6, 1, 0, 0, DateTimeZone.UTC));
    }
}
