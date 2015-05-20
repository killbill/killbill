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

package org.killbill.billing.subscription.engine.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.clock.Clock;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.migration.AccountMigrationData;
import org.killbill.billing.subscription.api.migration.AccountMigrationData.BundleMigrationData;
import org.killbill.billing.subscription.api.timeline.RepairSubscriptionLifecycleDao;
import org.killbill.billing.subscription.api.timeline.SubscriptionDataRepair;
import org.killbill.billing.subscription.api.transfer.TransferCancelData;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class RepairSubscriptionDao extends EntityDaoBase<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> implements SubscriptionDao, RepairSubscriptionLifecycleDao {

    private static final String NOT_IMPLEMENTED = "Not implemented";

    private final ThreadLocal<Map<UUID, SubscriptionRepairEvent>> preThreadsInRepairSubscriptions = new ThreadLocal<Map<UUID, SubscriptionRepairEvent>>();

    @Inject
    public RepairSubscriptionDao(final IDBI dbi, final Clock clock, final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao), BundleSqlDao.class);
    }

    @Override
    protected SubscriptionApiException generateAlreadyExistsException(final SubscriptionBundleModelDao entity, final InternalCallContext context) {
        return new SubscriptionApiException(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS, entity.getExternalKey());
    }

    private static final class SubscriptionEventWithOrderingId {

        private final SubscriptionBaseEvent event;
        private final long orderingId;

        public SubscriptionEventWithOrderingId(final SubscriptionBaseEvent event, final long orderingId) {
            this.event = event;
            this.orderingId = orderingId;
        }

        public SubscriptionBaseEvent getEvent() {
            return event;
        }

        public long getOrderingId() {
            return orderingId;
        }

        @Override
        public String toString() {
            final StringBuilder tmp = new StringBuilder();
            tmp.append("[");
            tmp.append(event.getType());
            tmp.append(": effDate=");
            tmp.append(event.getEffectiveDate());
            tmp.append(", subId=");
            tmp.append(event.getSubscriptionId());
            tmp.append(", ordering=");
            tmp.append(event.getTotalOrdering());
            tmp.append("]");
            return tmp.toString();
        }
    }

    private static final class SubscriptionRepairEvent {

        private final Set<SubscriptionEventWithOrderingId> events;
        private long curOrderingId;

        public SubscriptionRepairEvent(final List<SubscriptionBaseEvent> initialEvents) {
            this.events = new TreeSet<SubscriptionEventWithOrderingId>(new Comparator<SubscriptionEventWithOrderingId>() {
                @Override
                public int compare(final SubscriptionEventWithOrderingId o1, final SubscriptionEventWithOrderingId o2) {
                    // Work around jdk7 change: compare(o1, o1) is now invoked when inserting the first element
                    // See:
                    // - http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5045147
                    // - http://hg.openjdk.java.net/jdk7/tl/jdk/rev/bf37edb38fbb
                    if (o1 == o2) {
                        return 0;
                    }

                    final int result = o1.getEvent().getEffectiveDate().compareTo(o2.getEvent().getEffectiveDate());
                    if (result == 0) {
                        if (o1.getOrderingId() < o2.getOrderingId()) {
                            return -1;
                        } else if (o1.getOrderingId() > o2.getOrderingId()) {
                            return 1;
                        } else {
                            throw new RuntimeException(String.format(" Repair subscription events should not have the same orderingId %s, %s ", o1, o2));
                        }
                    }
                    return result;
                }
            });

            this.curOrderingId = 0;

            if (initialEvents != null) {
                addEvents(initialEvents);
            }
        }

        public List<SubscriptionBaseEvent> getEvents() {
            return new ArrayList<SubscriptionBaseEvent>(Collections2.transform(events, new Function<SubscriptionEventWithOrderingId, SubscriptionBaseEvent>() {
                @Override
                public SubscriptionBaseEvent apply(SubscriptionEventWithOrderingId in) {
                    return in.getEvent();
                }
            }));
        }

        public void addEvents(final List<SubscriptionBaseEvent> newEvents) {
            for (final SubscriptionBaseEvent cur : newEvents) {
                events.add(new SubscriptionEventWithOrderingId(cur, curOrderingId++));
            }
        }
    }

    private Map<UUID, SubscriptionRepairEvent> getRepairMap() {
        if (preThreadsInRepairSubscriptions.get() == null) {
            preThreadsInRepairSubscriptions.set(new HashMap<UUID, SubscriptionRepairEvent>());
        }
        return preThreadsInRepairSubscriptions.get();
    }

    private SubscriptionRepairEvent getRepairSubscriptionEvents(final UUID subscriptionId) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        return map.get(subscriptionId);
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        return new LinkedList<SubscriptionBaseEvent>(target.getEvents());
    }

    @Override
    public void createSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> createEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), createEvents);
    }

    @Override
    public void recreateSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> recreateEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), recreateEvents);
    }

    @Override
    public void cancelSubscription(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent cancelEvent, final InternalCallContext context, final int cancelSeq) {
        final UUID subscriptionId = subscription.getId();
        final long activeVersion = cancelEvent.getActiveVersion();
        addEvents(subscriptionId, Collections.singletonList(cancelEvent));
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        boolean foundCancelEvent = false;
        for (final SubscriptionBaseEvent cur : target.getEvents()) {
            if (cur.getId().equals(cancelEvent.getId())) {
                foundCancelEvent = true;
            } else if (foundCancelEvent) {
                cur.setActiveVersion(activeVersion - 1);
            }
        }
    }

    @Override
    public void cancelSubscriptions(final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final InternalCallContext context) {
    }

    @Override
    public void changePlan(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> changeEvents, final InternalCallContext context) {
        addEvents(subscription.getId(), changeEvents);
    }

    @Override
    public void initializeRepair(final UUID subscriptionId, final List<SubscriptionBaseEvent> initialEvents, final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        if (map.get(subscriptionId) == null) {
            final SubscriptionRepairEvent value = new SubscriptionRepairEvent(initialEvents);
            map.put(subscriptionId, value);
        } else {
            throw new SubscriptionBaseError(String.format("Unexpected SubscriptionRepairEvent %s for thread %s", subscriptionId, Thread.currentThread().getName()));
        }
    }

    @Override
    public void cleanup(final InternalTenantContext context) {
        final Map<UUID, SubscriptionRepairEvent> map = getRepairMap();
        map.clear();
    }

    private void addEvents(final UUID subscriptionId, final List<SubscriptionBaseEvent> events) {
        final SubscriptionRepairEvent target = getRepairSubscriptionEvents(subscriptionId);
        target.addEvents(events);
    }

    @Override
    public void uncancelSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> uncancelEvents, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBaseBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBaseBundle createSubscriptionBundle(final DefaultSubscriptionBaseBundle bundle, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBase getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBase getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBase> getSubscriptions(final UUID bundleId, final List<SubscriptionBaseEvent> dryRunEvents, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void updateChargedThroughDate(final DefaultSubscriptionBase subscription, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void createNextPhaseEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent nextPhase, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public SubscriptionBaseEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public Map<UUID, List<SubscriptionBase>> getSubscriptionsForAccount(final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public Iterable<SubscriptionBaseEvent> getFutureEventsForAccount(final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBaseEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData data, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleMigrationData data,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext,
                         final InternalCallContext toContext) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public void updateBundleExternalKey(final UUID bundleId, final String externalKey, final InternalCallContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        throw new SubscriptionBaseError(NOT_IMPLEMENTED);
    }
}
