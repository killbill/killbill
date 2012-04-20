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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;
import org.skife.jdbi.v2.sqlobject.mixins.Transmogrifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.repair.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKey;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.customfield.dao.CustomFieldSqlDao;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

import javax.annotation.Nullable;

public class EntitlementSqlDao implements EntitlementDao {

    private final static Logger log = LoggerFactory.getLogger(EntitlementSqlDao.class);
    public static final String ENTITLEMENT_EVENTS_TABLE_NAME = "entitlement_events";
    public static final String BUNDLES_TABLE_NAME = "bundles";
    public static final String SUBSCRIPTIONS_TABLE_NAME = "subscriptions";

    private final Clock clock;
    private final SubscriptionSqlDao subscriptionsDao;
    private final BundleSqlDao bundlesDao;
    private final EventSqlDao eventsDao;
    private final NotificationQueueService notificationQueueService;
    private final AddonUtils addonUtils;
    private final CustomFieldDao customFieldDao;

    //
    // We are not injecting SubscriptionFactory since that creates circular dependencies--
    // Guice would still work, but this is playing with fire.
    //
    // Instead that factory passed through API top to bottom for the call where is it needed-- where we returned fully rehydrated Subscriptions
    //
    @Inject
    public EntitlementSqlDao(final IDBI dbi, final Clock clock,
            final AddonUtils addonUtils, final NotificationQueueService notificationQueueService,
            final CustomFieldDao customFieldDao) {
        this.clock = clock;
        this.subscriptionsDao = dbi.onDemand(SubscriptionSqlDao.class);
        this.eventsDao = dbi.onDemand(EventSqlDao.class);
        this.bundlesDao = dbi.onDemand(BundleSqlDao.class);
        this.notificationQueueService = notificationQueueService;
        this.addonUtils = addonUtils;
        this.customFieldDao = customFieldDao;
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
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final CallContext context) {
        return bundlesDao.inTransaction(new Transaction<SubscriptionBundle, BundleSqlDao>() {
            @Override
            public SubscriptionBundle inTransaction(BundleSqlDao bundlesDao, TransactionStatus status) {
                bundlesDao.insertBundle(bundle, context);

                AuditSqlDao auditSqlDao = bundlesDao.become(AuditSqlDao.class);
                String bundleId = bundle.getId().toString();
                auditSqlDao.insertAuditFromTransaction(BUNDLES_TABLE_NAME, bundleId, ChangeType.INSERT, context);

                return bundle;
            }
        });
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
    public Subscription getBaseSubscription(final SubscriptionFactory factory, final UUID bundleId) {
        return getBaseSubscription(factory, bundleId, true);
    }

    @Override
    public Subscription getSubscriptionFromId(final SubscriptionFactory factory, final UUID subscriptionId) {
        return buildSubscription(factory, subscriptionsDao.getSubscriptionFromId(subscriptionId.toString()));
    }

    @Override
    public List<Subscription> getSubscriptions(final SubscriptionFactory factory, final UUID bundleId) {
        return buildBundleSubscriptions(factory, subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString()));
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(final SubscriptionFactory factory, final String bundleKey) {
        SubscriptionBundle bundle =  bundlesDao.getBundleFromKey(bundleKey);
        if (bundle == null) {
            return Collections.emptyList();
        }
        return getSubscriptions(factory, bundle.getId());
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final CallContext context) {

        final Date ctd = (subscription.getChargedThroughDate() != null)  ? subscription.getChargedThroughDate().toDate() : null;

        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {
            @Override
            public Void inTransaction(SubscriptionSqlDao transactionalDao,
                    TransactionStatus status) throws Exception {
                transactionalDao.updateChargedThroughDate(subscription.getId().toString(), ctd, context);

                BundleSqlDao tmpDao = transactionalDao.become(BundleSqlDao.class);
                tmpDao.updateBundleLastSysTime(subscription.getBundleId().toString(), clock.getUTCNow().toDate());
                AuditSqlDao auditSqlDao = transactionalDao.become(AuditSqlDao.class);
                String subscriptionId = subscription.getId().toString();
                auditSqlDao.insertAuditFromTransaction(SUBSCRIPTIONS_TABLE_NAME, subscriptionId, ChangeType.UPDATE, context);
                return null;
            }
        });
    }

    @Override
    public void createNextPhaseEvent(final UUID subscriptionId, final EntitlementEvent nextPhase, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextPhaseEventFromTransaction(subscriptionId, dao, context);
                dao.insertEvent(nextPhase, context);
                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, nextPhase.getId().toString(), ChangeType.INSERT, context);

                recordFutureNotificationFromTransaction(dao,
                        nextPhase.getEffectiveDate(),
                        new EntitlementNotificationKey(nextPhase.getId()));
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
        return eventsDao.getEventsForSubscription(subscriptionId.toString());
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId) {

        Map<UUID, List<EntitlementEvent>> result = subscriptionsDao.inTransaction(new Transaction<Map<UUID, List<EntitlementEvent>>, SubscriptionSqlDao>() {
            @Override
            public Map<UUID, List<EntitlementEvent>> inTransaction(SubscriptionSqlDao transactional,
                    TransactionStatus status) throws Exception {
                List<Subscription> subscriptions = transactional.getSubscriptionsFromBundleId(bundleId.toString());
                if (subscriptions.size() == 0) {
                    return Collections.emptyMap();
                }
                EventSqlDao eventsDaoFromSameTransaction = transactional.become(EventSqlDao.class);
                Map<UUID, List<EntitlementEvent>> result = new HashMap<UUID, List<EntitlementEvent>>();
                for (Subscription cur : subscriptions) {
                    List<EntitlementEvent> events = eventsDaoFromSameTransaction.getEventsForSubscription(cur.getId().toString());
                    result.put(cur.getId(), events);
                }
                return result;
            }
        });
        return result;
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(UUID subscriptionId) {
        Date now = clock.getUTCNow().toDate();
        return eventsDao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
    }

    @Override
    public void createSubscription(final SubscriptionData subscription,
            final List<EntitlementEvent> initialEvents, final CallContext context) {

        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {

            @Override
            public Void inTransaction(SubscriptionSqlDao dao,
                    TransactionStatus status) throws Exception {

                dao.insertSubscription(subscription, context);
                // STEPH batch as well
                EventSqlDao eventsDaoFromSameTransaction = dao.become(EventSqlDao.class);
                List<String> eventIds = new ArrayList<String>();

                for (final EntitlementEvent cur : initialEvents) {
                    eventsDaoFromSameTransaction.insertEvent(cur, context);
                    eventIds.add(cur.getId().toString()); // collect ids for batch audit log insert
                    recordFutureNotificationFromTransaction(dao,
                            cur.getEffectiveDate(),
                            new EntitlementNotificationKey(cur.getId()));
                }

                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, eventIds, ChangeType.INSERT, context);
                return null;
            }
        });
    }

    @Override
    public void recreateSubscription(final UUID subscriptionId,
            final List<EntitlementEvent> recreateEvents, final CallContext context) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {

                List<String> eventIds = new ArrayList<String>();
                for (final EntitlementEvent cur : recreateEvents) {
                    dao.insertEvent(cur, context);
                    eventIds.add(cur.getId().toString()); // gather event ids for batch audit insert
                    recordFutureNotificationFromTransaction(dao,
                            cur.getEffectiveDate(),
                            new EntitlementNotificationKey(cur.getId()));

                }

                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, eventIds, ChangeType.INSERT, context);
                return null;
            }
        });
    }

    @Override
    public void cancelSubscription(final UUID subscriptionId, final EntitlementEvent cancelEvent, final CallContext context, final int seqId) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao,
                    TransactionStatus status) throws Exception {
                cancelNextCancelEventFromTransaction(subscriptionId, dao, context);
                cancelNextChangeEventFromTransaction(subscriptionId, dao, context);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao, context);
                dao.insertEvent(cancelEvent, context);
                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                String cancelEventId = cancelEvent.getId().toString();
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, cancelEventId, ChangeType.INSERT, context);

                recordFutureNotificationFromTransaction(dao,
                        cancelEvent.getEffectiveDate(),
                        new EntitlementNotificationKey(cancelEvent.getId(), seqId));
                return null;
            }
        });
    }

    @Override
    public void uncancelSubscription(final UUID subscriptionId, final List<EntitlementEvent> uncancelEvents, final CallContext context) {

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
                    dao.unactiveEvent(existingCancelId.toString(), context);
                    String deactivatedEventId = existingCancelId.toString();

                    List<String> eventIds = new ArrayList<String>();
                    for (final EntitlementEvent cur : uncancelEvents) {
                        dao.insertEvent(cur, context);
                        eventIds.add(cur.getId().toString()); // gather event ids for batch insert into audit log
                        recordFutureNotificationFromTransaction(dao,
                                cur.getEffectiveDate(),
                                new EntitlementNotificationKey(cur.getId()));
                    }

                    AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                    auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, deactivatedEventId, ChangeType.UPDATE, context);
                    auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, eventIds, ChangeType.INSERT, context);
                }
                return null;
            }
        });
    }

    @Override
    public void changePlan(final UUID subscriptionId, final List<EntitlementEvent> changeEvents, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {
            @Override
            public Void inTransaction(EventSqlDao dao, TransactionStatus status) throws Exception {
                cancelNextChangeEventFromTransaction(subscriptionId, dao, context);
                cancelNextPhaseEventFromTransaction(subscriptionId, dao, context);

                List<String> eventIds = new ArrayList<String>();
                for (final EntitlementEvent cur : changeEvents) {
                    dao.insertEvent(cur, context);
                    eventIds.add(cur.getId().toString()); // gather event ids for batch audit log insert

                    recordFutureNotificationFromTransaction(dao,
                            cur.getEffectiveDate(),
                            new EntitlementNotificationKey(cur.getId()));
                }

                AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, eventIds, ChangeType.INSERT, context);
                return null;
            }
        });
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.PHASE, null, context);
    }

    private void cancelNextChangeEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CHANGE, context);
    }

    private void cancelNextCancelEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CANCEL, context);
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final EventSqlDao dao,
            final EventType type, @Nullable final ApiEventType apiType,
            final CallContext context) {

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
            dao.unactiveEvent(futureEventId.toString(), context);

            AuditSqlDao auditSqlDao = dao.become(AuditSqlDao.class);
            auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, futureEventId.toString(), ChangeType.UPDATE, context);
        }
    }

    private void updateCustomFieldsFromTransaction(final SubscriptionSqlDao transactionalDao,
            final SubscriptionData subscription,
            final CallContext context) {
        customFieldDao.saveFields(transactionalDao, subscription.getId(), subscription.getObjectName(), subscription.getFieldList(), context);
    }

    private Subscription buildSubscription(final SubscriptionFactory factory, final Subscription input) {
        if (input == null) {
            return null;
        }
        List<Subscription> bundleInput = new ArrayList<Subscription>();
        if (input.getCategory() == ProductCategory.ADD_ON) {
            Subscription baseSubscription = getBaseSubscription(factory, input.getBundleId(), false);
            bundleInput.add(baseSubscription);
            bundleInput.add(input);
        } else {
            bundleInput.add(input);
        }
        List<Subscription> reloadedSubscriptions = buildBundleSubscriptions(factory, bundleInput);
        for (Subscription cur : reloadedSubscriptions) {
            if (cur.getId().equals(input.getId())) {
                return cur;
            }
        }
        throw new EntitlementError(String.format("Unexpected code path in buildSubscription"));
    }

    private List<Subscription> buildBundleSubscriptions(final SubscriptionFactory factory, final List<Subscription> input) {
        // Make sure BasePlan -- if exists-- is first
        Collections.sort(input, new Comparator<Subscription>() {
            @Override
            public int compare(Subscription o1, Subscription o2) {
                if (o1.getCategory() == ProductCategory.BASE) {
                    return -1;
                } else if (o2.getCategory() == ProductCategory.BASE) {
                    return 1;
                } else {
                    return o1.getStartDate().compareTo(o2.getStartDate());
                }
            }
        });

        EntitlementEvent futureBaseEvent = null;
        List<Subscription> result = new ArrayList<Subscription>(input.size());
        for (Subscription cur : input) {

            List<EntitlementEvent> events = eventsDao.getEventsForSubscription(cur.getId().toString());
            Subscription reloaded = factory.createSubscription(new SubscriptionBuilder((SubscriptionData) cur), events);

            switch (cur.getCategory()) {
            case BASE:
                Collection<EntitlementEvent> futureApiEvents = Collections2.filter(events, new Predicate<EntitlementEvent>() {
                    @Override
                    public boolean apply(EntitlementEvent input) {
                        return (input.getEffectiveDate().isAfter(clock.getUTCNow()) &&
                                ((input instanceof ApiEventCancel) || (input instanceof ApiEventChange)));
                    }
                });
                futureBaseEvent = (futureApiEvents.size() == 0) ? null : futureApiEvents.iterator().next();
                break;

            case ADD_ON:
                Plan targetAddOnPlan = reloaded.getCurrentPlan();
                String baseProductName = (futureBaseEvent instanceof ApiEventChange) ?
                        ((ApiEventChange) futureBaseEvent).getEventPlan() : null;

                        boolean createCancelEvent = (futureBaseEvent != null) &&
                        ((futureBaseEvent instanceof ApiEventCancel) ||
                                ((! addonUtils.isAddonAvailable(baseProductName, futureBaseEvent.getEffectiveDate(), targetAddOnPlan)) ||
                                        (addonUtils.isAddonIncluded(baseProductName, futureBaseEvent.getEffectiveDate(), targetAddOnPlan))));

                        if (createCancelEvent) {
                            DateTime now = clock.getUTCNow();
                            EntitlementEvent addOnCancelEvent = new ApiEventCancel(new ApiEventBuilder()
                            .setSubscriptionId(reloaded.getId())
                            .setActiveVersion(((SubscriptionData) reloaded).getActiveVersion())
                            .setProcessedDate(now)
                            .setEffectiveDate(futureBaseEvent.getEffectiveDate())
                            .setRequestedDate(now)
                            // This event is only there to indicate the ADD_ON is future canceled, but it is not there
                            // on disk until the base plan cancellation becomes effective
                            .setFromDisk(false));

                            events.add(addOnCancelEvent);
                            // Finally reload subscription with full set of events
                            reloaded = factory.createSubscription(new SubscriptionBuilder((SubscriptionData) cur), events);
                        }
                        break;
            default:
                break;
            }
            loadCustomFields((SubscriptionData) reloaded);
            result.add(reloaded);
        }
        return result;
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData, final CallContext context) {

        eventsDao.inTransaction(new Transaction<Void, EventSqlDao>() {

            @Override
            public Void inTransaction(EventSqlDao transEventDao,
                    TransactionStatus status) throws Exception {

                SubscriptionSqlDao transSubDao = transEventDao.become(SubscriptionSqlDao.class);
                BundleSqlDao transBundleDao = transEventDao.become(BundleSqlDao.class);

                List<String> bundleIds = new ArrayList<String>();
                List<String> subscriptionIds = new ArrayList<String>();
                List<String> eventIds = new ArrayList<String>();

                for (BundleMigrationData curBundle : accountData.getData()) {
                    SubscriptionBundleData bundleData = curBundle.getData();

                    for (SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {

                        SubscriptionData subData = curSubscription.getData();
                        for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                            transEventDao.insertEvent(curEvent, context);
                            eventIds.add(curEvent.getId().toString()); // gather event ids for batch audit

                            recordFutureNotificationFromTransaction(transEventDao,
                                    curEvent.getEffectiveDate(),
                                    new EntitlementNotificationKey(curEvent.getId()));
                        }
                        transSubDao.insertSubscription(subData, context);
                        subscriptionIds.add(subData.getId().toString()); // gather subscription ids for batch audit
                    }
                    transBundleDao.insertBundle(bundleData, context);
                    bundleIds.add(bundleData.getId().toString()); // gather bundle ids for batch audit
                }

                // add audit records for bundles, subscriptions, and events
                AuditSqlDao auditSqlDao = transBundleDao.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction(SUBSCRIPTIONS_TABLE_NAME, subscriptionIds, ChangeType.INSERT, context);
                auditSqlDao.insertAuditFromTransaction(BUNDLES_TABLE_NAME, bundleIds, ChangeType.INSERT, context);
                auditSqlDao.insertAuditFromTransaction(ENTITLEMENT_EVENTS_TABLE_NAME, eventIds, ChangeType.INSERT, context);

                return null;
            }
        });
    }

    public void repair(final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final CallContext context) {
        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {

            @Override
            public Void inTransaction(SubscriptionSqlDao transactional,
                    TransactionStatus status) throws Exception {

                EventSqlDao transEventDao = transactional.become(EventSqlDao.class);
                for (SubscriptionDataRepair cur : inRepair) {
                    transactional.updateActiveVersion(cur.getId().toString(), cur.getActiveVersion(), context);
                    for (EntitlementEvent event : cur.getInitialEvents()) {
                        transEventDao.updateVersion(event.getId().toString(), cur.getActiveVersion(), context);
                    }
                    for (EntitlementEvent event : cur.getNewEvents()) {
                        transEventDao.insertEvent(event, context);
                    }
                }
                return null;
            }
        });
    }


    private Subscription getBaseSubscription(final SubscriptionFactory factory, final UUID bundleId, boolean rebuildSubscription) {
        List<Subscription> subscriptions = subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString());
        for (Subscription cur : subscriptions) {
            if (cur.getCategory() == ProductCategory.BASE) {
                return  rebuildSubscription ? buildSubscription(factory, cur) : cur;
            }
        }
        return null;
    }

    private void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime effectiveDate, final NotificationKey notificationKey) {
        try {
            NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                    Engine.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(transactionalDao, effectiveDate, notificationKey);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveCustomFields(final SubscriptionData subscription, final CallContext context) {
        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {
            @Override
            public Void inTransaction(SubscriptionSqlDao transactionalDao,
                    TransactionStatus status) throws Exception {
                updateCustomFieldsFromTransaction(transactionalDao, subscription, context);
                return null;
            }
        });
    }

    private void loadCustomFields(final SubscriptionData subscription) {
        CustomFieldSqlDao customFieldSqlDao = subscriptionsDao.become(CustomFieldSqlDao.class);
        List<CustomField> fields = customFieldSqlDao.load(subscription.getId().toString(), subscription.getObjectName());
        subscription.clearFields();
        if (fields != null) {
            subscription.setFields(fields);
        }
    }
}
