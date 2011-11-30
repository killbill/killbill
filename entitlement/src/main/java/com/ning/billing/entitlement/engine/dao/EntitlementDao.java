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

package com.ning.billing.entitlement.engine.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.config.IEntitlementConfig;
import com.ning.billing.entitlement.api.user.ISubscription;
import com.ning.billing.entitlement.api.user.ISubscriptionBundle;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.events.IEntitlementEvent;
import com.ning.billing.entitlement.events.IEntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.events.user.IApiEvent;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.Hostname;
import com.ning.billing.util.clock.IClock;

public class EntitlementDao implements IEntitlementDao {

    private final static Logger log = LoggerFactory.getLogger(EntitlementDao.class);

    private final IClock clock;
    private final ISubscriptionSqlDao subscriptionsDao;
    private final IBundleSqlDao bundlesDao;
    private final IEventSqlDao eventsDao;
    private final IEntitlementConfig config;
    private final String hostname;

    @Inject
    public EntitlementDao(DBI dbi, IClock clock, IEntitlementConfig config) {
        this.clock = clock;
        this.config = config;
        this.subscriptionsDao = dbi.onDemand(ISubscriptionSqlDao.class);
        this.eventsDao = dbi.onDemand(IEventSqlDao.class);
        this.bundlesDao = dbi.onDemand(IBundleSqlDao.class);
        this.hostname = Hostname.get();
    }

    @Override
    public List<ISubscriptionBundle> getSubscriptionBundleForAccount(
            UUID accountId) {
        return bundlesDao.getBundleFromAccount(accountId.toString());
    }

    @Override
    public ISubscriptionBundle getSubscriptionBundleFromId(UUID bundleId) {
        return bundlesDao.getBundleFromId(bundleId.toString());
    }

    @Override
    public ISubscriptionBundle createSubscriptionBundle(SubscriptionBundle bundle) {
        bundlesDao.insertBundle(bundle);
        return bundle;
    }

    @Override
    public ISubscription getSubscriptionFromId(UUID subscriptionId) {
        return subscriptionsDao.getSubscriptionFromId(subscriptionId.toString());
    }

    @Override
    public ISubscription getBaseSubscription(final UUID bundleId) {

        List<ISubscription> subscriptions = subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString());
        for (ISubscription cur : subscriptions) {
            if (((Subscription)cur).getCategory() == ProductCategory.BASE) {
                return cur;
            }
        }
        return null;
    }

    @Override
    public List<ISubscription> getSubscriptions(UUID bundleId) {
        return subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString());
    }

    @Override
    public List<ISubscription> getSubscriptionsForKey(String bundleKey) {
        ISubscriptionBundle bundle =  bundlesDao.getBundleFromKey(bundleKey);
        if (bundle == null) {
            return Collections.emptyList();
        }
        return subscriptionsDao.getSubscriptionsFromBundleId(bundle.getId().toString());
    }

    @Override
    public void updateSubscription(Subscription subscription) {
        Date ctd = (subscription.getChargedThroughDate() != null)  ? subscription.getChargedThroughDate().toDate() : null;
        Date ptd = (subscription.getPaidThroughDate() != null)  ? subscription.getPaidThroughDate().toDate() : null;
        subscriptionsDao.updateSubscription(subscription.getId().toString(), subscription.getActiveVersion(), ctd, ptd);
    }

    @Override
    public void createNextPhaseEvent(final UUID subscriptionId, final IEntitlementEvent nextPhase) {
        eventsDao.inTransaction(new Transaction<Void, IEventSqlDao>() {

            @Override
            public Void inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                dao.insertEvent(nextPhase);
                return null;
            }
        });
    }


    @Override
    public List<IEntitlementEvent> getEventsForSubscription(UUID subscriptionId) {
        List<IEntitlementEvent> events = eventsDao.getEventsForSubscription(subscriptionId.toString());
        return events;
    }

    @Override
    public List<IEntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId) {
        Date now = clock.getUTCNow().toDate();
        List<IEntitlementEvent> results = eventsDao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
        return results;
    }

    @Override
    public List<IEntitlementEvent> getEventsReady(final UUID ownerId, final int sequenceId) {

        final Date now = clock.getUTCNow().toDate();
        final Date nextAvailable = clock.getUTCNow().plus(config.getDaoClaimTimeMs()).toDate();

        log.debug(String.format("EntitlementDao getEventsReady START effectiveNow =  %s", now));

        List<IEntitlementEvent> events = eventsDao.inTransaction(new Transaction<List<IEntitlementEvent>, IEventSqlDao>() {

            @Override
            public List<IEntitlementEvent> inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {

                List<IEntitlementEvent> claimedEvents = new ArrayList<IEntitlementEvent>();
                List<IEntitlementEvent> input = dao.getReadyEvents(now, config.getDaoMaxReadyEvents());
                for (IEntitlementEvent cur : input) {
                    final boolean claimed = (dao.claimEvent(ownerId.toString(), nextAvailable, cur.getId().toString(), now) == 1);
                    if (claimed) {
                        claimedEvents.add(cur);
                        dao.insertClaimedHistory(sequenceId, ownerId.toString(), hostname, now, cur.getId().toString());
                    }
                }
                return claimedEvents;
            }
        });

        for (IEntitlementEvent cur : events) {
            log.debug(String.format("EntitlementDao %s [host %s] claimed events %s", ownerId, hostname, cur.getId()));
            if (cur.getOwner() != null && !cur.getOwner().equals(ownerId)) {
                log.warn(String.format("EventProcessor %s stealing event %s from %s", ownerId, cur, cur.getOwner()));
            }
        }
        return events;
    }

    @Override
    public void clearEventsReady(final UUID ownerId, final Collection<IEntitlementEvent> cleared) {

        log.debug(String.format("EntitlementDao clearEventsReady START cleared size = %d", cleared.size()));

        eventsDao.inTransaction(new Transaction<Void, IEventSqlDao>() {

            @Override
            public Void inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {
                // STEPH Same here batch would nice
                for (IEntitlementEvent cur : cleared) {
                    dao.clearEvent(cur.getId().toString(), ownerId.toString());
                    log.debug(String.format("EntitlementDao %s [host %s] cleared events %s", ownerId, hostname, cur.getId()));
                }
                return null;
            }
        });
    }

    @Override
    public ISubscription createSubscription(final Subscription subscription,
            final List<IEntitlementEvent> initialEvents) {

        subscriptionsDao.inTransaction(new Transaction<Void, ISubscriptionSqlDao>() {

            @Override
            public Void inTransaction(ISubscriptionSqlDao dao,
                    TransactionStatus status) throws Exception {

                dao.insertSubscription(subscription);
                // STEPH batch as well
                IEventSqlDao eventsDaoFromSameTranscation = dao.become(IEventSqlDao.class);
                for (IEntitlementEvent cur : initialEvents) {
                    eventsDaoFromSameTranscation.insertEvent(cur);
                }
                return null;
            }
        });
        return new Subscription(new SubscriptionBuilder(subscription), true);
    }

    @Override
    public void cancelSubscription(final UUID subscriptionId, final IEntitlementEvent cancelEvent) {

        eventsDao.inTransaction(new Transaction<Void, IEventSqlDao>() {
            @Override
            public Void inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextChangeEventFromTransaction(subscriptionId, dao);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                dao.insertEvent(cancelEvent);
                return null;
            }
        });
    }

    @Override
    public void uncancelSubscription(final UUID subscriptionId, final List<IEntitlementEvent> uncancelEvents) {

        eventsDao.inTransaction(new Transaction<Void, IEventSqlDao>() {

            @Override
            public Void inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {

                UUID existingCancelId = null;
                Date now = clock.getUTCNow().toDate();
                List<IEntitlementEvent> events = dao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);

                for (IEntitlementEvent cur : events) {
                    if (cur.getType() == EventType.API_USER && ((IApiEvent) cur).getEventType() == ApiEventType.CANCEL) {
                        if (existingCancelId != null) {
                            throw new EntitlementError(String.format("Found multiple cancel active events for subscriptions %s", subscriptionId.toString()));
                        }
                        existingCancelId = cur.getId();
                    }
                }

                if (existingCancelId != null) {
                    dao.unactiveEvent(existingCancelId.toString(), now);
                    for (IEntitlementEvent cur : uncancelEvents) {
                        dao.insertEvent(cur);
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void changePlan(final UUID subscriptionId, final List<IEntitlementEvent> changeEvents) {
        eventsDao.inTransaction(new Transaction<Void, IEventSqlDao>() {
            @Override
            public Void inTransaction(IEventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextChangeEventFromTransaction(subscriptionId, dao);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                for (IEntitlementEvent cur : changeEvents) {
                    dao.insertEvent(cur);
                }
                return null;
            }
        });
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final IEventSqlDao dao) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.PHASE, null);
    }

    private void cancelNextChangeEventFromTransaction(final UUID subscriptionId, final IEventSqlDao dao) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CHANGE);
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final IEventSqlDao dao, EventType type, ApiEventType apiType) {

        UUID futureEventId = null;
        Date now = clock.getUTCNow().toDate();
        List<IEntitlementEvent> events = dao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
        for (IEntitlementEvent cur : events) {
            if (cur.getType() == type &&
                    (apiType == null || apiType == ((IApiEvent) cur).getEventType() )) {
                if (futureEventId != null) {
                    throw new EntitlementError(
                            String.format("Found multiple future events for type %s for subscriptions %s",
                                    type, subscriptionId.toString()));
                }
                futureEventId = cur.getId();
            }
        }

        if (futureEventId != null) {
            dao.unactiveEvent(futureEventId.toString(), now);
        }
    }
}
