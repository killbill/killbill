/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventBuilder;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventChange;
import org.killbill.billing.subscription.events.user.ApiEventCreate;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.killbill.billing.subscription.events.user.ApiEventType.CREATE;

public class TestSubscriptionBillingEvents extends SubscriptionTestSuiteNoDB {

    //  Catalog config for all scenarii:
    //
    //  4 catalogs versions, we use gold-monthly, with following effectiveDateForExistingSubscriptions (effSubDt):
    //
    //  * V1 : 2011-01-01
    //
    //  * V2 : 2011-02-02, effSubDt = 2011-02-14
    //
    //  * V3 : 2011-02-03, effSubDt = 2011-02-14
    //
    //  * V4 : 2011-03-03, effSubDt = 2011-03-14

    private static final DateTime EFF_V1 = new DateTime("2011-01-01T00:00:00+00:00");

    private static final DateTime EFF_V2 = new DateTime("2011-02-02T00:00:00+00:00");
    private static final DateTime EFF_SUB_DT_V2 = new DateTime("2011-02-14T00:00:00+00:00");

    private static final DateTime EFF_V3 = new DateTime("2011-02-03T00:00:00+00:00");
    private static final DateTime EFF_SUB_DT_V3 = EFF_SUB_DT_V2;

    private static final DateTime EFF_V4 = new DateTime("2011-03-03T00:00:00+00:00");
    private static final DateTime EFF_SUB_DT_V4 = new DateTime("2011-03-14T00:00:00+00:00");

    @Override
    protected KillbillConfigSource getConfigSource(final Map<String, String> extraProperties) {
        final Map<String, String> allExtraProperties = new HashMap<String, String>(extraProperties);
        allExtraProperties.put("org.killbill.catalog.uri", "catalogs/subscriptionBillingEvents");
        return getConfigSource(null, allExtraProperties);
    }


    @Test(groups = "fast")
    public void testWithCancelation_Before_EffSubDtV2() throws Exception {


        final DateTime createDate = new DateTime(2011, 1, 2, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(createDate), subscriptionBaseApiService, clock);

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("gold-monthly")
                                                                .setEventPlanPhase("gold-monthly-trial")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(createDate)
                                                                .setEffectiveDate(createDate)
                                                                .setTotalOrdering(1)
                                                                .setActive(true)));

        final DateTime evergreenPhaseDate = createDate.plusDays(30);
        inputEvents.add(new PhaseEventData(new PhaseEventBuilder().setPhaseName("gold-monthly-evergreen")
                                                                  .setUuid(UUID.randomUUID())
                                                                  .setSubscriptionId(subscriptionId)
                                                                  .setCreatedDate(evergreenPhaseDate)
                                                                  .setUpdatedDate(evergreenPhaseDate)
                                                                  .setEffectiveDate(evergreenPhaseDate)
                                                                  .setTotalOrdering(1)
                                                                  .setActive(true)));

        final DateTime cancelDate = new DateTime(2011, 2, 13, 0, 0, DateTimeZone.UTC);

        inputEvents.add(new ApiEventCancel(new ApiEventBuilder().setApiEventType(ApiEventType.CANCEL)
                                                                .setEventPlan(null)
                                                                .setEventPlanPhase(null)
                                                                .setEventPriceList(null)
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(cancelDate)
                                                                .setTotalOrdering(2)
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);


        final List<SubscriptionBillingEvent> result = subscriptionBase.getSubscriptionBillingEvents(catalog.getCatalog(), subscriptionCatalogApi.getPriceOverrideSvcStatus(), internalCallContext);

        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get(0).getType(), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(result.get(0).getEffectiveDate().compareTo(createDate), 0);
        Assert.assertEquals(result.get(0).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(0).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        Assert.assertEquals(result.get(1).getType(), SubscriptionBaseTransitionType.PHASE);
        Assert.assertEquals(result.get(1).getEffectiveDate().compareTo(evergreenPhaseDate), 0);
        Assert.assertEquals(result.get(1).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(1).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        // Cancel event
        Assert.assertEquals(result.get(2).getType(), SubscriptionBaseTransitionType.CANCEL);
        Assert.assertEquals(result.get(2).getEffectiveDate().compareTo(cancelDate), 0);
        Assert.assertNull(result.get(2).getPlan());

        // Nothing after cancel -> we correctly discarded subsequent catalog update events after the cancel

    }

    @Test(groups = "fast")
    public void testWithCancelation_After_EffSubDtV2() throws Exception {

        final DateTime createDate = new DateTime(2011, 1, 2, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(createDate), subscriptionBaseApiService, clock);

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("gold-monthly")
                                                                .setEventPlanPhase("gold-monthly-trial")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(createDate)
                                                                .setEffectiveDate(createDate)
                                                                .setTotalOrdering(1)
                                                                .setActive(true)));

        final DateTime evergreenPhaseDate = createDate.plusDays(30);
        inputEvents.add(new PhaseEventData(new PhaseEventBuilder().setPhaseName("gold-monthly-evergreen")
                                                                  .setUuid(UUID.randomUUID())
                                                                  .setSubscriptionId(subscriptionId)
                                                                  .setCreatedDate(evergreenPhaseDate)
                                                                  .setUpdatedDate(evergreenPhaseDate)
                                                                  .setEffectiveDate(evergreenPhaseDate)
                                                                  .setTotalOrdering(2)
                                                                  .setActive(true)));

        final DateTime cancelDate = new DateTime(2011, 2, 15, 0, 0, DateTimeZone.UTC);

        inputEvents.add(new ApiEventCancel(new ApiEventBuilder().setApiEventType(ApiEventType.CANCEL)
                                                                .setEventPlan(null)
                                                                .setEventPlanPhase(null)
                                                                .setEventPriceList(null)
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(cancelDate)
                                                                .setTotalOrdering(3)
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);

        final List<SubscriptionBillingEvent> result = subscriptionBase.getSubscriptionBillingEvents(catalog.getCatalog(), subscriptionCatalogApi.getPriceOverrideSvcStatus(), internalCallContext);

        Assert.assertEquals(result.size(), 5);
        Assert.assertEquals(result.get(0).getType(), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(result.get(0).getEffectiveDate().compareTo(createDate), 0);
        Assert.assertEquals(result.get(0).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(0).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        Assert.assertEquals(result.get(1).getType(), SubscriptionBaseTransitionType.PHASE);
        Assert.assertEquals(result.get(1).getEffectiveDate().compareTo(evergreenPhaseDate), 0);
        Assert.assertEquals(result.get(1).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(1).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        // Catalog change event for EFF_SUB_DT_V2
        Assert.assertEquals(result.get(2).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(2).getEffectiveDate().toLocalDate().compareTo(EFF_SUB_DT_V2.toLocalDate()), 0);
        Assert.assertEquals(result.get(2).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(2).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V2), 0);

        // Catalog change event for EFF_SUB_DT_V3
        Assert.assertEquals(result.get(3).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(3).getEffectiveDate().toLocalDate().compareTo(EFF_SUB_DT_V3.toLocalDate()), 0);
        Assert.assertEquals(result.get(3).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(3).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V3), 0);

        // Cancel event
        Assert.assertEquals(result.get(4).getType(), SubscriptionBaseTransitionType.CANCEL);
        Assert.assertEquals(result.get(4).getEffectiveDate().compareTo(cancelDate), 0);
        Assert.assertNull(result.get(4).getPlan());

        // Nothing after cancel -> we correctly discarded subsequent catalog update events after the cancel
    }

    @Test(groups = "fast")
    public void testWithChange_Before_EffSubDtV2() throws Exception {

        final DateTime createDate = new DateTime(2011, 1, 2, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(createDate), subscriptionBaseApiService, clock);

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("gold-monthly")
                                                                .setEventPlanPhase("gold-monthly-trial")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(createDate)
                                                                .setEffectiveDate(createDate)
                                                                .setTotalOrdering(1)
                                                                .setActive(true)));

        final DateTime evergreenPhaseDate = createDate.plusDays(30);
        inputEvents.add(new PhaseEventData(new PhaseEventBuilder().setPhaseName("gold-monthly-evergreen")
                                                                  .setUuid(UUID.randomUUID())
                                                                  .setSubscriptionId(subscriptionId)
                                                                  .setCreatedDate(evergreenPhaseDate)
                                                                  .setUpdatedDate(evergreenPhaseDate)
                                                                  .setEffectiveDate(evergreenPhaseDate)
                                                                  .setTotalOrdering(2)
                                                                  .setActive(true)));

        final DateTime changeDate = new DateTime(2011, 2, 13, 0, 0, DateTimeZone.UTC);

        inputEvents.add(new ApiEventChange(new ApiEventBuilder().setApiEventType(ApiEventType.CHANGE)
                                                                .setEventPlan("silver-monthly")
                                                                .setEventPlanPhase("silver-monthly-evergreen")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(changeDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(changeDate)
                                                                .setTotalOrdering(3)
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);

        final List<SubscriptionBillingEvent> result = subscriptionBase.getSubscriptionBillingEvents(catalog.getCatalog(), subscriptionCatalogApi.getPriceOverrideSvcStatus(), internalCallContext);

        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get(0).getType(), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(result.get(0).getEffectiveDate().compareTo(createDate), 0);
        Assert.assertEquals(result.get(0).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(0).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        Assert.assertEquals(result.get(1).getType(), SubscriptionBaseTransitionType.PHASE);
        Assert.assertEquals(result.get(1).getEffectiveDate().compareTo(evergreenPhaseDate), 0);
        Assert.assertEquals(result.get(1).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(1).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        // User CHANGE event
        Assert.assertEquals(result.get(2).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(2).getEffectiveDate().compareTo(changeDate), 0);
        Assert.assertEquals(result.get(2).getPlan().getName().compareTo("silver-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(2).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V3), 0);

        // We should not see any catalog CHANGE events
    }


    @Test(groups = "fast")
    public void testWithChange_After_EffSubDtV3() throws Exception {

        final DateTime createDate = new DateTime(2011, 1, 2, 0, 0, DateTimeZone.UTC);
        final DefaultSubscriptionBase subscriptionBase = new DefaultSubscriptionBase(new SubscriptionBuilder().setAlignStartDate(createDate), subscriptionBaseApiService, clock);

        final UUID subscriptionId = UUID.randomUUID();
        final List<SubscriptionBaseEvent> inputEvents = new LinkedList<SubscriptionBaseEvent>();
        inputEvents.add(new ApiEventCreate(new ApiEventBuilder().setApiEventType(CREATE)
                                                                .setEventPlan("gold-monthly")
                                                                .setEventPlanPhase("gold-monthly-trial")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(createDate)
                                                                .setUpdatedDate(createDate)
                                                                .setEffectiveDate(createDate)
                                                                .setTotalOrdering(1)
                                                                .setActive(true)));

        final DateTime evergreenPhaseDate = createDate.plusDays(30);
        inputEvents.add(new PhaseEventData(new PhaseEventBuilder().setPhaseName("gold-monthly-evergreen")
                                                                  .setUuid(UUID.randomUUID())
                                                                  .setSubscriptionId(subscriptionId)
                                                                  .setCreatedDate(evergreenPhaseDate)
                                                                  .setUpdatedDate(evergreenPhaseDate)
                                                                  .setEffectiveDate(evergreenPhaseDate)
                                                                  .setTotalOrdering(2)
                                                                  .setActive(true)));

        final DateTime changeDate = new DateTime(2011, 2, 15, 0, 0, DateTimeZone.UTC);

        inputEvents.add(new ApiEventChange(new ApiEventBuilder().setApiEventType(ApiEventType.CHANGE)
                                                                .setEventPlan("silver-monthly")
                                                                .setEventPlanPhase("silver-monthly-evergreen")
                                                                .setEventPriceList("DEFAULT")
                                                                .setFromDisk(true)
                                                                .setUuid(UUID.randomUUID())
                                                                .setSubscriptionId(subscriptionId)
                                                                .setCreatedDate(changeDate)
                                                                .setUpdatedDate(null)
                                                                .setEffectiveDate(changeDate)
                                                                .setTotalOrdering(3)
                                                                .setActive(true)));
        subscriptionBase.rebuildTransitions(inputEvents, catalog);

        final List<SubscriptionBillingEvent> result = subscriptionBase.getSubscriptionBillingEvents(catalog.getCatalog(), subscriptionCatalogApi.getPriceOverrideSvcStatus(), internalCallContext);

        Assert.assertEquals(result.size(), 5);
        Assert.assertEquals(result.get(0).getType(), SubscriptionBaseTransitionType.CREATE);
        Assert.assertEquals(result.get(0).getEffectiveDate().compareTo(createDate), 0);
        Assert.assertEquals(result.get(0).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(0).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        Assert.assertEquals(result.get(1).getType(), SubscriptionBaseTransitionType.PHASE);
        Assert.assertEquals(result.get(1).getEffectiveDate().compareTo(evergreenPhaseDate), 0);
        Assert.assertEquals(result.get(1).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(1).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V1), 0);

        // Catalog change event for EFF_SUB_DT_V2
        Assert.assertEquals(result.get(2).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(2).getEffectiveDate().toLocalDate().compareTo(EFF_SUB_DT_V2.toLocalDate()), 0);
        Assert.assertEquals(result.get(2).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(2).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V2), 0);

        // Catalog change event for EFF_SUB_DT_V3
        Assert.assertEquals(result.get(3).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(3).getEffectiveDate().toLocalDate().compareTo(EFF_SUB_DT_V3.toLocalDate()), 0);
        Assert.assertEquals(result.get(3).getPlan().getName().compareTo("gold-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(3).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V3), 0);

        // User CHANGE event
        Assert.assertEquals(result.get(4).getType(), SubscriptionBaseTransitionType.CHANGE);
        Assert.assertEquals(result.get(4).getEffectiveDate().compareTo(changeDate), 0);
        Assert.assertEquals(result.get(4).getPlan().getName().compareTo("silver-monthly"), 0);
        Assert.assertEquals(toDateTime(result.get(4).getPlan().getCatalog().getEffectiveDate()).compareTo(EFF_V3), 0);

        // We should not see any more catalog CHANGE events
    }



    private static DateTime toDateTime(Date input) {
        return new DateTime(input).toDateTime(DateTimeZone.UTC);
    }

}
