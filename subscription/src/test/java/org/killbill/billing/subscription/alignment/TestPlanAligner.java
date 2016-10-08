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

package org.killbill.billing.subscription.alignment;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.user.ApiEventBase;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class TestPlanAligner extends SubscriptionTestSuiteNoDB {

    private static final String priceList = PriceListSet.DEFAULT_PRICELIST_NAME;

    private final DefaultClock clock = new DefaultClock();

    private PlanAligner planAligner;

    @Override
    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        planAligner = new PlanAligner(catalogService);

    }

    @Test(groups = "fast")
    public void testCreationBundleAlignment() throws Exception {
        final String productName = "pistol-monthly";
        final PhaseType initialPhase = PhaseType.TRIAL;
        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionStartedInThePast(productName, initialPhase);

        // Make the creation effective now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDate);

        // All plans but Laser-Scope are START_OF_BUNDLE aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), defaultSubscriptionBase.getBundleStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), defaultSubscriptionBase.getBundleStartDate().plusDays(30));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDate, internalCallContext);
        Assert.assertEquals(nextTimePhase.getStartPhase(), defaultSubscriptionBase.getBundleStartDate().plusDays(30));

        // Now look at the past, before the bundle started
        final DateTime effectiveDateInThePast = defaultSubscriptionBase.getBundleStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), defaultSubscriptionBase.getBundleStartDate());

        // Verify the next phase via the other API
        try {
            planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDateInThePast, internalCallContext);
            Assert.fail("Can't use getNextTimedPhase(): the effective date is before the initial plan");
        } catch (SubscriptionBaseError e) {
            Assert.assertTrue(true);
        }

        // Try a change plan now (simulate an IMMEDIATE policy)
        final String newProductName = "shotgun-monthly";
        final DateTime effectiveChangeDate = clock.getUTCNow();
        changeSubscription(effectiveChangeDate, defaultSubscriptionBase, productName, newProductName, initialPhase);

        // All non rescue plans are START_OF_SUBSCRIPTION aligned on change
        final TimedPhase newPhase = getNextTimedPhaseOnChange(defaultSubscriptionBase, newProductName, effectiveChangeDate);
        Assert.assertEquals(newPhase.getStartPhase(), defaultSubscriptionBase.getStartDate().plusDays(30),
                            String.format("Start phase: %s, but bundle start date: %s and subscription start date: %s",
                                          newPhase.getStartPhase(), defaultSubscriptionBase.getBundleStartDate(), defaultSubscriptionBase.getStartDate())
                           );
    }

    @Test(groups = "fast")
    public void testCreationSubscriptionAlignment() throws Exception {
        final String productName = "laser-scope-monthly";
        final PhaseType initialPhase = PhaseType.DISCOUNT;
        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionStartedInThePast(productName, initialPhase);

        // Look now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDate);

        // Laser-Scope is START_OF_SUBSCRIPTION aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), defaultSubscriptionBase.getStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), defaultSubscriptionBase.getStartDate().plusMonths(1));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDate, internalCallContext);
        Assert.assertEquals(nextTimePhase.getStartPhase(), defaultSubscriptionBase.getStartDate().plusMonths(1));

        // Now look at the past, before the subscription started
        final DateTime effectiveDateInThePast = defaultSubscriptionBase.getStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), defaultSubscriptionBase.getStartDate());

        // Verify the next phase via the other API
        try {
            planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDateInThePast, internalCallContext);
            Assert.fail("Can't use getNextTimedPhase(): the effective date is before the initial plan");
        } catch (SubscriptionBaseError e) {
            Assert.assertTrue(true);
        }

        // Try a change plan (simulate END_OF_TERM policy)
        final String newProductName = "telescopic-scope-monthly";
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusMonths(1);
        changeSubscription(effectiveChangeDate, defaultSubscriptionBase, productName, newProductName, initialPhase);

        // All non rescue plans are START_OF_SUBSCRIPTION aligned on change. Since we're END_OF_TERM here, we'll
        // never see the discount phase of telescopic-scope-monthly and jump right into evergreen.
        // But in this test, since we didn't create the future change event from discount to evergreen (see changeSubscription,
        // the subscription has only two transitions), we'll see null
        final TimedPhase newPhase = getNextTimedPhaseOnChange(defaultSubscriptionBase, newProductName, effectiveChangeDate);
        Assert.assertNull(newPhase);
    }

    private DefaultSubscriptionBase createSubscriptionStartedInThePast(final String productName, final PhaseType phaseType) throws CatalogApiException {
        final SubscriptionBuilder builder = new SubscriptionBuilder();
        builder.setBundleStartDate(clock.getUTCNow().minusHours(10));
        // Make sure to set the dates apart
        builder.setAlignStartDate(new DateTime(builder.getBundleStartDate().plusHours(5)));

        // Create the transitions
        final DefaultSubscriptionBase defaultSubscriptionBase = new DefaultSubscriptionBase(builder, null, clock);
        final SubscriptionBaseEvent event = createSubscriptionEvent(builder.getAlignStartDate(),
                                                                    productName,
                                                                    phaseType,
                                                                    ApiEventType.CREATE
                                                                   );
        final List<SubscriptionBaseEvent> events = new ArrayList<SubscriptionBaseEvent>();
        events.add(event);
        defaultSubscriptionBase.rebuildTransitions(events, catalogService.getFullCatalog(true, true, internalCallContext));

        Assert.assertEquals(defaultSubscriptionBase.getAllTransitions().size(), 1);
        Assert.assertNull(defaultSubscriptionBase.getAllTransitions().get(0).getPreviousPhase());
        Assert.assertNotNull(defaultSubscriptionBase.getAllTransitions().get(0).getNextPhase());

        return defaultSubscriptionBase;
    }

    private void changeSubscription(final DateTime effectiveChangeDate,
                                    final DefaultSubscriptionBase defaultSubscriptionBase,
                                    final String previousProductName,
                                    final String newProductName,
                                    final PhaseType commonPhaseType) throws CatalogApiException {
        final SubscriptionBaseEvent previousEvent = createSubscriptionEvent(defaultSubscriptionBase.getStartDate(),
                                                                            previousProductName,
                                                                            commonPhaseType,
                                                                            ApiEventType.CREATE
                                                                           );
        final SubscriptionBaseEvent event = createSubscriptionEvent(effectiveChangeDate,
                                                                    newProductName,
                                                                    commonPhaseType,
                                                                    ApiEventType.CHANGE
                                                                   );

        final List<SubscriptionBaseEvent> events = new ArrayList<SubscriptionBaseEvent>();
        events.add(previousEvent);
        events.add(event);

        defaultSubscriptionBase.rebuildTransitions(events, catalogService.getFullCatalog(true, true, internalCallContext));

        final List<SubscriptionBaseTransition> newTransitions = defaultSubscriptionBase.getAllTransitions();
        Assert.assertEquals(newTransitions.size(), 2);
        Assert.assertNull(newTransitions.get(0).getPreviousPhase());
        Assert.assertEquals(newTransitions.get(0).getNextPhase(), newTransitions.get(1).getPreviousPhase());
        Assert.assertNotNull(newTransitions.get(1).getNextPhase());
    }

    private SubscriptionBaseEvent createSubscriptionEvent(final DateTime effectiveDate,
                                                          final String productName,
                                                          final PhaseType phaseType,
                                                          final ApiEventType apiEventType) {
        final ApiEventBuilder eventBuilder = new ApiEventBuilder();
        eventBuilder.setEffectiveDate(effectiveDate);
        eventBuilder.setEventPlan(productName);
        eventBuilder.setEventPlanPhase(productName + "-" + phaseType.toString().toLowerCase());
        eventBuilder.setEventPriceList(priceList);

        // We don't really use the following but the code path requires it
        eventBuilder.setFromDisk(true);

        return new ApiEventBase(eventBuilder.setApiEventType(apiEventType));
    }

    private TimedPhase getNextTimedPhaseOnChange(final DefaultSubscriptionBase defaultSubscriptionBase,
                                                 final String newProductName,
                                                 final DateTime effectiveChangeDate) throws CatalogApiException, SubscriptionBaseApiException {
        // The date is used for different catalog versions - we don't care here
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());

        return planAligner.getNextTimedPhaseOnChange(defaultSubscriptionBase, newPlan, priceList, effectiveChangeDate, internalCallContext);
    }

    private TimedPhase[] getTimedPhasesOnCreate(final String productName,
                                                final PhaseType initialPhase,
                                                final DefaultSubscriptionBase defaultSubscriptionBase,
                                                final DateTime effectiveDate) throws CatalogApiException, SubscriptionBaseApiException {
        // The date is used for different catalog versions - we don't care here
        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());

        // Same here for the requested date
        final TimedPhase[] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(defaultSubscriptionBase.getAlignStartDate(), defaultSubscriptionBase.getBundleStartDate(),
                                                                                    plan, initialPhase, priceList, effectiveDate, internalCallContext);
        Assert.assertEquals(phases.length, 2);

        return phases;
    }
}
