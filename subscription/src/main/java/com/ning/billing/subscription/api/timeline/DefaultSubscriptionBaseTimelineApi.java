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

package com.ning.billing.subscription.api.timeline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.subscription.api.SubscriptionApiBase;
import com.ning.billing.subscription.api.SubscriptionBaseApiService;
import com.ning.billing.subscription.api.SubscriptionBaseTransitionType;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;
import com.ning.billing.subscription.api.user.SubscriptionBuilder;
import com.ning.billing.subscription.api.user.DefaultSubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransitionData;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.events.SubscriptionBaseEvent;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.subscription.api.timeline.SubscriptionBaseTimeline.NewEvent;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.clock.Clock;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.google.inject.name.Named;

public class DefaultSubscriptionBaseTimelineApi extends SubscriptionApiBase implements SubscriptionBaseTimelineApi {

    private final RepairSubscriptionLifecycleDao repairDao;
    private final CatalogService catalogService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AddonUtils addonUtils;

    private final SubscriptionBaseApiService repairApiService;

    private enum RepairType {
        BASE_REPAIR,
        ADD_ON_REPAIR,
        STANDALONE_REPAIR
    }

    @Inject
    public DefaultSubscriptionBaseTimelineApi(final CatalogService catalogService,
                                              final SubscriptionBaseApiService apiService,
                                              @Named(DefaultSubscriptionModule.REPAIR_NAMED) final RepairSubscriptionLifecycleDao repairDao, final SubscriptionDao dao,
                                              @Named(DefaultSubscriptionModule.REPAIR_NAMED) final SubscriptionBaseApiService repairApiService,
                                              final InternalCallContextFactory internalCallContextFactory, final Clock clock, final AddonUtils addonUtils) {
        super(dao, apiService, clock, catalogService);
        this.catalogService = catalogService;
        this.repairDao = repairDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.repairApiService = repairApiService;
        this.addonUtils = addonUtils;
    }

    @Override
    public BundleBaseTimeline getBundleTimeline(final SubscriptionBaseBundle bundle, final TenantContext context)
            throws SubscriptionBaseRepairException {
        return getBundleTimelineInternal(bundle, bundle.getExternalKey(), context);
    }

    @Override
    public BundleBaseTimeline getBundleTimeline(final UUID accountId, final String bundleName, final TenantContext context)
            throws SubscriptionBaseRepairException {
        final List<SubscriptionBaseBundle> bundles = dao.getSubscriptionBundlesFromAccountAndKey(accountId, bundleName, internalCallContextFactory.createInternalTenantContext(context));
        final SubscriptionBaseBundle bundle = bundles.size() > 0 ? bundles.get(bundles.size() - 1) : null;
        return getBundleTimelineInternal(bundle, bundleName + " [accountId= " + accountId.toString() + "]", context);
    }

    @Override
    public BundleBaseTimeline getBundleTimeline(final UUID bundleId, final TenantContext context) throws SubscriptionBaseRepairException {

        final SubscriptionBaseBundle bundle = dao.getSubscriptionBundleFromId(bundleId, internalCallContextFactory.createInternalTenantContext(context));
        return getBundleTimelineInternal(bundle, bundleId.toString(), context);
    }

    private BundleBaseTimeline getBundleTimelineInternal(final SubscriptionBaseBundle bundle, final String descBundle, final TenantContext context) throws SubscriptionBaseRepairException {
        try {
            if (bundle == null) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_UNKNOWN_BUNDLE, descBundle);
            }
            final List<SubscriptionDataRepair> subscriptions = convertToSubscriptionsDataRepair(dao.getSubscriptions(bundle.getId(), internalCallContextFactory.createInternalTenantContext(context)));
            if (subscriptions.size() == 0) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, bundle.getId());
            }
            final String viewId = getViewId(((DefaultSubscriptionBaseBundle) bundle).getLastSysUpdateDate(), subscriptions);
            final List<SubscriptionBaseTimeline> repairs = createGetSubscriptionRepairList(subscriptions, Collections.<SubscriptionBaseTimeline>emptyList());
            return createGetBundleRepair(bundle.getId(), bundle.getExternalKey(), viewId, repairs);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseRepairException(e);
        }
    }

    private List<SubscriptionDataRepair> convertToSubscriptionsDataRepair(List<SubscriptionBase> input) {
        return new ArrayList<SubscriptionDataRepair>(Collections2.transform(input, new Function<SubscriptionBase, SubscriptionDataRepair>() {
            @Override
            public SubscriptionDataRepair apply(@Nullable final SubscriptionBase subscription) {
                return convertToSubscriptionDataRepair((DefaultSubscriptionBase) subscription);
            }
        }));
    }
    private SubscriptionDataRepair convertToSubscriptionDataRepair(DefaultSubscriptionBase input) {
        return new SubscriptionDataRepair(input, repairApiService, (SubscriptionDao) repairDao, clock, addonUtils, catalogService, internalCallContextFactory);
    }

    @Override
    public BundleBaseTimeline repairBundle(final BundleBaseTimeline input, final boolean dryRun, final CallContext context) throws SubscriptionBaseRepairException {
        final InternalTenantContext tenantContext = internalCallContextFactory.createInternalTenantContext(context);
        try {
            final SubscriptionBaseBundle bundle = dao.getSubscriptionBundleFromId(input.getId(), tenantContext);
            if (bundle == null) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_UNKNOWN_BUNDLE, input.getId());
            }

            // Subscriptions are ordered with BASE subscription first-- if exists
            final List<SubscriptionDataRepair> subscriptions = convertToSubscriptionsDataRepair(dao.getSubscriptions(input.getId(), tenantContext));
            if (subscriptions.size() == 0) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_NO_ACTIVE_SUBSCRIPTIONS, input.getId());
            }

            final String viewId = getViewId(((DefaultSubscriptionBaseBundle) bundle).getLastSysUpdateDate(), subscriptions);
            if (!viewId.equals(input.getViewId())) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_VIEW_CHANGED, input.getId(), input.getViewId(), viewId);
            }

            DateTime firstDeletedBPEventTime = null;
            DateTime lastRemainingBPEventTime = null;

            boolean isBasePlanRecreate = false;
            DateTime newBundleStartDate = null;

            SubscriptionDataRepair baseSubscriptionRepair = null;
            final List<SubscriptionDataRepair> addOnSubscriptionInRepair = new LinkedList<SubscriptionDataRepair>();
            final List<SubscriptionDataRepair> inRepair = new LinkedList<SubscriptionDataRepair>();
            for (final SubscriptionBase cur : subscriptions) {
                final SubscriptionBaseTimeline curRepair = findAndCreateSubscriptionRepair(cur.getId(), input.getSubscriptions());
                if (curRepair != null) {
                    final SubscriptionDataRepair curInputRepair = ((SubscriptionDataRepair) cur);
                    final List<SubscriptionBaseEvent> remaining = getRemainingEventsAndValidateDeletedEvents(curInputRepair, firstDeletedBPEventTime, curRepair.getDeletedEvents());

                    final boolean isPlanRecreate = (curRepair.getNewEvents().size() > 0
                                                    && (curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionBaseTransitionType.CREATE
                                                        || curRepair.getNewEvents().get(0).getSubscriptionTransitionType() == SubscriptionBaseTransitionType.RE_CREATE));

                    final DateTime newSubscriptionStartDate = isPlanRecreate ? curRepair.getNewEvents().get(0).getRequestedDate() : null;

                    if (isPlanRecreate && remaining.size() != 0) {
                        throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_SUB_RECREATE_NOT_EMPTY, cur.getId(), cur.getBundleId());
                    }

                    if (!isPlanRecreate && remaining.size() == 0) {
                        throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_SUB_EMPTY, cur.getId(), cur.getBundleId());
                    }

                    if (cur.getCategory() == ProductCategory.BASE) {

                        final int bpTransitionSize = ((DefaultSubscriptionBase) cur).getAllTransitions().size();
                        lastRemainingBPEventTime = (remaining.size() > 0) ? curInputRepair.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime() : null;
                        firstDeletedBPEventTime = (remaining.size() < bpTransitionSize) ? curInputRepair.getAllTransitions().get(remaining.size()).getEffectiveTransitionTime() : null;

                        isBasePlanRecreate = isPlanRecreate;
                        newBundleStartDate = newSubscriptionStartDate;
                    }

                    if (curRepair.getNewEvents().size() > 0) {
                        final DateTime lastRemainingEventTime = (remaining.size() == 0) ? null : curInputRepair.getAllTransitions().get(remaining.size() - 1).getEffectiveTransitionTime();
                        validateFirstNewEvent(curInputRepair, curRepair.getNewEvents().get(0), lastRemainingBPEventTime, lastRemainingEventTime);
                    }

                    final SubscriptionDataRepair curOutputRepair = createSubscriptionDataRepair(curInputRepair, newBundleStartDate, newSubscriptionStartDate, remaining);
                    repairDao.initializeRepair(curInputRepair.getId(), remaining, tenantContext);
                    inRepair.add(curOutputRepair);
                    if (curOutputRepair.getCategory() == ProductCategory.ADD_ON) {
                        // Check if ADD_ON RE_CREATE is before BP start
                        if (isPlanRecreate && (subscriptions.get(0)).getStartDate().isAfter(curRepair.getNewEvents().get(0).getRequestedDate())) {
                            throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_AO_CREATE_BEFORE_BP_START, cur.getId(), cur.getBundleId());
                        }
                        addOnSubscriptionInRepair.add(curOutputRepair);
                    } else if (curOutputRepair.getCategory() == ProductCategory.BASE) {
                        baseSubscriptionRepair = curOutputRepair;
                    }
                }
            }

            final RepairType repairType = getRepairType(subscriptions.get(0), (baseSubscriptionRepair != null));
            switch (repairType) {
                case BASE_REPAIR:
                    // We need to add any existing addon that are not in the input repair list
                    for (final SubscriptionBase cur : subscriptions) {
                        if (cur.getCategory() == ProductCategory.ADD_ON && !inRepair.contains(cur)) {
                            final SubscriptionDataRepair curOutputRepair = createSubscriptionDataRepair((SubscriptionDataRepair) cur, newBundleStartDate, null, ((SubscriptionDataRepair) cur).getEvents());
                            repairDao.initializeRepair(curOutputRepair.getId(), ((SubscriptionDataRepair) cur).getEvents(), tenantContext);
                            inRepair.add(curOutputRepair);
                            addOnSubscriptionInRepair.add(curOutputRepair);
                        }
                    }
                    break;
                case ADD_ON_REPAIR:
                    // We need to set the baseSubscription as it is useful to calculate addon validity
                    final SubscriptionDataRepair baseSubscription = (SubscriptionDataRepair) subscriptions.get(0);
                    baseSubscriptionRepair = createSubscriptionDataRepair(baseSubscription, baseSubscription.getBundleStartDate(), baseSubscription.getAlignStartDate(), baseSubscription.getEvents());
                    break;
                case STANDALONE_REPAIR:
                default:
                    break;
            }

            validateBasePlanRecreate(isBasePlanRecreate, subscriptions, input.getSubscriptions());
            validateInputSubscriptionsKnown(subscriptions, input.getSubscriptions());

            final Collection<NewEvent> newEvents = createOrderedNewEventInput(input.getSubscriptions());
            for (final NewEvent newEvent : newEvents) {
                final DefaultNewEvent cur = (DefaultNewEvent) newEvent;
                final SubscriptionDataRepair curDataRepair = findSubscriptionDataRepair(cur.getSubscriptionId(), inRepair);
                if (curDataRepair == null) {
                    throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getSubscriptionId());
                }
                curDataRepair.addNewRepairEvent(cur, baseSubscriptionRepair, addOnSubscriptionInRepair, context);
            }

            if (dryRun) {
                baseSubscriptionRepair.addFutureAddonCancellation(addOnSubscriptionInRepair, context);

                final List<SubscriptionBaseTimeline> repairs = createGetSubscriptionRepairList(subscriptions, convertDataRepair(inRepair));
                return createGetBundleRepair(input.getId(), bundle.getExternalKey(), input.getViewId(), repairs);
            } else {
                dao.repair(bundle.getAccountId(), input.getId(), inRepair, internalCallContextFactory.createInternalCallContext(bundle.getAccountId(), context));
                return getBundleTimeline(input.getId(), context);
            }
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseRepairException(e);
        } finally {
            repairDao.cleanup(tenantContext);
        }
    }

    private RepairType getRepairType(final SubscriptionBase firstSubscription, final boolean gotBaseSubscription) {
        if (firstSubscription.getCategory() == ProductCategory.BASE) {
            return gotBaseSubscription ? RepairType.BASE_REPAIR : RepairType.ADD_ON_REPAIR;
        } else {
            return RepairType.STANDALONE_REPAIR;
        }
    }

    private void validateBasePlanRecreate(final boolean isBasePlanRecreate, final List<SubscriptionDataRepair> subscriptions, final List<SubscriptionBaseTimeline> input)
            throws SubscriptionBaseRepairException {
        if (!isBasePlanRecreate) {
            return;
        }
        if (subscriptions.size() != input.size()) {
            throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO, subscriptions.get(0).getBundleId());
        }
        for (final SubscriptionBaseTimeline cur : input) {
            if (cur.getNewEvents().size() != 0
                && (cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionBaseTransitionType.CREATE
                    && cur.getNewEvents().get(0).getSubscriptionTransitionType() != SubscriptionBaseTransitionType.RE_CREATE)) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_BP_RECREATE_MISSING_AO_CREATE, subscriptions.get(0).getBundleId());
            }
        }
    }

    private void validateInputSubscriptionsKnown(final List<SubscriptionDataRepair> subscriptions, final List<SubscriptionBaseTimeline> input)
            throws SubscriptionBaseRepairException {
        for (final SubscriptionBaseTimeline cur : input) {
            boolean found = false;
            for (final SubscriptionBase s : subscriptions) {
                if (s.getId().equals(cur.getId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_UNKNOWN_SUBSCRIPTION, cur.getId());
            }
        }
    }

    private void validateFirstNewEvent(final DefaultSubscriptionBase data, final NewEvent firstNewEvent, final DateTime lastBPRemainingTime, final DateTime lastRemainingTime)
            throws SubscriptionBaseRepairException {
        if (lastBPRemainingTime != null &&
            firstNewEvent.getRequestedDate().isBefore(lastBPRemainingTime)) {
            throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_BP_REMAINING, firstNewEvent.getSubscriptionTransitionType(), data.getId());
        }
        if (lastRemainingTime != null &&
            firstNewEvent.getRequestedDate().isBefore(lastRemainingTime)) {
            throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_NEW_EVENT_BEFORE_LAST_AO_REMAINING, firstNewEvent.getSubscriptionTransitionType(), data.getId());
        }

    }

    private Collection<NewEvent> createOrderedNewEventInput(final List<SubscriptionBaseTimeline> subscriptionsReapir) {
        final TreeSet<NewEvent> newEventSet = new TreeSet<SubscriptionBaseTimeline.NewEvent>(new Comparator<NewEvent>() {
            @Override
            public int compare(final NewEvent o1, final NewEvent o2) {
                return o1.getRequestedDate().compareTo(o2.getRequestedDate());
            }
        });
        for (final SubscriptionBaseTimeline cur : subscriptionsReapir) {
            for (final NewEvent e : cur.getNewEvents()) {
                newEventSet.add(new DefaultNewEvent(cur.getId(), e.getPlanPhaseSpecifier(), e.getRequestedDate(), e.getSubscriptionTransitionType()));
            }
        }

        return newEventSet;
    }

    private List<SubscriptionBaseEvent> getRemainingEventsAndValidateDeletedEvents(final SubscriptionDataRepair data, final DateTime firstBPDeletedTime,
                                                                              final List<SubscriptionBaseTimeline.DeletedEvent> deletedEvents)
            throws SubscriptionBaseRepairException {
        if (deletedEvents == null || deletedEvents.size() == 0) {
            return data.getEvents();
        }

        int nbDeleted = 0;
        final LinkedList<SubscriptionBaseEvent> result = new LinkedList<SubscriptionBaseEvent>();
        for (final SubscriptionBaseEvent cur : data.getEvents()) {

            boolean foundDeletedEvent = false;
            for (final SubscriptionBaseTimeline.DeletedEvent d : deletedEvents) {
                if (cur.getId().equals(d.getEventId())) {
                    foundDeletedEvent = true;
                    nbDeleted++;
                    break;
                }
            }
            if (!foundDeletedEvent && nbDeleted > 0) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_INVALID_DELETE_SET, cur.getId(), data.getId());
            }
            if (firstBPDeletedTime != null &&
                !cur.getEffectiveDate().isBefore(firstBPDeletedTime) &&
                !foundDeletedEvent) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_MISSING_AO_DELETE_EVENT, cur.getId(), data.getId());
            }

            if (nbDeleted == 0) {
                result.add(cur);
            }
        }

        if (nbDeleted != deletedEvents.size()) {
            for (final SubscriptionBaseTimeline.DeletedEvent d : deletedEvents) {
                boolean found = false;
                for (final SubscriptionBaseTransition cur : data.getAllTransitions()) {
                    if (((SubscriptionBaseTransitionData) cur).getId().equals(d.getEventId())) {
                        found = true;
                    }
                }
                if (!found) {
                    throw new SubscriptionBaseRepairException(ErrorCode.SUB_REPAIR_NON_EXISTENT_DELETE_EVENT, d.getEventId(), data.getId());
                }
            }

        }

        return result;
    }

    private String getViewId(final DateTime lastUpdateBundleDate, final List<SubscriptionDataRepair> subscriptions) {
        final StringBuilder tmp = new StringBuilder();
        long lastOrderedId = -1;
        for (final SubscriptionBase cur : subscriptions) {
            lastOrderedId = lastOrderedId < ((DefaultSubscriptionBase) cur).getLastEventOrderedId() ? ((DefaultSubscriptionBase) cur).getLastEventOrderedId() : lastOrderedId;
        }
        tmp.append(lastOrderedId);
        tmp.append("-");
        tmp.append(lastUpdateBundleDate.toDate().getTime());

        return tmp.toString();
    }

    private BundleBaseTimeline createGetBundleRepair(final UUID bundleId, final String externalKey, final String viewId, final List<SubscriptionBaseTimeline> repairList) {
        return new BundleBaseTimeline() {
            @Override
            public String getViewId() {
                return viewId;
            }

            @Override
            public List<SubscriptionBaseTimeline> getSubscriptions() {
                return repairList;
            }

            @Override
            public UUID getId() {
                return bundleId;
            }

            @Override
            public DateTime getCreatedDate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public DateTime getUpdatedDate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getExternalKey() {
                return externalKey;
            }
        };
    }

    private List<SubscriptionBaseTimeline> createGetSubscriptionRepairList(final List<SubscriptionDataRepair> subscriptions, final List<SubscriptionBaseTimeline> inRepair) throws CatalogApiException {

        final List<SubscriptionBaseTimeline> result = new LinkedList<SubscriptionBaseTimeline>();
        final Set<UUID> repairIds = new TreeSet<UUID>();
        for (final SubscriptionBaseTimeline cur : inRepair) {
            repairIds.add(cur.getId());
            result.add(cur);
        }

        for (final SubscriptionBase cur : subscriptions) {
            if (!repairIds.contains(cur.getId())) {
                result.add(new DefaultSubscriptionBaseTimeline((SubscriptionDataRepair) cur, catalogService.getFullCatalog()));
            }
        }

        return result;
    }

    private List<SubscriptionBaseTimeline> convertDataRepair(final List<SubscriptionDataRepair> input) throws CatalogApiException {
        final List<SubscriptionBaseTimeline> result = new LinkedList<SubscriptionBaseTimeline>();
        for (final SubscriptionDataRepair cur : input) {
            result.add(new DefaultSubscriptionBaseTimeline(cur, catalogService.getFullCatalog()));
        }

        return result;
    }

    private SubscriptionDataRepair findSubscriptionDataRepair(final UUID targetId, final List<SubscriptionDataRepair> input) {
        for (final SubscriptionDataRepair cur : input) {
            if (cur.getId().equals(targetId)) {
                return cur;
            }
        }

        return null;
    }

    private SubscriptionDataRepair createSubscriptionDataRepair(final DefaultSubscriptionBase curData, final DateTime newBundleStartDate, final DateTime newSubscriptionStartDate, final List<SubscriptionBaseEvent> initialEvents) {
        final SubscriptionBuilder builder = new SubscriptionBuilder(curData);
        builder.setActiveVersion(curData.getActiveVersion() + 1);
        if (newBundleStartDate != null) {
            builder.setBundleStartDate(newBundleStartDate);
        }
        if (newSubscriptionStartDate != null) {
            builder.setAlignStartDate(newSubscriptionStartDate);
        }
        if (initialEvents.size() > 0) {
            for (final SubscriptionBaseEvent cur : initialEvents) {
                cur.setActiveVersion(builder.getActiveVersion());
            }
        }

        final SubscriptionDataRepair subscriptiondataRepair = new SubscriptionDataRepair(builder, curData.getEvents(), repairApiService, (SubscriptionDao) repairDao, clock, addonUtils, catalogService, internalCallContextFactory);
        subscriptiondataRepair.rebuildTransitions(curData.getEvents(), catalogService.getFullCatalog());
        return subscriptiondataRepair;
    }

    private SubscriptionBaseTimeline findAndCreateSubscriptionRepair(final UUID target, final List<SubscriptionBaseTimeline> input) {
        for (final SubscriptionBaseTimeline cur : input) {
            if (target.equals(cur.getId())) {
                return new DefaultSubscriptionBaseTimeline(cur);
            }
        }

        return null;
    }
}

