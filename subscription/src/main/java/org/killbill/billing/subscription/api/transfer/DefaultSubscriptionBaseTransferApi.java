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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.timeline.BundleBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseRepairException;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimeline.ExistingEvent;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransition;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.catalog.SubscriptionCatalog;
import org.killbill.billing.subscription.catalog.SubscriptionCatalogApi;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.bcd.BCDEventData;
import org.killbill.billing.subscription.events.phase.PhaseEventData;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventChange;
import org.killbill.billing.subscription.events.user.ApiEventTransfer;
import org.killbill.billing.util.UUIDs;
import org.killbill.commons.utils.annotation.VisibleForTesting;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;

public class DefaultSubscriptionBaseTransferApi extends SubscriptionApiBase implements SubscriptionBaseTransferApi {

    private final SubscriptionCatalogApi subscriptionCatalogApi;
    private final SubscriptionBaseTimelineApi timelineApi;
    private final SubscriptionBaseInternalApi subscriptionBaseInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultSubscriptionBaseTransferApi(final Clock clock, final SubscriptionDao dao, final SubscriptionBaseTimelineApi timelineApi, final SubscriptionCatalogApi subscriptionCatalogApi,
                                              final SubscriptionBaseInternalApi subscriptionBaseInternalApi, final SubscriptionBaseApiService apiService, final InternalCallContextFactory internalCallContextFactory) {
        super(dao, apiService, clock);
        this.subscriptionCatalogApi = subscriptionCatalogApi;
        this.timelineApi = timelineApi;
        this.subscriptionBaseInternalApi = subscriptionBaseInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    private SubscriptionBaseEvent createEvent(final boolean firstEvent, final ExistingEvent existingEvent, final DefaultSubscriptionBase subscription, final DateTime transferDate)
            throws CatalogApiException {

        SubscriptionBaseEvent newEvent = null;
        final DateTime effectiveDate = existingEvent.getEffectiveDate().isBefore(transferDate) ? transferDate : existingEvent.getEffectiveDate();

        final ApiEventBuilder apiBuilder = new ApiEventBuilder()
                .setSubscriptionId(subscription.getId())
                .setEventPlan(existingEvent.getPlanName())
                .setEventPlanPhase(existingEvent.getPlanPhaseName())
                .setEffectiveDate(effectiveDate)
                .setFromDisk(true);

        final PlanPhaseSpecifier spec = existingEvent.getPlanPhaseSpecifier();
        if (spec != null) {
            apiBuilder.setEventPriceList(spec.getPriceListName());
        }

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
                           PhaseEventData.createNextPhaseEvent(subscription.getId(), existingEvent.getPlanPhaseName(), effectiveDate);
                break;

            case BCD_CHANGE:
                newEvent = BCDEventData.createBCDEvent(subscription, effectiveDate, existingEvent.getBillCycleDayLocal());
                break;

            case CANCEL:
            default:
                break;

        }
        return newEvent;
    }

    @VisibleForTesting
    List<SubscriptionBaseEvent> toEvents(final List<ExistingEvent> existingEvents, final DefaultSubscriptionBase subscription, final DateTime transferDate) throws SubscriptionBaseTransferApiException {
        try {
            final List<SubscriptionBaseEvent> result = new LinkedList<>();
            ExistingEvent prevEvent = null;
            ExistingEvent prevBCDEvent = null;
            boolean firstEvent = true;
            for (final ExistingEvent cur : existingEvents) {
                // Skip all events prior to the transferDate
                if (cur.getEffectiveDate().isBefore(transferDate)) {
                    if (cur.getSubscriptionTransitionType() == SubscriptionBaseTransitionType.BCD_CHANGE) {
                        prevBCDEvent = cur;
                    } else {
                        prevEvent = cur;
                    }
                    continue;
                }

                // Add previous event the first time if needed
                if (insertEventToResult(firstEvent, prevEvent, subscription, transferDate, result)) {
                    firstEvent = false;
                    prevEvent = null;
                }

                if (insertEventToResult(firstEvent, prevBCDEvent, subscription, transferDate, result)) {
                    firstEvent = false;
                    prevBCDEvent = null;
                }

                if (insertEventToResult(firstEvent, cur, subscription, transferDate, result)) {
                    firstEvent = false;
                }
            }

            // Previous loop did not get anything because transferDate is greater than effectiveDate of last event
            if (insertEventToResult(firstEvent, prevEvent, subscription, transferDate, result)) {
                firstEvent = false;
            }
            insertEventToResult(firstEvent, prevBCDEvent, subscription, transferDate, result);


            return result;
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseTransferApiException(e);
        }
    }

    private boolean insertEventToResult(final boolean firstEvent, @Nullable final ExistingEvent event, final DefaultSubscriptionBase subscription, final DateTime transferDate, final List<SubscriptionBaseEvent> result) throws CatalogApiException {
        if (event != null) {
            final SubscriptionBaseEvent newEvent = createEvent(firstEvent, event, subscription, transferDate);
            if (newEvent != null) {
                result.add(newEvent);
                return true;
            }
        }
        return false;
    }

    @Override
    public SubscriptionBaseBundle transferBundle(final UUID sourceAccountId, final UUID destAccountId,
                                                 final String bundleKey, final Map<UUID, String> subExtKeysMap,
                                                 final DateTime transferDate, final boolean transferAddOn,
                                                 final boolean cancelImmediately, final CallContext context) throws SubscriptionBaseTransferApiException {
        final InternalCallContext fromInternalCallContext = internalCallContextFactory.createInternalCallContext(sourceAccountId, context);
        final InternalCallContext toInternalCallContext = internalCallContextFactory.createInternalCallContext(destAccountId, context);

        try {

            final SubscriptionCatalog catalog = subscriptionCatalogApi.getFullCatalog(fromInternalCallContext);
            final DateTime effectiveTransferDate = transferDate == null ? context.getCreatedDate() : transferDate;
            final SubscriptionBaseBundle bundle = subscriptionBaseInternalApi.getActiveBundleForKey(catalog.getCatalog(), bundleKey, fromInternalCallContext);
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
                final DefaultSubscriptionBase oldSubscription = (DefaultSubscriptionBase) dao.getSubscriptionFromId(cur.getId(), catalog, false, fromInternalCallContext);
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
                    final DateTime candidateCancelDate = !cancelImmediately &&
                                                         oldSubscription.getChargedThroughDate() != null &&
                                                         effectiveTransferDate.isBefore(oldSubscription.getChargedThroughDate()) ?
                                                         oldSubscription.getChargedThroughDate() : effectiveTransferDate;

                    //
                    // We are checking that if the subscription is PENDING (start date in the future) and if the requestedDate
                    // for the transfer is prior to the startDate, then it gets realigned with the startDate.
                    // The code goes further (reuse logic from validateEffectiveDate) and also checks that we if we have a subscription with multiple
                    // change plans (that we want to transfer), we don't end up cancelling prior a previous transition as this would create some
                    // weird REPAIR scenarios.
                    //
                    final SubscriptionBaseTransition previousTransition = oldSubscription.getPreviousTransition();
                    final DateTime earliestValidDate = previousTransition != null ? previousTransition.getEffectiveTransitionTime() : oldSubscription.getStartDate();
                    final DateTime effectiveCancelDate = (candidateCancelDate.isBefore(earliestValidDate)) ? earliestValidDate : candidateCancelDate;

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


                // Use any key provided from the map subExtKeysMap
                final String subExtKey = subExtKeysMap.get(oldSubscription.getId());
                // Create the new subscription for the new bundle on the new account
                final DefaultSubscriptionBase defaultSubscriptionBase = createSubscriptionForApiUse(new SubscriptionBuilder()
                                                                                                            .setId(UUIDs.randomUUID())
                                                                                                            .setBundleId(subscriptionBundleData.getId())
                                                                                                            .setBundleExternalKey(subscriptionBundleData.getExternalKey())
                                                                                                            .setExternalKey(subExtKey)
                                                                                                            .setCategory(productCategory)
                                                                                                            .setBundleStartDate(effectiveTransferDate)
                                                                                                            .setAlignStartDate(subscriptionAlignStartDate),
                                                                                                    Collections.emptyList(),
                                                                                                    catalog);

                final List<SubscriptionBaseEvent> events = toEvents(existingEvents, defaultSubscriptionBase, effectiveTransferDate);
                final SubscriptionTransferData curData = new SubscriptionTransferData(defaultSubscriptionBase, events, null);
                subscriptionTransferDataList.add(curData);
            }
            final BundleTransferData bundleTransferData = new BundleTransferData(subscriptionBundleData, subscriptionTransferDataList);

            // Atomically cancelWithRequestedDate all subscription on old account and create new bundle, subscriptions, events for new account
            dao.transfer(sourceAccountId, destAccountId, bundleTransferData, transferCancelDataList, catalog, fromInternalCallContext, toInternalCallContext);

            return bundleTransferData.getData();
        } catch (final SubscriptionBaseRepairException e) {
            throw new SubscriptionBaseTransferApiException(e);
        } catch (final CatalogApiException e) {
            throw new SubscriptionBaseTransferApiException(e);
        }
    }

}
