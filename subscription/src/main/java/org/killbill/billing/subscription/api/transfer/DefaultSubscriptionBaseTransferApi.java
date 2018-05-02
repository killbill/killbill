/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription.api.transfer;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import org.killbill.billing.subscription.api.timeline.BundleBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseRepairException;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.ExistingEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventChange;
import org.killbill.billing.subscription.events.user.ApiEventTransfer;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.UUIDs;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultSubscriptionBaseTransferApi extends SubscriptionApiBase implements SubscriptionBaseTransferApi {

    private final CatalogInternalApi catalogInternalApi;
    private final SubscriptionBaseTimelineApi timelineApi;
    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultSubscriptionBaseTransferApi(final Clock clock, final SubscriptionDao dao, final SubscriptionBaseTimelineApi timelineApi, final CatalogInternalApi catalogInternalApi,
                                              final SubscriptionBaseInternalApi subscriptionBaseInternalApi, final SubscriptionBaseApiService apiService, final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock);
        this.catalogInternalApi = catalogInternalApi;
        this.timelineApi = timelineApi;
        this.subscriptionBaseInternalApi = subscriptionBaseInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    private SubscriptionBaseEvent createEvent(final boolean firstEvent, final ExistingEvent existingEvent, final DefaultSubscriptionBase subscription, final DateTime transferDate, final Catalog catalog, final InternalTenantContext context)
            throws CatalogApiException {

        SubscriptionBaseEvent newEvent = null;
        final DateTime effectiveDate = existingEvent.getEffectiveDate().isBefore(transferDate) ? transferDate : existingEvent.getEffectiveDate();

        final PlanPhaseSpecifier spec = existingEvent.getPlanPhaseSpecifier();
        final PlanPhase currentPhase = existingEvent.getPlanPhaseName() != null ? catalog.findPhase(existingEvent.getPlanPhaseName(), effectiveDate, subscription.getAlignStartDate()) : null;

        if (spec == null || currentPhase == null) {
            // Ignore cancellations - we assume that transferred subscriptions should always be active
            return null;
        }
        final ApiEventBuilder apiBuilder = new ApiEventBuilder()
                .setSubscriptionId(subscription.getId())
                .setEventPlan(existingEvent.getPlanName())
                .setEventPlanPhase(currentPhase.getName())
                .setEventPriceList(spec.getPriceListName())
                .setEffectiveDate(effectiveDate)
                .setFromDisk(true);

        switch (existingEvent.getSubscriptionTransitionType()) {
            case TRANSFER:
            case CREATE:
                newEvent = new ApiEventTransfer(apiBuilder);
                break;

            // Should we even keep future change events; product question really
            case CHANGE:
                newEvent = firstEvent ? new ApiEventTransfer(apiBuilder) : new ApiEventChange(apiBuilder);
                break;

            case PHASE:
                newEvent = firstEvent ? new ApiEventTransfer(apiBuilder) :
                           PhaseEventData.createNextPhaseEvent(subscription.getId(), currentPhase.getName(), effectiveDate);
                break;

            case CANCEL:
                break;

            default:
                throw new SubscriptionBaseError(String.format("Unexpected transitionType %s", existingEvent.getSubscriptionTransitionType()));
        }
        return newEvent;
    }

    @VisibleForTesting
    List<SubscriptionBaseEvent> toEvents(final List<ExistingEvent> existingEvents, final DefaultSubscriptionBase subscription,
                                         final DateTime transferDate, final Catalog catalog, final InternalTenantContext context) throws SubscriptionBaseTransferApiException {


        try {

            final List<SubscriptionBaseEvent> result = new LinkedList<SubscriptionBaseEvent>();

            SubscriptionBaseEvent event = null;
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
                    event = createEvent(firstEvent, prevEvent, subscription, transferDate, catalog, context);
                    if (event != null) {
                        result.add(event);
                        firstEvent = false;
                    }
                    prevEvent = null;
                }

                event = createEvent(firstEvent, cur, subscription, transferDate, catalog, context);
                if (event != null) {
                    result.add(event);
                    firstEvent = false;
                }
            }

            // Previous loop did not get anything because transferDate is greater than effectiveDate of last event
            if (prevEvent != null) {
                event = createEvent(firstEvent, prevEvent, subscription, transferDate, catalog, context);
                if (event != null) {
                    result.add(event);
                }
                prevEvent = null;
            }

            return result;
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseTransferApiException(e);
        }
    }

    @Override
    public SubscriptionBaseBundle transferBundle(final UUID sourceAccountId, final UUID destAccountId,
                                                 final String bundleKey, final DateTime transferDate, final boolean transferAddOn,
                                                 final boolean cancelImmediately, final CallContext context) throws SubscriptionBaseTransferApiException {
        final InternalCallContext fromInternalCallContext = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
        final InternalCallContext toInternalCallContext = internalCallContextFactory.createInternalCallContext(destAccountId, context);

        try {
            final Catalog catalog = catalogInternalApi.getFullCatalog(true, true, fromInternalCallContext);


            final DateTime effectiveTransferDate = transferDate == null ? context.getCreatedDate() : transferDate;
            if (effectiveTransferDate.isAfter(context.getCreatedDate())) {
                // The transfer event for the migrated bundle will be the first one, which cannot be in the future
                // (subscription always expects the first event to be in the past)
                throw new SubscriptionBaseTransferApiException(ErrorCode.SUB_TRANSFER_INVALID_EFF_DATE, effectiveTransferDate);
            }

            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getActiveBundleForKey(bundleKey, catalog, fromInternalCallContext);
            if (bundle == null) {
                throw new SubscriptionBaseTransferApiException(ErrorCode.SUB_CREATE_NO_BUNDLE, bundleKey);
            }

            // Get the bundle timeline for the old account
            final BundleBaseTimeline bundleBaseTimeline = timelineApi.getBundleTimeline(bundle, context);

            final DefaultSubscriptionBaseBundle subscriptionBundleData = new DefaultSubscriptionBaseBundle(bundleKey, destAccountId, effectiveTransferDate,
                                                                                                           bundle.getOriginalCreatedDate(), context.getCreatedDate(), context.getCreatedDate());
            final List<SubscriptionTransferData> subscriptionTransferDataList = new LinkedList<SubscriptionTransferData>();

            final List<TransferCancelData> transferCancelDataList = new LinkedList<TransferCancelData>();

            DateTime bundleStartdate = null;

            for (final SubscriptionBaseTimeline cur : bundleBaseTimeline.getSubscriptions()) {
                final DefaultSubscriptionBase oldSubscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(cur.getId(), catalog, fromInternalCallContext);
                // Skip already cancelled subscriptions
                if (oldSubscription.getState() == EntitlementState.CANCELLED) {
                    continue;
                }
                final List<ExistingEvent> existingEvents = cur.getExistingEvents();
                final ProductCategory productCategory = existingEvents.get(0).getProductCategory();

                // For future add-on cancellations, don't add a cancellation on disk right away (mirror the behavior
                // on base plan cancellations, even though we don't support un-transfer today)
                if (productCategory != ProductCategory.ADD_ON || cancelImmediately) {
                    // Create the cancelWithRequestedDate event on effectiveCancelDate
                    final DateTime effectiveCancelDate = !cancelImmediately && oldSubscription.getChargedThroughDate() != null &&
                                                         effectiveTransferDate.isBefore(oldSubscription.getChargedThroughDate()) ?
                                                         oldSubscription.getChargedThroughDate() : effectiveTransferDate;

                    final SubscriptionBaseEvent cancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                         .setSubscriptionId(cur.getId())
                                                                                         .setEffectiveDate(effectiveCancelDate)
                                                                                         .setFromDisk(true));

                    final TransferCancelData cancelData = new TransferCancelData(oldSubscription, cancelEvent);
                    transferCancelDataList.add(cancelData);
                }

                if (productCategory == ProductCategory.ADD_ON && !transferAddOn) {
                    continue;
                }

                // We Align with the original subscription
                final DateTime subscriptionAlignStartDate = oldSubscription.getAlignStartDate();
                if (bundleStartdate == null) {
                    bundleStartdate = oldSubscription.getStartDate();
                }

                // Create the new subscription for the new bundle on the new account
                final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionForApiUse(new SubscriptionBuilder()
                                                                                                            .setId(UUIDs.randomUUID())
                                                                                                            .setBundleId(subscriptionBundleData.getId())
                                                                                                            .setBundleExternalKey(subscriptionBundleData.getExternalKey())
                                                                                                            .setCategory(productCategory)
                                                                                                            .setBundleStartDate(effectiveTransferDate)
                                                                                                            .setAlignStartDate(subscriptionAlignStartDate),
                                                                                                    ImmutableList.<SubscriptionBaseEvent>of(), catalog, fromInternalCallContext);

                final List<SubscriptionBaseEvent> events = toEvents(existingEvents, defaultSubscriptionBase, effectiveTransferDate, catalog, fromInternalCallContext);
                final SubscriptionTransferData curData = new SubscriptionTransferData(defaultSubscriptionBase, events, null);
                subscriptionTransferDataList.add(curData);
            }
            final BundleTransferData bundleTransferData = new BundleTransferData(subscriptionBundleData, subscriptionTransferDataList);

            // Atomically cancelWithRequestedDate all subscription on old account and create new bundle, subscriptions, events for new account
            dao.transfer(sourceAccountId, destAccountId, bundleTransferData, transferCancelDataList, catalog, fromInternalCallContext, toInternalCallContext);

            return bundleTransferData.getData();
        } catch (SubscriptionBaseRepairException e) {
            throw new SubscriptionBaseTransferApiException(e);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseTransferApiException(e);
        }
    }

}
