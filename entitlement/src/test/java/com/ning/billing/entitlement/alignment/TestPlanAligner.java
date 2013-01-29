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

package com.ning.billing.entitlement.alignment;

import java.util.List;
import java.util.Map;

import org.joda.time.DateTime;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.ning.billing.KillbillTestSuite;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.io.VersionedCatalogLoader;
import com.ning.billing.entitlement.EntitlementTestSuiteNoDB;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.util.config.CatalogConfig;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventBase;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.DefaultClock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestPlanAligner extends EntitlementTestSuiteNoDB {
    private static final String priceList = PriceListSet.DEFAULT_PRICELIST_NAME;

    private final DefaultClock clock = new DefaultClock();

    private DefaultCatalogService catalogService;
    private PlanAligner planAligner;

    @BeforeClass(groups = "fast")
    public void setUp() throws Exception {
        final VersionedCatalogLoader versionedCatalogLoader = new VersionedCatalogLoader(clock);
        final CatalogConfig config = new ConfigurationObjectFactory(new ConfigSource() {
            final Map<String, String> properties = ImmutableMap.<String, String>of("killbill.catalog.uri", "file:src/test/resources/testInput.xml");

            @Override
            public String getString(final String propertyName) {
                return properties.get(propertyName);
            }
        }).build(CatalogConfig.class);

        catalogService = new DefaultCatalogService(config, versionedCatalogLoader);
        planAligner = new PlanAligner(catalogService);

        catalogService.loadCatalog();
    }

    @Test(groups = "fast")
    public void testCreationBundleAlignment() throws Exception {
        final String productName = "pistol-monthly";
        final PhaseType initialPhase = PhaseType.TRIAL;
        final SubscriptionData subscriptionData = createSubscriptionStartedInThePast(productName, initialPhase);

        // Make the creation effective now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, subscriptionData, effectiveDate);

        // All plans but Laser-Scope are START_OF_BUNDLE aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), subscriptionData.getBundleStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), subscriptionData.getBundleStartDate().plusDays(30));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(subscriptionData, effectiveDate, effectiveDate);
        Assert.assertEquals(nextTimePhase.getStartPhase(), subscriptionData.getBundleStartDate().plusDays(30));

        // Now look at the past, before the bundle started
        final DateTime effectiveDateInThePast = subscriptionData.getBundleStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, subscriptionData, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), subscriptionData.getBundleStartDate());

        // Verify the next phase via the other API
        try {
            planAligner.getNextTimedPhase(subscriptionData, effectiveDateInThePast, effectiveDateInThePast);
            Assert.fail("Can't use getNextTimedPhase(): the effective date is before the initial plan");
        } catch (EntitlementError e) {
            Assert.assertTrue(true);
        }

        // Try a change plan now (simulate an IMMEDIATE policy)
        final String newProductName = "shotgun-monthly";
        final DateTime effectiveChangeDate = clock.getUTCNow();
        changeSubscription(effectiveChangeDate, subscriptionData, productName, newProductName, initialPhase);

        // All non rescue plans are START_OF_SUBSCRIPTION aligned on change
        final TimedPhase newPhase = getNextTimedPhaseOnChange(subscriptionData, newProductName, effectiveChangeDate);
        Assert.assertEquals(newPhase.getStartPhase(), subscriptionData.getStartDate().plusDays(30),
                            String.format("Start phase: %s, but bundle start date: %s and subscription start date: %s",
                                          newPhase.getStartPhase(), subscriptionData.getBundleStartDate(), subscriptionData.getStartDate()));
    }

    @Test(groups = "fast")
    public void testCreationSubscriptionAlignment() throws Exception {
        final String productName = "laser-scope-monthly";
        final PhaseType initialPhase = PhaseType.DISCOUNT;
        final SubscriptionData subscriptionData = createSubscriptionStartedInThePast(productName, initialPhase);

        // Look now, after the bundle and the subscription started
        final DateTime effectiveDate = clock.getUTCNow();
        final TimedPhase[] phases = getTimedPhasesOnCreate(productName, initialPhase, subscriptionData, effectiveDate);

        // Laser-Scope is START_OF_SUBSCRIPTION aligned on creation
        Assert.assertEquals(phases[0].getStartPhase(), subscriptionData.getStartDate());
        Assert.assertEquals(phases[1].getStartPhase(), subscriptionData.getStartDate().plusMonths(1));

        // Verify the next phase via the other API
        final TimedPhase nextTimePhase = planAligner.getNextTimedPhase(subscriptionData, effectiveDate, effectiveDate);
        Assert.assertEquals(nextTimePhase.getStartPhase(), subscriptionData.getStartDate().plusMonths(1));

        // Now look at the past, before the subscription started
        final DateTime effectiveDateInThePast = subscriptionData.getStartDate().minusHours(10);
        final TimedPhase[] phasesInThePast = getTimedPhasesOnCreate(productName, initialPhase, subscriptionData, effectiveDateInThePast);
        Assert.assertNull(phasesInThePast[0]);
        Assert.assertEquals(phasesInThePast[1].getStartPhase(), subscriptionData.getStartDate());

        // Verify the next phase via the other API
        try {
            planAligner.getNextTimedPhase(subscriptionData, effectiveDateInThePast, effectiveDateInThePast);
            Assert.fail("Can't use getNextTimedPhase(): the effective date is before the initial plan");
        } catch (EntitlementError e) {
            Assert.assertTrue(true);
        }

        // Try a change plan (simulate END_OF_TERM policy)
        final String newProductName = "telescopic-scope-monthly";
        final DateTime effectiveChangeDate = subscriptionData.getStartDate().plusMonths(1);
        changeSubscription(effectiveChangeDate, subscriptionData, productName, newProductName, initialPhase);

        // All non rescue plans are START_OF_SUBSCRIPTION aligned on change. Since we're END_OF_TERM here, we'll
        // never see the discount phase of telescopic-scope-monthly and jump right into evergreen.
        // But in this test, since we didn't create the future change event from discount to evergreen (see changeSubscription,
        // the subscription has only two transitions), we'll see null
        final TimedPhase newPhase = getNextTimedPhaseOnChange(subscriptionData, newProductName, effectiveChangeDate);
        Assert.assertNull(newPhase);
    }

    private SubscriptionData createSubscriptionStartedInThePast(final String productName, final PhaseType phaseType) {
        final SubscriptionBuilder builder = new SubscriptionBuilder();
        builder.setBundleStartDate(clock.getUTCNow().minusHours(10));
        // Make sure to set the dates apart
        builder.setAlignStartDate(new DateTime(builder.getBundleStartDate().plusHours(5)));

        // Create the transitions
        final SubscriptionData subscriptionData = new SubscriptionData(builder, null, clock);
        final EntitlementEvent event = createEntitlementEvent(builder.getAlignStartDate(),
                                                              productName,
                                                              phaseType,
                                                              ApiEventType.CREATE,
                                                              subscriptionData.getActiveVersion());
        subscriptionData.rebuildTransitions(ImmutableList.<EntitlementEvent>of(event), catalogService.getFullCatalog());

        Assert.assertEquals(subscriptionData.getAllTransitions().size(), 1);
        Assert.assertNull(subscriptionData.getAllTransitions().get(0).getPreviousPhase());
        Assert.assertNotNull(subscriptionData.getAllTransitions().get(0).getNextPhase());

        return subscriptionData;
    }

    private void changeSubscription(final DateTime effectiveChangeDate,
                                    final SubscriptionData subscriptionData,
                                    final String previousProductName,
                                    final String newProductName,
                                    final PhaseType commonPhaseType) {
        final EntitlementEvent previousEvent = createEntitlementEvent(subscriptionData.getStartDate(),
                                                                      previousProductName,
                                                                      commonPhaseType,
                                                                      ApiEventType.CREATE,
                                                                      subscriptionData.getActiveVersion());
        final EntitlementEvent event = createEntitlementEvent(effectiveChangeDate,
                                                              newProductName,
                                                              commonPhaseType,
                                                              ApiEventType.CHANGE,
                                                              subscriptionData.getActiveVersion());

        subscriptionData.rebuildTransitions(ImmutableList.<EntitlementEvent>of(previousEvent, event), catalogService.getFullCatalog());

        final List<SubscriptionTransitionData> newTransitions = subscriptionData.getAllTransitions();
        Assert.assertEquals(newTransitions.size(), 2);
        Assert.assertNull(newTransitions.get(0).getPreviousPhase());
        Assert.assertEquals(newTransitions.get(0).getNextPhase(), newTransitions.get(1).getPreviousPhase());
        Assert.assertNotNull(newTransitions.get(1).getNextPhase());
    }

    private EntitlementEvent createEntitlementEvent(final DateTime effectiveDate,
                                                    final String productName,
                                                    final PhaseType phaseType,
                                                    final ApiEventType apiEventType,
                                                    final long activeVersion) {
        final ApiEventBuilder eventBuilder = new ApiEventBuilder();
        eventBuilder.setEffectiveDate(effectiveDate);
        eventBuilder.setEventPlan(productName);
        eventBuilder.setEventPlanPhase(productName + "-" + phaseType.toString().toLowerCase());
        eventBuilder.setEventPriceList(priceList);

        // We don't really use the following but the code path requires it
        eventBuilder.setRequestedDate(effectiveDate);
        eventBuilder.setFromDisk(true);
        eventBuilder.setActiveVersion(activeVersion);

        return new ApiEventBase(eventBuilder.setEventType(apiEventType));
    }

    private TimedPhase getNextTimedPhaseOnChange(final SubscriptionData subscriptionData,
                                                 final String newProductName,
                                                 final DateTime effectiveChangeDate) throws CatalogApiException, EntitlementUserApiException {
        // The date is used for different catalog versions - we don't care here
        final Plan newPlan = catalogService.getFullCatalog().findPlan(newProductName, clock.getUTCNow());

        return planAligner.getNextTimedPhaseOnChange(subscriptionData, newPlan, priceList, effectiveChangeDate, effectiveChangeDate);
    }

    private TimedPhase[] getTimedPhasesOnCreate(final String productName,
                                                final PhaseType initialPhase,
                                                final SubscriptionData subscriptionData,
                                                final DateTime effectiveDate) throws CatalogApiException, EntitlementUserApiException {
        // The date is used for different catalog versions - we don't care here
        final Plan plan = catalogService.getFullCatalog().findPlan(productName, clock.getUTCNow());

        // Same here for the requested date
        final TimedPhase[] phases = planAligner.getCurrentAndNextTimedPhaseOnCreate(subscriptionData, plan, initialPhase, priceList, clock.getUTCNow(), effectiveDate);
        Assert.assertEquals(phases.length, 2);

        return phases;
    }
}
