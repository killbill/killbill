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

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.timeline.DefaultRepairEntitlementEvent;
import com.ning.billing.entitlement.api.timeline.RepairEntitlementEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.user.DefaultRequestedSubscriptionEvent;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
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
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.Bus.EventBusException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;

public class AuditedEntitlementDao implements EntitlementDao {
    private static final Logger log = LoggerFactory.getLogger(AuditedEntitlementDao.class);

    private final Clock clock;
    private final SubscriptionSqlDao subscriptionsDao;
    private final BundleSqlDao bundlesDao;
    private final EntitlementEventSqlDao eventsDao;
    private final NotificationQueueService notificationQueueService;
    private final AddonUtils addonUtils;
    private final Bus eventBus;
    private final CatalogService catalogService;

    @Inject
    public AuditedEntitlementDao(final IDBI dbi, final Clock clock, final AddonUtils addonUtils,
                                 final NotificationQueueService notificationQueueService, final Bus eventBus, final CatalogService catalogService) {
        this.clock = clock;
        this.subscriptionsDao = dbi.onDemand(SubscriptionSqlDao.class);
        this.eventsDao = dbi.onDemand(EntitlementEventSqlDao.class);
        this.bundlesDao = dbi.onDemand(BundleSqlDao.class);
        this.notificationQueueService = notificationQueueService;
        this.addonUtils = addonUtils;
        this.eventBus = eventBus;
        this.catalogService = catalogService;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromKey(final String bundleKey) {
        return bundlesDao.getBundleFromKey(bundleKey);
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId) {
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
            public SubscriptionBundle inTransaction(final BundleSqlDao transactional, final TransactionStatus status) {
                bundlesDao.insertBundle(bundle, context);
                final Long recordId = bundlesDao.getRecordId(bundle.getId().toString());

                final EntityAudit audit = new EntityAudit(TableName.BUNDLES, recordId, ChangeType.INSERT);
                bundlesDao.insertAuditFromTransaction(audit, context);

                return bundle;
            }
        });
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId) {
        final Subscription subscription = subscriptionsDao.getSubscriptionFromId(subscriptionId.toString());
        if (subscription == null) {
            log.error(String.format(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getFormat(), subscriptionId.toString()));
            return null;
        }

        final UUID bundleId = subscription.getBundleId();
        if (bundleId == null) {
            log.error(String.format(ErrorCode.ENT_GET_NO_BUNDLE_FOR_SUBSCRIPTION.getFormat(), subscriptionId.toString()));
            return null;
        }

        final SubscriptionBundle bundle = bundlesDao.getBundleFromId(bundleId.toString());
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
        return buildBundleSubscriptions(bundleId, factory, subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString()));
    }

    @Override
    public List<Subscription> getSubscriptionsForKey(final SubscriptionFactory factory, final String bundleKey) {
        final SubscriptionBundle bundle = bundlesDao.getBundleFromKey(bundleKey);
        if (bundle == null) {
            return Collections.emptyList();
        }

        return getSubscriptions(factory, bundle.getId());
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final CallContext context) {
        final Date ctd = (subscription.getChargedThroughDate() != null) ? subscription.getChargedThroughDate().toDate() : null;

        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {
            @Override
            public Void inTransaction(final SubscriptionSqlDao transactionalDao, final TransactionStatus status) throws Exception {
                final String subscriptionId = subscription.getId().toString();
                transactionalDao.updateChargedThroughDate(subscription.getId().toString(), ctd, context);
                final Long subscriptionRecordId = transactionalDao.getRecordId(subscriptionId);
                final EntityAudit subscriptionAudit = new EntityAudit(TableName.SUBSCRIPTIONS, subscriptionRecordId, ChangeType.UPDATE);
                transactionalDao.insertAuditFromTransaction(subscriptionAudit, context);

                final BundleSqlDao bundleSqlDao = transactionalDao.become(BundleSqlDao.class);
                final String bundleId = subscription.getBundleId().toString();
                bundleSqlDao.updateBundleLastSysTime(bundleId, clock.getUTCNow().toDate());

                final Long recordId = bundleSqlDao.getRecordId(bundleId);
                final EntityAudit bundleAudit = new EntityAudit(TableName.BUNDLES, recordId, ChangeType.UPDATE);
                bundleSqlDao.insertAuditFromTransaction(bundleAudit, context);

                return null;
            }
        });
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final EntitlementEvent nextPhase, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional, final TransactionStatus status) throws Exception {
                final UUID subscriptionId = subscription.getId();
                cancelNextPhaseEventFromTransaction(subscriptionId, transactional, context);
                transactional.insertEvent(nextPhase, context);

                final Long recordId = transactional.getRecordId(nextPhase.getId().toString());
                final EntityAudit audit = new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                recordFutureNotificationFromTransaction(transactional,
                                                        nextPhase.getEffectiveDate(),
                                                        new EntitlementNotificationKey(nextPhase.getId()));

                // Notify the Bus of the requested change
                notifyBusOfRequestedChange(transactional, subscription, nextPhase);

                return null;
            }
        });
    }

    @Override
    public EntitlementEvent getEventById(final UUID eventId) {
        return eventsDao.getEventById(eventId.toString());
    }

    @Override
    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId) {
        return eventsDao.getEventsForSubscription(subscriptionId.toString());
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId) {
        return subscriptionsDao.inTransaction(new Transaction<Map<UUID, List<EntitlementEvent>>, SubscriptionSqlDao>() {
            @Override
            public Map<UUID, List<EntitlementEvent>> inTransaction(final SubscriptionSqlDao transactional,
                                                                   final TransactionStatus status) throws Exception {
                final List<Subscription> subscriptions = transactional.getSubscriptionsFromBundleId(bundleId.toString());
                if (subscriptions.size() == 0) {
                    return Collections.emptyMap();
                }

                final EntitlementEventSqlDao eventsDaoFromSameTransaction = transactional.become(EntitlementEventSqlDao.class);
                final Map<UUID, List<EntitlementEvent>> result = new HashMap<UUID, List<EntitlementEvent>>();
                for (final Subscription cur : subscriptions) {
                    final List<EntitlementEvent> events = eventsDaoFromSameTransaction.getEventsForSubscription(cur.getId().toString());
                    result.put(cur.getId(), events);
                }

                return result;
            }
        });
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(final UUID subscriptionId) {
        final Date now = clock.getUTCNow().toDate();
        return eventsDao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
    }

    @Override
    public void createSubscription(final SubscriptionData subscription, final List<EntitlementEvent> initialEvents, final CallContext context) {
        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {
            @Override
            public Void inTransaction(final SubscriptionSqlDao transactional, final TransactionStatus status) throws Exception {
                transactional.insertSubscription(subscription, context);

                final Long subscriptionRecordId = transactional.getRecordId(subscription.getId().toString());
                final EntityAudit audit = new EntityAudit(TableName.SUBSCRIPTIONS, subscriptionRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                // STEPH batch as well
                final EntitlementEventSqlDao eventsDaoFromSameTransaction = transactional.become(EntitlementEventSqlDao.class);
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                for (final EntitlementEvent cur : initialEvents) {
                    eventsDaoFromSameTransaction.insertEvent(cur, context);
                    final Long recordId = eventsDaoFromSameTransaction.getRecordId(cur.getId().toString());
                    audits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT));
                    recordFutureNotificationFromTransaction(transactional,
                                                            cur.getEffectiveDate(),
                                                            new EntitlementNotificationKey(cur.getId()));
                }

                eventsDaoFromSameTransaction.insertAuditFromTransaction(audits, context);

                // Notify the Bus of the latest requested change, if needed
                if (initialEvents.size() > 0) {
                    notifyBusOfRequestedChange(eventsDaoFromSameTransaction, subscription, initialEvents.get(initialEvents.size() - 1));
                }

                return null;
            }
        });
    }

    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<EntitlementEvent> recreateEvents, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional,
                                      final TransactionStatus status) throws Exception {
                final List<EntityAudit> audits = new ArrayList<EntityAudit>();
                for (final EntitlementEvent cur : recreateEvents) {
                    transactional.insertEvent(cur, context);
                    final Long recordId = transactional.getRecordId(cur.getId().toString());
                    audits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT));
                    recordFutureNotificationFromTransaction(transactional,
                                                            cur.getEffectiveDate(),
                                                            new EntitlementNotificationKey(cur.getId()));

                }

                transactional.insertAuditFromTransaction(audits, context);

                // Notify the Bus of the latest requested change
                notifyBusOfRequestedChange(transactional, subscription, recreateEvents.get(recreateEvents.size() - 1));

                return null;
            }
        });
    }

    @Override
    public void cancelSubscription(final SubscriptionData subscription, final EntitlementEvent cancelEvent, final CallContext context, final int seqId) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional, final TransactionStatus status) throws Exception {
                final UUID subscriptionId = subscription.getId();
                cancelNextCancelEventFromTransaction(subscriptionId, transactional, context);
                cancelNextChangeEventFromTransaction(subscriptionId, transactional, context);
                cancelNextPhaseEventFromTransaction(subscriptionId, transactional, context);
                transactional.insertEvent(cancelEvent, context);
                final String cancelEventId = cancelEvent.getId().toString();

                final Long recordId = transactional.getRecordId(cancelEventId);
                final EntityAudit audit = new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                recordFutureNotificationFromTransaction(transactional,
                                                        cancelEvent.getEffectiveDate(),
                                                        new EntitlementNotificationKey(cancelEvent.getId(), seqId));

                // Notify the Bus of the requested change
                notifyBusOfRequestedChange(transactional, subscription, cancelEvent);

                return null;
            }
        });
    }

    @Override
    public void uncancelSubscription(final SubscriptionData subscription, final List<EntitlementEvent> uncancelEvents, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional, final TransactionStatus status) throws Exception {
                final UUID subscriptionId = subscription.getId();
                EntitlementEvent cancelledEvent = null;
                final Date now = clock.getUTCNow().toDate();
                final List<EntitlementEvent> events = transactional.getFutureActiveEventForSubscription(subscriptionId.toString(), now);

                for (final EntitlementEvent cur : events) {
                    if (cur.getType() == EventType.API_USER && ((ApiEvent) cur).getEventType() == ApiEventType.CANCEL) {
                        if (cancelledEvent != null) {
                            throw new EntitlementError(String.format("Found multiple cancel active events for subscriptions %s", subscriptionId.toString()));
                        }
                        cancelledEvent = cur;
                    }
                }

                if (cancelledEvent != null) {
                    final List<EntityAudit> eventAudits = new ArrayList<EntityAudit>();

                    final String cancelledEventId = cancelledEvent.getId().toString();
                    transactional.unactiveEvent(cancelledEventId, context);
                    final Long cancelledRecordId = transactional.getRecordId(cancelledEventId);
                    eventAudits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, cancelledRecordId, ChangeType.UPDATE));

                    for (final EntitlementEvent cur : uncancelEvents) {
                        transactional.insertEvent(cur, context);
                        final Long recordId = transactional.getRecordId(cur.getId().toString());
                        eventAudits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT));
                        recordFutureNotificationFromTransaction(transactional,
                                                                cur.getEffectiveDate(),
                                                                new EntitlementNotificationKey(cur.getId()));
                    }

                    transactional.insertAuditFromTransaction(eventAudits, context);

                    // Notify the Bus of the latest requested change
                    notifyBusOfRequestedChange(transactional, subscription, uncancelEvents.get(uncancelEvents.size() - 1));
                }

                return null;
            }
        });
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<EntitlementEvent> changeEvents, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional, final TransactionStatus status) throws Exception {
                final UUID subscriptionId = subscription.getId();
                cancelNextChangeEventFromTransaction(subscriptionId, transactional, context);
                cancelNextPhaseEventFromTransaction(subscriptionId, transactional, context);

                final List<EntityAudit> eventAudits = new ArrayList<EntityAudit>();
                for (final EntitlementEvent cur : changeEvents) {
                    transactional.insertEvent(cur, context);
                    final Long recordId = transactional.getRecordId(cur.getId().toString());
                    eventAudits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT));

                    recordFutureNotificationFromTransaction(transactional,
                                                            cur.getEffectiveDate(),
                                                            new EntitlementNotificationKey(cur.getId()));
                }

                transactional.insertAuditFromTransaction(eventAudits, context);

                // Notify the Bus of the latest requested change
                final EntitlementEvent finalEvent = changeEvents.get(changeEvents.size() - 1);
                notifyBusOfRequestedChange(transactional, subscription, finalEvent);

                return null;
            }
        });
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final EntitlementEventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.PHASE, null, context);
    }

    private void cancelNextChangeEventFromTransaction(final UUID subscriptionId, final EntitlementEventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CHANGE, context);
    }

    private void cancelNextCancelEventFromTransaction(final UUID subscriptionId, final EntitlementEventSqlDao dao, final CallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, dao, EventType.API_USER, ApiEventType.CANCEL, context);
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final EntitlementEventSqlDao dao, final EventType type,
                                                  @Nullable final ApiEventType apiType, final CallContext context) {
        EntitlementEvent futureEvent = null;
        final Date now = clock.getUTCNow().toDate();
        final List<EntitlementEvent> events = dao.getFutureActiveEventForSubscription(subscriptionId.toString(), now);
        for (final EntitlementEvent cur : events) {
            if (cur.getType() == type &&
                    (apiType == null || apiType == ((ApiEvent) cur).getEventType())) {
                if (futureEvent != null) {
                    throw new EntitlementError(String.format("Found multiple future events for type %s for subscriptions %s",
                                                             type, subscriptionId.toString()));
                }
                futureEvent = cur;
            }
        }

        if (futureEvent != null) {
            final String eventId = futureEvent.getId().toString();
            dao.unactiveEvent(eventId, context);
            final Long recordId = dao.getRecordId(eventId);
            final EntityAudit audit = new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.UPDATE);
            dao.insertAuditFromTransaction(audit, context);
        }
    }

    private Subscription buildSubscription(final SubscriptionFactory factory, final Subscription input) {
        if (input == null) {
            return null;
        }

        final List<Subscription> bundleInput = new ArrayList<Subscription>();
        if (input.getCategory() == ProductCategory.ADD_ON) {
            final Subscription baseSubscription = getBaseSubscription(factory, input.getBundleId(), false);
            bundleInput.add(baseSubscription);
            bundleInput.add(input);
        } else {
            bundleInput.add(input);
        }

        final List<Subscription> reloadedSubscriptions = buildBundleSubscriptions(input.getBundleId(), factory, bundleInput);
        for (final Subscription cur : reloadedSubscriptions) {
            if (cur.getId().equals(input.getId())) {
                return cur;
            }
        }

        throw new EntitlementError("Unexpected code path in buildSubscription");
    }

    private List<Subscription> buildBundleSubscriptions(final UUID bundleId, final SubscriptionFactory factory, final List<Subscription> input) {
        if (input == null || input.size() == 0) {
            return Collections.emptyList();
        }

        // Make sure BasePlan -- if exists-- is first
        Collections.sort(input, new Comparator<Subscription>() {
            @Override
            public int compare(final Subscription o1, final Subscription o2) {

                //
                // STEPH production NPE needs debugging:
                //
                if (o1 == null) {
                    log.error("o1 :Unexpected null subscription in compareMethod for bundle {}, input.size() = {}", bundleId, input.size());
                }
                if (o2 == null) {
                    log.error("o2 :Unexpected null subscription in compareMethod for bundle {}, input.size() = {}", bundleId, input.size());
                }


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
        final List<Subscription> result = new ArrayList<Subscription>(input.size());
        for (final Subscription cur : input) {
            final List<EntitlementEvent> events = eventsDao.getEventsForSubscription(cur.getId().toString());
            Subscription reloaded = factory.createSubscription(new SubscriptionBuilder((SubscriptionData) cur), events);

            switch (cur.getCategory()) {
                case BASE:
                    final Collection<EntitlementEvent> futureApiEvents = Collections2.filter(events, new Predicate<EntitlementEvent>() {
                        @Override
                        public boolean apply(final EntitlementEvent input) {
                            return (input.getEffectiveDate().isAfter(clock.getUTCNow()) &&
                                    ((input instanceof ApiEventCancel) || (input instanceof ApiEventChange)));
                        }
                    });
                    futureBaseEvent = (futureApiEvents.size() == 0) ? null : futureApiEvents.iterator().next();
                    break;

                case ADD_ON:
                    final Plan targetAddOnPlan = reloaded.getCurrentPlan();
                    final String baseProductName = (futureBaseEvent instanceof ApiEventChange) ?
                            ((ApiEventChange) futureBaseEvent).getEventPlan() : null;

                    final boolean createCancelEvent = (futureBaseEvent != null) &&
                            ((futureBaseEvent instanceof ApiEventCancel) ||
                                    ((!addonUtils.isAddonAvailableFromPlanName(baseProductName, futureBaseEvent.getEffectiveDate(), targetAddOnPlan)) ||
                                            (addonUtils.isAddonIncludedFromPlanName(baseProductName, futureBaseEvent.getEffectiveDate(), targetAddOnPlan))));

                    if (createCancelEvent) {
                        final DateTime now = clock.getUTCNow();
                        final EntitlementEvent addOnCancelEvent = new ApiEventCancel(new ApiEventBuilder()
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

            result.add(reloaded);
        }

        return result;
    }

    @Override
    public void migrate(final UUID accountId, final AccountMigrationData accountData, final CallContext context) {
        eventsDao.inTransaction(new Transaction<Void, EntitlementEventSqlDao>() {
            @Override
            public Void inTransaction(final EntitlementEventSqlDao transactional, final TransactionStatus status) throws Exception {
                final SubscriptionSqlDao transSubDao = transactional.become(SubscriptionSqlDao.class);
                final BundleSqlDao transBundleDao = transactional.become(BundleSqlDao.class);

                final List<EntityAudit> audits = new ArrayList<EntityAudit>();

                Long recordId;

                for (final BundleMigrationData curBundle : accountData.getData()) {
                    final SubscriptionBundleData bundleData = curBundle.getData();

                    for (final SubscriptionMigrationData curSubscription : curBundle.getSubscriptions()) {
                        final SubscriptionData subData = curSubscription.getData();
                        for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                            transactional.insertEvent(curEvent, context);
                            recordId = transactional.getRecordId(curEvent.getId().toString());
                            audits.add(new EntityAudit(TableName.SUBSCRIPTION_EVENTS, recordId, ChangeType.INSERT));

                            recordFutureNotificationFromTransaction(transactional,
                                                                    curEvent.getEffectiveDate(),
                                                                    new EntitlementNotificationKey(curEvent.getId()));
                        }
                        transSubDao.insertSubscription(subData, context);
                        recordId = transSubDao.getRecordId(subData.getId().toString());
                        audits.add(new EntityAudit(TableName.SUBSCRIPTIONS, recordId, ChangeType.INSERT));

                        // Notify the Bus of the latest requested change
                        final EntitlementEvent finalEvent = curSubscription.getInitialEvents().get(curSubscription.getInitialEvents().size() - 1);
                        notifyBusOfRequestedChange(transactional, subData, finalEvent);
                    }

                    transBundleDao.insertBundle(bundleData, context);
                    recordId = transBundleDao.getRecordId(bundleData.getId().toString());
                    audits.add(new EntityAudit(TableName.BUNDLES, recordId, ChangeType.INSERT));
                }

                // add audit records for bundles, subscriptions, and events
                transSubDao.insertAuditFromTransaction(audits, context);

                return null;
            }
        });
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final CallContext context) {
        subscriptionsDao.inTransaction(new Transaction<Void, SubscriptionSqlDao>() {
            @Override
            public Void inTransaction(final SubscriptionSqlDao transactional, final TransactionStatus status) throws Exception {
                final EntitlementEventSqlDao transEventDao = transactional.become(EntitlementEventSqlDao.class);
                for (final SubscriptionDataRepair cur : inRepair) {
                    transactional.updateForRepair(cur.getId().toString(), cur.getActiveVersion(), cur.getStartDate().toDate(), cur.getBundleStartDate().toDate(), context);
                    for (final EntitlementEvent event : cur.getInitialEvents()) {
                        transEventDao.updateVersion(event.getId().toString(), event.getActiveVersion(), context);
                    }
                    for (final EntitlementEvent event : cur.getNewEvents()) {
                        transEventDao.insertEvent(event, context);
                        if (event.getEffectiveDate().isAfter(clock.getUTCNow())) {
                            recordFutureNotificationFromTransaction(transactional,
                                                                    event.getEffectiveDate(),
                                                                    new EntitlementNotificationKey(event.getId()));
                        }
                    }
                }

                try {
                    // Note: we don't send a requested change event here, but a repair event
                    final RepairEntitlementEvent busEvent = new DefaultRepairEntitlementEvent(context.getUserToken(), accountId, bundleId, clock.getUTCNow());
                    eventBus.postFromTransaction(busEvent, transactional);
                } catch (EventBusException e) {
                    log.warn("Failed to post repair entitlement event for bundle " + bundleId, e);
                }

                return null;
            }
        });
    }

    private Subscription getBaseSubscription(final SubscriptionFactory factory, final UUID bundleId, final boolean rebuildSubscription) {
        final List<Subscription> subscriptions = subscriptionsDao.getSubscriptionsFromBundleId(bundleId.toString());
        for (final Subscription cur : subscriptions) {
            if (cur.getCategory() == ProductCategory.BASE) {
                return rebuildSubscription ? buildSubscription(factory, cur) : cur;
            }
        }

        return null;
    }

    private void recordFutureNotificationFromTransaction(final Transmogrifier transactionalDao, final DateTime effectiveDate, final NotificationKey notificationKey) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                                                                                                           Engine.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(transactionalDao, effectiveDate, null, notificationKey);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void notifyBusOfRequestedChange(final EntitlementEventSqlDao transactional, final SubscriptionData subscription, final EntitlementEvent nextEvent) {
        try {
            eventBus.postFromTransaction(new DefaultRequestedSubscriptionEvent(subscription, nextEvent), transactional);
        } catch (EventBusException e) {
            log.warn("Failed to post requested change event for subscription " + subscription.getId(), e);
        }
    }
}
