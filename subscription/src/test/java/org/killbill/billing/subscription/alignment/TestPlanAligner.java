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
        if (hasFailed()) {
            return;
        }

        super.beforeClass();
        planAligner = new PlanAligner();

    }

    @Test(groups = "fast")
    public void testCreationBundleAlignment() throws Exception {
        final String productName = "pistol-monthly";
        final PhaseType initialPhase = PhaseType.TRIAL;

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now.minusHours(10);
        final DateTime alignStartDate = bundleStartDate.plusHours(5);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, initialPhase);

        // Make the creation effective now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDate);

        // All plans but Laser-Scope are START_OF_BUNDLE aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), defaultSubscriptionBase.getBundleStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), defaultSubscriptionBase.getBundleStartDate().plusDays(30));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDate, catalog, internalCallContext);
        Assert.assertEquals(nextTimePhase.getStartPhase(), defaultSubscriptionBase.getBundleStartDate().plusDays(30));

        // Now look at the past, before the bundle started
        final DateTime effectiveDateInThePast = defaultSubscriptionBase.getBundleStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), defaultSubscriptionBase.getBundleStartDate());

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

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now.minusHours(10);
        final DateTime alignStartDate = bundleStartDate.plusHours(5);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, initialPhase);

        // Look now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDate);

        // Laser-Scope is START_OF_SUBSCRIPTION aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), defaultSubscriptionBase.getStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), defaultSubscriptionBase.getStartDate().plusMonths(1));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(defaultSubscriptionBase, effectiveDate, catalog, internalCallContext);
        Assert.assertEquals(nextTimePhase.getStartPhase(), defaultSubscriptionBase.getStartDate().plusMonths(1));

        // Now look at the past, before the subscription started
        final DateTime effectiveDateInThePast = defaultSubscriptionBase.getStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, defaultSubscriptionBase, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), defaultSubscriptionBase.getStartDate());

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


    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION after skipping TRIAL on Create to a new Plan that only has EVERGREEN
    //
    @Test(groups = "fast")
    public void testCreateWithTargetPhaseType1() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, PhaseType.EVERGREEN, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertNull(phases[1]);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.EVERGREEN);

        final String newProductName = "pistol-monthly-notrial";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusMonths(15);

        final TimedPhase currentPhase  = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);
    }


    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION after skipping TRIAL on Create to a new Plan that has {TRIAL, EVERGREEN}
    //
    @Test(groups = "fast")
    public void testCreateWithTargetPhaseType2() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, PhaseType.EVERGREEN, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertNull(phases[1]);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.EVERGREEN);

        final String newProductName = "shotgun-monthly";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusMonths(15);

        final TimedPhase currentPhase  = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);
    }


    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION after skipping TRIAL on Create to a new Plan that has {TRIAL, DISCOUNT, EVERGREEN}
    //
    @Test(groups = "fast")
    public void testCreateWithTargetPhaseType3() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, PhaseType.EVERGREEN, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertNull(phases[1]);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.EVERGREEN);

        final String newProductName = "assault-rifle-annual-gunclub-discount";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusMonths(15);

        // Because new Plan has an EVERGREEN PhaseType we end up directly on that PhaseType
        final TimedPhase currentPhase  = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);

    }

    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION after skipping TRIAL on Create to a new Plan that has {DISCOUNT, EVERGREEN}
    //
    @Test(groups = "fast")
    public void testCreateWithTargetPhaseType4() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, PhaseType.EVERGREEN, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertNull(phases[1]);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.EVERGREEN);

        final String newProductName = "pistol-annual-gunclub-discount-notrial";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusMonths(15);

        // Because new Plan has an EVERGREEN PhaseType we end up directly on that PhaseType
        final TimedPhase currentPhase  = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);
    }

    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION after skipping TRIAL on Create to a new Plan that has {TRIAL, FIXEDTERM}
    //
    @Test(groups = "fast")
    public void testCreateWithTargetPhaseType5() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, PhaseType.EVERGREEN, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertNull(phases[1]);

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.EVERGREEN);

        final String newProductName = "pistol-monthly-fixedterm";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusDays(5);

        final TimedPhase currentPhase = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);

        // Initial phase EVERGREEN does not exist in the new Plan so we ignore the original skipped Phase and proceed with default alignment (we only move the clock 5 days so we are still in TRIAL)
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.TRIAL);

        final TimedPhase nextPhase  = planAligner.getNextTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
        Assert.assertEquals(nextPhase.getStartPhase(), alignStartDate.plusDays(30));
        Assert.assertEquals(nextPhase.getPhase().getPhaseType(), PhaseType.FIXEDTERM);
    }


    //
    // Scenario : change Plan with START_OF_SUBSCRIPTION to a new Plan that has {TRIAL, DISCOUNT, EVERGREEN} and specifying a target PhaseType = DISCOUNT
    //
    @Test(groups = "fast")
    public void testChangeWithTargetPhaseType1() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, null, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.TRIAL);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertEquals(phases[1].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[1].getStartPhase(), now.plusDays(30));

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.TRIAL);

        final String newProductName = "pistol-annual-gunclub-discount";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusDays(5);

        final TimedPhase currentPhase = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, PhaseType.DISCOUNT, catalog, internalCallContext);

        // We end up straight on DISCOUNT but because we are using START_OF_SUBSCRIPTION alignment, such Phase starts with beginning of subscription
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate);
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.DISCOUNT);

        final TimedPhase nextPhase  = planAligner.getNextTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, PhaseType.DISCOUNT, catalog, internalCallContext);
        Assert.assertEquals(nextPhase.getStartPhase(), alignStartDate.plusMonths(6));
        Assert.assertEquals(nextPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);
    }

    //
    // Scenario : change Plan with CHANGE_OF_PLAN to a new Plan that has {DISCOUNT, EVERGREEN} and specifying a target PhaseType = EVERGREEN
    //
    @Test(groups = "fast")
    public void testChangeWithTargetPhaseType2() throws Exception {
        final String productName = "pistol-monthly";

        final DateTime now = clock.getUTCNow();
        final DateTime bundleStartDate = now;
        final DateTime alignStartDate = bundleStartDate;

        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());
        final TimedPhase [] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(alignStartDate, bundleStartDate, plan, null, PriceListSet.DEFAULT_PRICELIST_NAME, now, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);
        Assert.assertEquals(phases[0].getPhase().getPhaseType(), PhaseType.TRIAL);
        Assert.assertEquals(phases[0].getStartPhase(), now);
        Assert.assertEquals(phases[1].getPhase().getPhaseType(), PhaseType.EVERGREEN);
        Assert.assertEquals(phases[1].getStartPhase(), now.plusDays(30));

        final DefaultSubscriptionBase defaultSubscriptionBase = createSubscription(bundleStartDate, alignStartDate, productName, PhaseType.TRIAL);

        final String newProductName = "assault-rifle-annual-rescue";
        final Plan newPlan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(newProductName, clock.getUTCNow());
        final DateTime effectiveChangeDate = defaultSubscriptionBase.getStartDate().plusDays(5);

        final TimedPhase currentPhase = planAligner.getCurrentTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, PhaseType.EVERGREEN, catalog, internalCallContext);

        // We end up straight on EVERGREEN Phase and because we are CHANGE_OF_PLAN aligned the start is at the effective date of the change
        Assert.assertEquals(currentPhase.getStartPhase(), alignStartDate.plusDays(5));
        Assert.assertEquals(currentPhase.getPhase().getPhaseType(), PhaseType.EVERGREEN);

        final TimedPhase nextPhase  = planAligner.getNextTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, PhaseType.EVERGREEN, catalog, internalCallContext);
        Assert.assertNull(nextPhase);
    }


    private DefaultSubscriptionBase createSubscription(final DateTime bundleStartDate, final DateTime alignStartDate, final String productName, final PhaseType phaseType) throws CatalogApiException {
        final SubscriptionBuilder builder = new SubscriptionBuilder();
        builder.setBundleStartDate(bundleStartDate);
        // Make sure to set the dates apart
        builder.setAlignStartDate(new DateTime(alignStartDate));

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

        return planAligner.getNextTimedPhaseOnChange(defaultSubscriptionBase, newPlan, effectiveChangeDate, null, catalog, internalCallContext);
    }

    private TimedPhase[] getTimedPhasesOnCreate(final String productName,
                                                final PhaseType initialPhase,
                                                final DefaultSubscriptionBase defaultSubscriptionBase,
                                                final DateTime effectiveDate) throws CatalogApiException, SubscriptionBaseApiException {
        // The date is used for different catalog versions - we don't care here
        final Plan plan = catalogService.getFullCatalog(true, true, internalCallContext).findPlan(productName, clock.getUTCNow());

        // Same here for the requested date
        final TimedPhase[] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(defaultSubscriptionBase.getAlignStartDate(), defaultSubscriptionBase.getBundleStartDate(),
                                                                                    plan, initialPhase, priceList, effectiveDate, catalog, internalCallContext);
        Assert.assertEquals(phases.length, 2);

        return phases;
    }
}
