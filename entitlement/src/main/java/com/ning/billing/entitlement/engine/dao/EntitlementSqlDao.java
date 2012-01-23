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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transactional;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.SubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import sun.jkernel.Bundle;


public class EntitlementSqlDao implements EntitlementDao {

    private final static Logger log = LoggerFactory.getLogger(EntitlementSqlDao.class);

    private final Clock clock;
    private final SubscriptionSqlDao subscriptionsDao;
    private final BundleSqlDao bundlesDao;
    private final EventSqlDao eventsDao;
    private final SubscriptionFactory factory;
    private final NotificationQueueService notificationQueueService;

    @Inject
    public EntitlementSqlDao(final IDBI dbi, final Clock clock, final SubscriptionFactory factory,
                             final NotificationQueueService notificationQueueService) {
        this.clock = clock;
        this.factory = factory;
        this.subscriptionsDao = dbi.onDemand(SubscriptionSqlDao.class);
        this.eventsDao = dbi.onDemand(EventSqlDao.class);
        this.bundlesDao = dbi.onDemand(BundleSqlDao.class);
        this.notificationQueueService = notificationQueueService;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey) {
        return bundlesDao.getBundleFromKey(bundleKey);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(
            final UUID accountId) {
        return bundlesDao.getBundleFromAccount(accountId.toString());
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId) {
        return bundlesDao.getBundleFromId(bundleId.toString());
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle) {
        return bundlesDao.inTransaction(new Transaction<SubscriptionBundle, BundleSqlDao>() {
            @Override
            public SubscriptionBundle inTransaction(BundleSqlDao bundlesDao, TransactionStatus status) {
                bundlesDao.insertBundle(bundle);
                return bundle;
            }
        });
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID subscriptionId) {
        return buildSubscription(subscriptionsDao.getSubscriptionFromId(subscriptionId.toString()));
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        Subscription subscription = subscriptionsDao.getSubscriptionFromId(subscriptionId.toString());
        if (subscription == null) {
            log.error(String.format(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getFormat(), subscriptionId.toString()));
            return null;
        }

        UUID bundleId = subscription.getBundleId();
        if (bundleId == null) {
            log.error(String.format(ErrorCode.ENT_GET_NO_BUNDLE_FOR_SUBSCRIPTION.getFormat(), subscriptionId.toString()));
            return null;
        }

        SubscriptionBundle bundle = bundlesDao.getBundleFromId(bundleId.toString());
        if (bundle == null) {
            log.error(String.format(ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getFormat(), bundleId.toString()));
            return null;
        }

        return bundle.getAccountId();
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId) {

        List<Subscription> subscriptions = subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString());
        for (Subscription cur : subscriptions) {
            if (((SubscriptionData)cur).getCategory() == ProductCategory.BASE) {
                return  buildSubscription(cur);
            }
        }
        return null;
    }

    @Override
    public List<Subscription> getSubscriptions(UUID bundleId) {
        return buildSubscription(subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString()));
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(String bundleKey) {
        SubscriptionBundle bundle =  bundlesDao.getBundleFromKey(bundleKey);
        if (bundle == null) {
            return Collections.emptyList();
        }
        return buildSubscription(subscriptionsDao.getSubscriptionsFromBundleId(bundle.getId().toString()));
    }

    @Override
    public void updateSubscription(SubscriptionData subscription) {
        Date ctd = (subscription.getChargedThroughDate() != null)  ? subscription.getChargedThroughDate().toDate() : null;
        Date ptd = (subscription.getPaidThroughDate() != null)  ? subscription.getPaidThroughDate().toDate() : null;
        subscriptionsDao.updateSubscription(subscription.getId().toString(), subscription.getActiveVersion(), ctd, ptd);
    }

    @Override
    public void createNextPhaseEvent(final UUID subscriptionId, final EntitlementEvent nextPhase) {
        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {

            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                dao.insertEvent(nextPhase);
                recordFutureNotificationFromTransaction(dao,
                        nextPhase.getEffectiveDate(),
                        new NotificationKey() {
                            @Override
                            public String toString() {
                                return nextPhase.getId().toString();
                            }
                        });
                return null;
            }
        });
    }

    @Override
    public EntitlementEvent getEventById(UUID eventId) {
        return eventsDao.getEventById(eventId.toString());
    }


    @Override
    public List<EntitlementEvent> getEventsForSubscription(UUID subscriptionId) {
        List<EntitlementEvent> events = eventsDao.getEventsForSubscription(subscriptionId.toString());
        return events;
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId) {
        Date now = clock.getUTCNow().toDate();
        List<EntitlementEvent> results = eventsDao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
        return results;
    }


    @Override
    public void createSubscription(final SubscriptionData subscription,
            final List<EntitlementEvent> initialEvents) {

        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {

            @Override
            public Void inTransaction(SubscriptionSqlDao dao,
                    TransactionStatus status) throws Exception {

                dao.insertSubscription(subscription);
                // STEPH batch as well
                EventSqlDao eventsDaoFromSameTranscation = dao.become(EventSqlDao.class);
                for (final EntitlementEvent cur : initialEvents) {
                    eventsDaoFromSameTranscation.insertEvent(cur);
                    recordFutureNotificationFromTransaction(dao,
                            cur.getEffectiveDate(),
                            new NotificationKey() {
                                @Override
                                public String toString() {
                                    return cur.getId().toString();
                                }
                            });
                }
                return null;
            }
        });
    }

    @Override
    public void cancelSubscription(final UUID subscriptionId, final EntitlementEvent cancelEvent) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextChangeEventFromTransaction(subscriptionId, dao);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                dao.insertEvent(cancelEvent);
                recordFutureNotificationFromTransaction(dao,
                        cancelEvent.getEffectiveDate(),
                        new NotificationKey() {
                            @Override
                            public String toString() {
                                return cancelEvent.getId().toString();
                            }
                        });
                return null;
            }
        });
    }

    @Override
    public void uncancelSubscription(final UUID subscriptionId, final List<EntitlementEvent> uncancelEvents) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {

            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {

                UUID existingCancelId = null;
                Date now = clock.getUTCNow().toDate();
                List<EntitlementEvent> events = dao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);

                for (EntitlementEvent cur : events) {
                    if (cur.getType() == EventType.API_USER && ((ApiEvent) cur).getEventType() == ApiEventType.CANCEL) {
                        if (existingCancelId != null) {
                            throw new EntitlementError(String.format("Found multiple cancel active events for subscriptions %s", subscriptionId.toString()));
                        }
                        existingCancelId = cur.getId();
                    }
                }

                if (existingCancelId != null) {
                    dao.unactiveEvent(existingCancelId.toString(), now);
                    for (final EntitlementEvent cur : uncancelEvents) {
                        dao.insertEvent(cur);
                        recordFutureNotificationFromTransaction(dao,
                                cur.getEffectiveDate(),
                                new NotificationKey() {
                            @Override
                            public String toString() {
                                return cur.getId().toString();
                            }
                        });
                    }
                }
                return null;
            }
        });
    }

    @Override
    public void changePlan(final UUID subscriptionId, final List<EntitlementEvent> changeEvents) {
        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextChangeEventFromTransaction(subscriptionId, dao);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao);
                for (final EntitlementEvent cur : changeEvents) {
                    dao.insertEvent(cur);
                    recordFutureNotificationFromTransaction(dao,
                            cur.getEffectiveDate(),
                            new NotificationKey() {
                        @Override
                        public String toString() {
                            return cur.getId().toString();
                        }
                    });
                }
                return null;
            }
        });
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.PHASE, null);
    }

    private void cancelNextChangeEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CHANGE);
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao, EventType type, ApiEventType apiType) {

        UUID futureEventId = null;
        Date now = clock.getUTCNow().toDate();
        List<EntitlementEvent> events = dao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
        for (EntitlementEvent cur : events) {
            if (cur.getType() == type &&
                    (apiType == null || apiType == ((ApiEvent) cur).getEventType() )) {
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

    private Subscription buildSubscription(Subscription input) {
        if (input == null) {
            return null;
        }
        return buildSubscription(Collections.singletonList(input)).get(0);
    }

    private List<Subscription> buildSubscription(List<Subscription> input) {
        List<Subscription> result = new ArrayList<Subscription>(input.size());
        for (Subscription cur : input) {
            List<EntitlementEvent> events = eventsDao.getEventsForSubscription(cur.getId().toString());
            Subscription reloaded =   factory.createSubscription(new SubscriptionBuilder((SubscriptionData) cur), events);
            result.add(reloaded);
        }
        return result;
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {

            @Override
            public Void inTransaction(EventSqlDao transEventDao,
                    TransactionStatus status) throws Exception {

                SubscriptionSqlDao transSubDao = transEventDao.become(SubscriptionSqlDao.class);
                BundleSqlDao transBundleDao = transEventDao.become(BundleSqlDao.class);

                // First get rid of any data from account
                undoMigrationFromTransaction(accountId, transEventDao, transBundleDao, transSubDao);

                for (BundleMigrationData curBundle : accountData.getData()) {
                    SubscriptionBundleData bundleData = curBundle.getData();
                    for (SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {
                        SubscriptionData subData = curSubscription.getData();
                        for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                            transEventDao.insertEvent(curEvent);
                            recordFutureNotificationFromTransaction(transEventDao,
                                    curEvent.getEffectiveDate(),
                                    new NotificationKey() {
                                @Override
                                public String toString() {
                                    return curEvent.getId().toString();
                                }
                            });
                        }
                        transSubDao.insertSubscription(subData);
                    }
                    transBundleDao.insertBundle(bundleData);
                }
                return null;
            }
        });
    }


    @Override
    public void undoMigration(final UUID accountId) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {

            @Override
            public Void inTransaction(EventSqlDao transEventDao,
                    TransactionStatus status) throws Exception {

                SubscriptionSqlDao transSubDao = transEventDao.become(SubscriptionSqlDao.class);
                BundleSqlDao transBundleDao = transEventDao.become(BundleSqlDao.class);
                undoMigrationFromTransaction(accountId, transEventDao, transBundleDao, transSubDao);
                return null;
            }
        });
    }

    private void undoMigrationFromTransaction(final UUID accountId, EventSqlDao transEventDao, BundleSqlDao transBundleDao, SubscriptionSqlDao transSubDao) {
        final List<SubscriptionBundle> bundles = transBundleDao.getBundleFromAccount(accountId.toString());
        for (SubscriptionBundle curBundle : bundles) {
            List<Subscription> subscriptions = transSubDao.getSubscriptionsFromBundleId(curBundle.getId().toString());
            for (Subscription cur : subscriptions) {
                eventsDao.removeEvents(cur.getId().toString());
                transSubDao.removeSubscription(cur.getId().toString());
            }
            transBundleDao.removeBundle(curBundle.getId().toString());
        }
    }

    private void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime effectiveDate, final NotificationKey notificationKey) {
        try {
            NotificationQueue subscritionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                Engine.NOTIFICATION_QUEUE_NAME);
            subscritionEventQueue.recordFutureNotificationFromTransaction(transactionalDao, effectiveDate, notificationKey);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        }
    }
}
