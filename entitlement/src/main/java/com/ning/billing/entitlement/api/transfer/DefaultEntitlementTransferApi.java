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

package com.ning.billing.entitlement.api.transfer;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.EntitlementApiBase;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline;
import com.ning.billing.entitlement.api.timeline.SubscriptionTimeline.ExistingEvent;

import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.phase.PhaseEventData;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventTransfer;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.clock.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultEntitlementTransferApi extends EntitlementApiBase implements EntitlementTransferApi {

    private final CatalogService catalogService;
    private final EntitlementTimelineApi timelineApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultEntitlementTransferApi(final Clock clock, final EntitlementDao dao, final EntitlementTimelineApi timelineApi, final CatalogService catalogService,
                                         final SubscriptionApiService apiService, final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock, catalogService);
        this.catalogService = catalogService;
        this.timelineApi = timelineApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    private EntitlementEvent createEvent(final boolean firstEvent, final ExistingEvent existingEvent, final SubscriptionData subscription, final DateTime transferDate, final CallContext context)
            throws CatalogApiException {

        EntitlementEvent newEvent = null;

        final Catalog catalog = catalogService.getFullCatalog();

        final DateTime effectiveDate = existingEvent.getEffectiveDate().isBefore(transferDate) ? transferDate : existingEvent.getEffectiveDate();

        final PlanPhaseSpecifier spec = existingEvent.getPlanPhaseSpecifier();
        final PlanPhase currentPhase = existingEvent.getPlanPhaseName() != null ? catalog.findPhase(existingEvent.getPlanPhaseName(), effectiveDate, subscription.getAlignStartDate()) : null;

        if (spec == null || currentPhase == null) {
            // Ignore cancellations - we assume that transferred subscriptions should always be active
            return null;
        }
        final ApiEventBuilder apiBuilder = new ApiEventBuilder()
                .setSubscriptionId(subscription.getId())
                .setEventPlan(currentPhase.getPlan().getName())
                .setEventPlanPhase(currentPhase.getName())
                .setEventPriceList(spec.getPriceListName())
                .setActiveVersion(subscription.getActiveVersion())
                .setProcessedDate(clock.getUTCNow())
                .setEffectiveDate(effectiveDate)
                .setRequestedDate(effectiveDate)
                .setFromDisk(true);

        switch (existingEvent.getSubscriptionTransitionType()) {
            case TRANSFER:
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

            // Ignore these events except if it's the first event for the new subscription
            case MIGRATE_BILLING:
                if (firstEvent) {
                    newEvent = new ApiEventTransfer(apiBuilder);
                }
                break;
            case CANCEL:
            case UNCANCEL:
                break;

            default:
                throw new EntitlementError(String.format("Unexpected transitionType %s", existingEvent.getSubscriptionTransitionType()));
        }
        return newEvent;
    }

    @VisibleForTesting
    List<EntitlementEvent> toEvents(final List<ExistingEvent> existingEvents, final SubscriptionData subscription,
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
    public SubscriptionBundle transferBundle(final UUID sourceAccountId, final UUID destAccountId,
                                             final String bundleKey, final DateTime transferDate, final boolean transferAddOn,
                                             final boolean cancelImmediately, final CallContext context) throws EntitlementTransferApiException {
        final InternalCallContext fromInternalCallContext = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
        final InternalCallContext toInternalCallContext = internalCallContextFactory.createInternalCallContext(destAccountId, context);

        try {
            final DateTime effectiveTransferDate = transferDate == null ? clock.getUTCNow() : transferDate;
            if (effectiveTransferDate.isAfter(clock.getUTCNow())) {
                // The transfer event for the migrated bundle will be the first one, which cannot be in the future
                // (entitlement always expects the first event to be in the past)
                throw new EntitlementTransferApiException(ErrorCode.ENT_TRANSFER_INVALID_EFF_DATE, effectiveTransferDate);
            }

            final SubscriptionBundle bundle = dao.getSubscriptionBundleFromAccountAndKey(sourceAccountId, bundleKey, fromInternalCallContext);
            if (bundle == null) {
                throw new EntitlementTransferApiException(ErrorCode.ENT_CREATE_NO_BUNDLE, bundleKey);
            }

            // Get the bundle timeline for the old account
            final BundleTimeline bundleTimeline = timelineApi.getBundleTimeline(bundle, context);

            final SubscriptionBundleData subscriptionBundleData = new SubscriptionBundleData(bundleKey, destAccountId, effectiveTransferDate);
            final List<SubscriptionMigrationData> subscriptionMigrationDataList = new LinkedList<SubscriptionMigrationData>();

            final List<TransferCancelData> transferCancelDataList = new LinkedList<TransferCancelData>();

            DateTime bundleStartdate = null;

            for (final SubscriptionTimeline cur : bundleTimeline.getSubscriptions()) {
                final SubscriptionData oldSubscription = (SubscriptionData) dao.getSubscriptionFromId(cur.getId(), fromInternalCallContext);
                final List<ExistingEvent> existingEvents = cur.getExistingEvents();
                final ProductCategory productCategory = existingEvents.get(0).getPlanPhaseSpecifier().getProductCategory();
                if (productCategory == ProductCategory.ADD_ON) {
                    if (!transferAddOn) {
                        continue;
                    }
                } else {

                    // If BP or STANDALONE subscription, create the cancel event on effectiveCancelDate
                    final DateTime effectiveCancelDate = !cancelImmediately && oldSubscription.getChargedThroughDate() != null &&
                                                         effectiveTransferDate.isBefore(oldSubscription.getChargedThroughDate()) ?
                                                         oldSubscription.getChargedThroughDate() : effectiveTransferDate;

                    final EntitlementEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                    .setSubscriptionId(cur.getId())
                                                                                    .setActiveVersion(cur.getActiveVersion())
                                                                                    .setProcessedDate(clock.getUTCNow())
                                                                                    .setEffectiveDate(effectiveCancelDate)
                                                                                    .setRequestedDate(effectiveTransferDate)
                                                                                    .setFromDisk(true));

                    TransferCancelData cancelData = new TransferCancelData(oldSubscription, cancelEvent);
                    transferCancelDataList.add(cancelData);
                }

                // We Align with the original subscription
                final DateTime subscriptionAlignStartDate = oldSubscription.getAlignStartDate();
                if (bundleStartdate == null) {
                    bundleStartdate = oldSubscription.getStartDate();
                }

                // Create the new subscription for the new bundle on the new account
                final SubscriptionData subscriptionData = createSubscriptionForApiUse(new SubscriptionBuilder()
                                                                                              .setId(UUID.randomUUID())
                                                                                              .setBundleId(subscriptionBundleData.getId())
                                                                                              .setCategory(productCategory)
                                                                                              .setBundleStartDate(effectiveTransferDate)
                                                                                              .setAlignStartDate(subscriptionAlignStartDate),
                                                                                      ImmutableList.<EntitlementEvent>of());

                final List<EntitlementEvent> events = toEvents(existingEvents, subscriptionData, effectiveTransferDate, context);
                final SubscriptionMigrationData curData = new SubscriptionMigrationData(subscriptionData, events, null);
                subscriptionMigrationDataList.add(curData);
            }
            BundleMigrationData bundleMigrationData = new BundleMigrationData(subscriptionBundleData, subscriptionMigrationDataList);

            // Atomically cancel all subscription on old account and create new bundle, subscriptions, events for new account
            dao.transfer(sourceAccountId, destAccountId, bundleMigrationData, transferCancelDataList, fromInternalCallContext, toInternalCallContext);

            return bundle;
        } catch (EntitlementRepairException e) {
            throw new EntitlementTransferApiException(e);
        }
    }
}
