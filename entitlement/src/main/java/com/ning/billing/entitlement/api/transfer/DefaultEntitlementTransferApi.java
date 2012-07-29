/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.entitlement.api.transfer;


import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventCreate;
import com.ning.billing.entitlement.events.user.ApiEventTransfer;

import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;

public class DefaultEntitlementTransferApi implements EntitlementTransferApi {


    private final Clock clock;
    private final EntitlementDao dao;
    private final CatalogService catalogService;
    private final SubscriptionFactory subscriptionFactory;
    private final EntitlementTimelineApi timelineApi;

    @Inject
    public DefaultEntitlementTransferApi(final Clock clock, final EntitlementDao dao, final EntitlementTimelineApi timelineApi, final CatalogService catalogService,
            final SubscriptionFactory subscriptionFactory) {
        this.clock = clock;
        this.dao = dao;
        this.catalogService = catalogService;
        this.subscriptionFactory = subscriptionFactory;
        this.timelineApi = timelineApi;
    }


    private EntitlementEvent createEvent(final boolean firstEvent, final ExistingEvent existingEvent, final SubscriptionData subscription, final DateTime transferDate, final CallContext context)
    throws CatalogApiException {

        EntitlementEvent newEvent = null;

        final Catalog catalog = catalogService.getFullCatalog();


        final DateTime effectiveDate = existingEvent.getEffectiveDate().isBefore(transferDate) ? transferDate : existingEvent.getEffectiveDate();

        // STEPH Issue with catalog for NO_BILLING_PERIOD
        /*
        final PlanPhaseSpecifier spec = existingEvent.getPlanPhaseSpecifier();
        final Plan currentPlan = catalog.findPlan(spec.getProductName(), spec.getBillingPeriod(), spec.getPriceListName(), existingEvent.getEffectiveDate());
        PlanPhase currentPhase = null;
        for (PlanPhase curPhase : currentPlan.getAllPhases()) {
            if (curPhase.getPhaseType() == spec.getPhaseType()) {
                currentPhase = curPhase;
                break;
            }
        }

        *
        */

        final PlanPhaseSpecifier spec = existingEvent.getPlanPhaseSpecifier();
        final PlanPhase currentPhase = catalog.findPhase(existingEvent.getPlanPhaseName(), effectiveDate, subscription.getAlignStartDate());


        final ApiEventBuilder apiBuilder = new ApiEventBuilder()
        .setSubscriptionId(subscription.getId())
        .setEventPlan(currentPhase.getPlan().getName())
        .setEventPlanPhase(currentPhase.getName())
        .setEventPriceList(spec.getPriceListName())
        .setActiveVersion(subscription.getActiveVersion())
        .setProcessedDate(clock.getUTCNow())
        .setEffectiveDate(effectiveDate)
        .setRequestedDate(effectiveDate)
        .setUserToken(context.getUserToken())
        .setFromDisk(true);

        switch(existingEvent.getSubscriptionTransitionType()) {
        case MIGRATE_ENTITLEMENT:
        case RE_CREATE:
        case CREATE:
            newEvent = new ApiEventTransfer(apiBuilder);
            break;

        // Should we even keep future change events; product question really
        case CHANGE:
            newEvent = firstEvent ? new ApiEventTransfer(apiBuilder) : new ApiEventChange(apiBuilder);
            break;

        case PHASE:
            newEvent = firstEvent ? new ApiEventTransfer(apiBuilder) :
                    PhaseEventData.createNextPhaseEvent(currentPhase.getName(), subscription, clock.getUTCNow(), effectiveDate);
            break;

        // Ignore
        case CANCEL:
        case UNCANCEL:
        case MIGRATE_BILLING:
            break;
        default:
            throw new EntitlementError(String.format("Unepxected transitionType %s", existingEvent.getSubscriptionTransitionType()));
        }
        return newEvent;
    }


    private List<EntitlementEvent> toEvents(final List<ExistingEvent> existingEvents, final SubscriptionData subscription,
            final DateTime transferDate, final CallContext context) throws EntitlementTransferApiException {

        try {
            final List<EntitlementEvent> result = new LinkedList<EntitlementEvent>();

            EntitlementEvent event = null;
            ExistingEvent prevEvent = null;
            boolean firstEvent = true;
            for (ExistingEvent cur : existingEvents) {
                // Skip all events prior to the transferDate
                if (cur.getEffectiveDate().isBefore(transferDate)) {
                    prevEvent = cur;
                    continue;
                }

                // Add previous event the first time if needed
                if (prevEvent != null) {
                    event = createEvent(firstEvent, prevEvent, subscription, transferDate, context);
                    if (event != null) {
                        result.add(event);
                        firstEvent = false;
                    }
                    prevEvent = null;
                }

                event = createEvent(firstEvent, cur, subscription, transferDate, context);
                if (event != null) {
                    result.add(event);
                    firstEvent = false;
                }
            }

            // Previous loop did not get anything because transferDate is greater than effectiveDate of last event
            if (prevEvent != null) {
                event = createEvent(firstEvent, prevEvent, subscription, transferDate, context);
                if (event != null) {
                    result.add(event);
                }
                prevEvent = null;
            }


            return result;
        } catch (CatalogApiException e) {
            throw new EntitlementTransferApiException(e);
        }
    }


    @Override
    public void transferBundle(final UUID sourceAccountId, final UUID destAccountId,
            final String bundleKey, final DateTime transferDate, final boolean transferAddOn,
            final CallContext context) throws EntitlementTransferApiException {

        try {

            final SubscriptionBundle bundle = dao.getSubscriptionBundleFromAccountAndKey(sourceAccountId, bundleKey);
            if (bundle == null) {
                throw new EntitlementTransferApiException(ErrorCode.ENT_CREATE_NO_BUNDLE, bundleKey);
            }

            // Get the bundle timeline for the old account
            final BundleTimeline bundleTimeline = timelineApi.getBundleTimeline(bundle);

            final SubscriptionBundleData subscriptionBundleData = new SubscriptionBundleData(bundleKey, destAccountId, transferDate);
            final List<SubscriptionMigrationData> subscriptionMigrationDataList = new LinkedList<SubscriptionMigrationData>();

            final List<TransferCancelData> transferCancelDataList = new LinkedList<TransferCancelData>();

            DateTime bundleStartdate = null;


            for (SubscriptionTimeline cur : bundleTimeline.getSubscriptions()) {

                final SubscriptionData oldSubscription = (SubscriptionData) dao.getSubscriptionFromId(subscriptionFactory, cur.getId());
                final List<ExistingEvent> existingEvents = cur.getExistingEvents();
                final ProductCategory productCategory = existingEvents.get(0).getPlanPhaseSpecifier().getProductCategory();
                if (productCategory == ProductCategory.ADD_ON) {
                    if (!transferAddOn) {
                        continue;
                    }
                } else {



                    // If BP or STANDALONE subscription, create the cancel event on effectiveCancelDate
                    final DateTime effectiveCancelDate = oldSubscription.getChargedThroughDate() != null && transferDate.isBefore(oldSubscription.getChargedThroughDate()) ?
                            oldSubscription.getChargedThroughDate() : transferDate;

                            final EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                            .setSubscriptionId(cur.getId())
                            .setActiveVersion(cur.getActiveVersion())
                            .setProcessedDate(clock.getUTCNow())
                            .setEffectiveDate(effectiveCancelDate)
                            .setRequestedDate(transferDate)
                            .setUserToken(context.getUserToken())
                            .setFromDisk(true));

                            TransferCancelData cancelData =  new TransferCancelData(oldSubscription, cancelEvent);
                            transferCancelDataList.add(cancelData);
                }

                // We Align with the original subscription
                final DateTime subscriptionAlignStartDate = oldSubscription.getAlignStartDate();
                if (bundleStartdate == null) {
                    bundleStartdate = oldSubscription.getStartDate();
                }

                // Create the new subscription for the new bundle on the new account
                final SubscriptionData subscriptionData = subscriptionFactory.createSubscription(new SubscriptionBuilder()
                .setId(UUID.randomUUID())
                .setBundleId(subscriptionBundleData.getId())
                .setCategory(productCategory)
                .setBundleStartDate(transferDate)
                .setAlignStartDate(subscriptionAlignStartDate),
                ImmutableList.<EntitlementEvent>of());

                final List<EntitlementEvent> events = toEvents(existingEvents, subscriptionData, transferDate, context);
                final SubscriptionMigrationData curData = new SubscriptionMigrationData(subscriptionData, events);
                subscriptionMigrationDataList.add(curData);
            }
            BundleMigrationData bundleMigrationData = new BundleMigrationData(subscriptionBundleData, subscriptionMigrationDataList);

            // Atomically cancel all subscription on old account and create new bundle, subscriptions, events for new account
            dao.transfer(sourceAccountId, destAccountId, bundleMigrationData, transferCancelDataList, context);

        } catch (EntitlementRepairException e) {
            throw new EntitlementTransferApiException(e);
        }
    }
}
