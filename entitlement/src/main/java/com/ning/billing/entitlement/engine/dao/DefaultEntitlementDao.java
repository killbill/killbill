/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.io.IOException;
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

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.migration.AccountMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.BundleMigrationData;
import com.ning.billing.entitlement.api.migration.AccountMigrationData.SubscriptionMigrationData;
import com.ning.billing.entitlement.api.timeline.DefaultRepairEntitlementEvent;
import com.ning.billing.entitlement.api.timeline.SubscriptionDataRepair;
import com.ning.billing.entitlement.api.transfer.TransferCancelData;
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.DefaultRequestedSubscriptionEvent;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBuilder;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionBundleData;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.core.Engine;
import com.ning.billing.entitlement.engine.core.EntitlementNotificationKey;
import com.ning.billing.entitlement.engine.dao.model.EntitlementEventModelDao;
import com.ning.billing.entitlement.engine.dao.model.SubscriptionBundleModelDao;
import com.ning.billing.entitlement.engine.dao.model.SubscriptionModelDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.EntitlementEvent.EventType;
import com.ning.billing.entitlement.events.phase.PhaseEvent;
import com.ning.billing.entitlement.events.user.ApiEvent;
import com.ning.billing.entitlement.events.user.ApiEventBuilder;
import com.ning.billing.entitlement.events.user.ApiEventCancel;
import com.ning.billing.entitlement.events.user.ApiEventChange;
import com.ning.billing.entitlement.events.user.ApiEventMigrateBilling;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.entitlement.exceptions.EntitlementError;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.entity.EntityPersistenceException;
import com.ning.billing.util.entity.dao.EntitySqlDao;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import com.ning.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.RepairEntitlementInternalEvent;
import com.ning.billing.util.notificationq.NotificationKey;
import com.ning.billing.util.notificationq.NotificationQueue;
import com.ning.billing.util.notificationq.NotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService.NoSuchNotificationQueue;
import com.ning.billing.util.svcsapi.bus.InternalBus;
import com.ning.billing.util.svcsapi.bus.InternalBus.EventBusException;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;


public class DefaultEntitlementDao implements EntitlementDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultEntitlementDao.class);

    private final Clock clock;
    private final EntitySqlDaoTransactionalJdbiWrapper transactionalSqlDao;
    private final NotificationQueueService notificationQueueService;
    private final AddonUtils addonUtils;
    private final InternalBus eventBus;
    private final CatalogService catalogService;

    @Inject
    public DefaultEntitlementDao(final IDBI dbi, final Clock clock, final AddonUtils addonUtils,
                                 final NotificationQueueService notificationQueueService, final InternalBus eventBus, final CatalogService catalogService,
                                 final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao) {
        this.clock = clock;
        this.transactionalSqlDao = new EntitySqlDaoTransactionalJdbiWrapper(dbi, clock, cacheControllerDispatcher, nonEntityDao);
        this.notificationQueueService = notificationQueueService;
        this.addonUtils = addonUtils;
        this.eventBus = eventBus;
        this.catalogService = catalogService;
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<SubscriptionBundle>() {
            @Override
            public SubscriptionBundle inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionBundleModelDao bundle = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundleFromAccountAndKey(accountId.toString(), bundleKey, context);
                return SubscriptionBundleModelDao.toSubscriptionbundle(bundle);
            }
        });
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<SubscriptionBundle>>() {
            @Override
            public List<SubscriptionBundle> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionBundleModelDao> models = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundleFromAccount(accountId.toString(), context);

                return new ArrayList<SubscriptionBundle>(Collections2.transform(models, new Function<SubscriptionBundleModelDao, SubscriptionBundle>() {
                    @Override
                    public SubscriptionBundle apply(@Nullable final SubscriptionBundleModelDao input) {
                        return SubscriptionBundleModelDao.toSubscriptionbundle(input);
                    }
                }));
            }
        });
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<SubscriptionBundle>() {
            @Override
            public SubscriptionBundle inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionBundleModelDao model = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getById(bundleId.toString(), context);
                return SubscriptionBundleModelDao.toSubscriptionbundle(model);
            }
        });
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<SubscriptionBundle>>() {
            @Override
            public List<SubscriptionBundle> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionBundleModelDao> models = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundlesForKey(bundleKey, context);
                return new ArrayList<SubscriptionBundle>(Collections2.transform(models, new Function<SubscriptionBundleModelDao, SubscriptionBundle>() {
                    @Override
                    public SubscriptionBundle apply(@Nullable final SubscriptionBundleModelDao input) {
                        return SubscriptionBundleModelDao.toSubscriptionbundle(input);
                    }
                }));
            }
        });
    }

    @Override
    public SubscriptionBundle createSubscriptionBundle(final SubscriptionBundleData bundle, final InternalCallContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<SubscriptionBundle>() {
            @Override
            public SubscriptionBundle inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws EntityPersistenceException {

                final SubscriptionBundleModelDao model = new SubscriptionBundleModelDao(bundle);
                entitySqlDaoWrapperFactory.become(BundleSqlDao.class).create(model, context);
                return bundle;
            }
        });
    }

    @Override
    public UUID getAccountIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionModelDao subscriptionModel = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getById(subscriptionId.toString(), context);
                if (subscriptionModel == null) {
                    log.error(String.format(ErrorCode.ENT_INVALID_SUBSCRIPTION_ID.getFormat(), subscriptionId.toString()));
                    return null;
                }

                final UUID bundleId = subscriptionModel.getBundleId();
                if (bundleId == null) {
                    log.error(String.format(ErrorCode.ENT_GET_NO_BUNDLE_FOR_SUBSCRIPTION.getFormat(), subscriptionId.toString()));
                    return null;
                }

                final SubscriptionBundleModelDao bundleModel = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getById(bundleId.toString(), context);
                if (bundleModel == null) {
                    log.error(String.format(ErrorCode.ENT_GET_INVALID_BUNDLE_ID.getFormat(), bundleId.toString()));
                    return null;
                }
                return bundleModel.getAccountId();
            }
        });
    }

    @Override
    public Subscription getBaseSubscription(final UUID bundleId, final InternalTenantContext context) {
        return getBaseSubscription(bundleId, true, context);
    }

    @Override
    public Subscription getSubscriptionFromId(final UUID subscriptionId, final InternalTenantContext context) {
        final Subscription shellSubscription = transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Subscription>() {
            @Override
            public Subscription inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionModelDao model = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getById(subscriptionId.toString(), context);
                return SubscriptionModelDao.toSubscription(model);
            }
        });
        return buildSubscription(shellSubscription, context);
    }


    @Override
    public List<Subscription> getSubscriptions(final UUID bundleId, final InternalTenantContext context) {
        return buildBundleSubscriptions(bundleId, getSubscriptionFromBundleId(bundleId, context), context);
    }

    private List<Subscription> getSubscriptionFromBundleId(final UUID bundleId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<Subscription>>() {
            @Override
            public List<Subscription> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionModelDao> models = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getSubscriptionsFromBundleId(bundleId.toString(), context);
                return new ArrayList<Subscription>(Collections2.transform(models, new Function<SubscriptionModelDao, Subscription>() {
                    @Override
                    public Subscription apply(@Nullable final SubscriptionModelDao input) {
                        return SubscriptionModelDao.toSubscription(input);
                    }
                }));
            }
        });
    }


    @Override
    public List<Subscription> getSubscriptionsForAccountAndKey(final UUID accountId,
                                                               final String bundleKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<Subscription>>() {
            @Override
            public List<Subscription> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionBundleModelDao bundleModel = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundleFromAccountAndKey(accountId.toString(), bundleKey, context);
                if (bundleModel == null) {
                    return Collections.emptyList();
                }
                return getSubscriptions(bundleModel.getId(), context);
            }
        });
    }

    @Override
    public void updateChargedThroughDate(final SubscriptionData subscription, final InternalCallContext context) {
        final Date ctd = (subscription.getChargedThroughDate() != null) ? subscription.getChargedThroughDate().toDate() : null;

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactionalDao = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
                transactionalDao.updateChargedThroughDate(subscription.getId().toString(), ctd, context);

                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);
                final String bundleId = subscription.getBundleId().toString();
                bundleSqlDao.updateBundleLastSysTime(bundleId, clock.getUTCNow().toDate(), context);
                return null;
            }
        });
    }

    @Override
    public void createNextPhaseEvent(final SubscriptionData subscription, final EntitlementEvent nextPhase, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);
                final UUID subscriptionId = subscription.getId();
                cancelNextPhaseEventFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, context);
                transactional.create(new EntitlementEventModelDao(nextPhase), context);
                recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                        nextPhase.getEffectiveDate(),
                                                        new EntitlementNotificationKey(nextPhase.getId()),
                                                        context);

                // Notify the Bus of the requested change
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, nextPhase, context);

                return null;
            }
        });
    }

    @Override
    public EntitlementEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<EntitlementEvent>() {
            @Override
            public EntitlementEvent inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                EntitlementEventModelDao model = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class).getById(eventId.toString(), context);
                return EntitlementEventModelDao.toEntitlementEvent(model);
            }
        });
    }

    @Override
    public List<EntitlementEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<EntitlementEvent>>() {
            @Override
            public List<EntitlementEvent> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<EntitlementEventModelDao> models = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class).getEventsForSubscription(subscriptionId.toString(), context);

                return new ArrayList<EntitlementEvent>(Collections2.transform(models, new Function<EntitlementEventModelDao, EntitlementEvent>() {
                    @Override
                    public EntitlementEvent apply(@Nullable final EntitlementEventModelDao input) {
                        return EntitlementEventModelDao.toEntitlementEvent(input);
                    }
                }));
            }
        });
    }

    @Override
    public Map<UUID, List<EntitlementEvent>> getEventsForBundle(final UUID bundleId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Map<UUID, List<EntitlementEvent>>>() {
            @Override
            public Map<UUID, List<EntitlementEvent>> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);

                final List<SubscriptionModelDao> subscriptionModels = transactional.getSubscriptionsFromBundleId(bundleId.toString(), context);
                if (subscriptionModels.size() == 0) {
                    return Collections.emptyMap();
                }

                final EntitlementEventSqlDao eventsDaoFromSameTransaction = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);
                final Map<UUID, List<EntitlementEvent>> result = new HashMap<UUID, List<EntitlementEvent>>();
                for (final SubscriptionModelDao cur : subscriptionModels) {
                    final List<EntitlementEventModelDao> eventModels = eventsDaoFromSameTransaction.getEventsForSubscription(cur.getId().toString(), context);
                    final List<EntitlementEvent> events = new ArrayList<EntitlementEvent>(Collections2.transform(eventModels, new Function<EntitlementEventModelDao, EntitlementEvent>() {
                        @Override
                        public EntitlementEvent apply(@Nullable final EntitlementEventModelDao input) {
                            return EntitlementEventModelDao.toEntitlementEvent(input);
                        }
                    }));
                    result.put(cur.getId(), events);
                }
                return result;
            }
        });
    }

    @Override
    public List<EntitlementEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final Date now = clock.getUTCNow().toDate();

        return transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<List<EntitlementEvent>>() {
            @Override
            public List<EntitlementEvent> inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final List<EntitlementEventModelDao> eventModels = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class).getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);
                final List<EntitlementEvent> events = new ArrayList<EntitlementEvent>(Collections2.transform(eventModels, new Function<EntitlementEventModelDao, EntitlementEvent>() {
                    @Override
                    public EntitlementEvent apply(@Nullable final EntitlementEventModelDao input) {
                        return EntitlementEventModelDao.toEntitlementEvent(input);
                    }
                }));
                return events;
            }
        });
    }

    @Override
    public void createSubscription(final SubscriptionData subscription, final List<EntitlementEvent> initialEvents, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
                transactional.create(new SubscriptionModelDao(subscription), context);

                final EntitlementEventSqlDao eventsDaoFromSameTransaction = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);
                for (final EntitlementEvent cur : initialEvents) {
                    eventsDaoFromSameTransaction.create(new EntitlementEventModelDao(cur), context);

                    final boolean isBusEvent = cur.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 && (cur.getType() == EventType.API_USER);
                    recordBusOrFutureNotificationFromTransaction(subscription, cur, entitySqlDaoWrapperFactory, isBusEvent, 0, context);

                }
                // Notify the Bus of the latest requested change, if needed
                if (initialEvents.size() > 0) {
                    notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, initialEvents.get(initialEvents.size() - 1), context);
                }
                return null;
            }
        });
    }



    @Override
    public void recreateSubscription(final SubscriptionData subscription, final List<EntitlementEvent> recreateEvents, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);

                for (final EntitlementEvent cur : recreateEvents) {
                    transactional.create(new EntitlementEventModelDao(cur), context);

                    final boolean isBusEvent = cur.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 && (cur.getType() == EventType.API_USER);
                    recordBusOrFutureNotificationFromTransaction(subscription, cur, entitySqlDaoWrapperFactory, isBusEvent, 0, context);
                }

                // Notify the Bus of the latest requested change
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, recreateEvents.get(recreateEvents.size() - 1), context);

                return null;
            }
        });
    }

    @Override
    public void cancelSubscriptions(final List<SubscriptionData> subscriptions, final List<EntitlementEvent> cancelEvents, final InternalCallContext context) {

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                for (int i = 0; i < subscriptions.size(); i++) {
                    final SubscriptionData subscription = subscriptions.get(i);
                    final EntitlementEvent cancelEvent = cancelEvents.get(i);
                    cancelSubscriptionFromTransaction(subscription, cancelEvent, entitySqlDaoWrapperFactory, context, i);
                }
                return null;
            }
        });
    }



    @Override
    public void cancelSubscription(final SubscriptionData subscription, final EntitlementEvent cancelEvent, final InternalCallContext context, final int seqId) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                cancelSubscriptionFromTransaction(subscription, cancelEvent, entitySqlDaoWrapperFactory, context, seqId);
                return null;
            }
        });
    }

    @Override
    public void uncancelSubscription(final SubscriptionData subscription, final List<EntitlementEvent> uncancelEvents, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);

                final UUID subscriptionId = subscription.getId();
                EntitlementEventModelDao cancelledEvent = null;
                final Date now = clock.getUTCNow().toDate();
                final List<EntitlementEventModelDao> eventModels = transactional.getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);

                for (final EntitlementEventModelDao cur : eventModels) {
                    if (cur.getUserType() == ApiEventType.CANCEL) {
                        if (cancelledEvent != null) {
                            throw new EntitlementError(String.format("Found multiple cancel active events for subscriptions %s", subscriptionId.toString()));
                        }
                        cancelledEvent = cur;
                    }
                }

                if (cancelledEvent != null) {
                    final String cancelledEventId = cancelledEvent.getId().toString();
                    transactional.unactiveEvent(cancelledEventId, context);
                    for (final EntitlementEvent cur : uncancelEvents) {
                        transactional.create(new EntitlementEventModelDao(cur), context);
                        recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                                cur.getEffectiveDate(),
                                                                new EntitlementNotificationKey(cur.getId()),
                                                                context);
                    }

                    // Notify the Bus of the latest requested change
                    notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, uncancelEvents.get(uncancelEvents.size() - 1), context);
                }

                return null;
            }
        });
    }

    @Override
    public void changePlan(final SubscriptionData subscription, final List<EntitlementEvent> changeEvents, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {

                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);
                final UUID subscriptionId = subscription.getId();

                final List<EntitlementEvent> changeEventsTweakedWithMigrateBilling = reinsertFutureMigrateBillingEventOnChangeFromTransaction(subscriptionId,
                                                                                                                                              changeEvents,
                                                                                                                                              entitySqlDaoWrapperFactory,
                                                                                                                                              context);

                cancelFutureEventsFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, context);

                for (final EntitlementEvent cur : changeEventsTweakedWithMigrateBilling) {

                    transactional.create(new EntitlementEventModelDao(cur), context);

                    final boolean isBusEvent = cur.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0 && (cur.getType() == EventType.API_USER);
                    recordBusOrFutureNotificationFromTransaction(subscription, cur, entitySqlDaoWrapperFactory, isBusEvent, 0, context);
                }

                // Notify the Bus of the latest requested change
                final EntitlementEvent finalEvent = changeEventsTweakedWithMigrateBilling.get(changeEvents.size() - 1);
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, finalEvent, context);

                return null;
            }
        });
    }

    //
    // This piece of code has been isolated in its own method in order to allow for migrated subscriptions to have their plan to changed prior
    // to MIGRATE_BILLING; the effect will be to reflect the change from an entitlement point of view while ignoring the change until we hit
    // the begining of the billing, that is when we hit the MIGRATE_BILLING event. If we had a clear separation between entitlement and
    // billing that would not be needed.
    //
    // If there is a change of plan prior to a future MIGRATE_BILLING, we want to modify the existing MIGRATE_BILLING so it reflects
    // the new plan, phase, pricelist; Invoice will only see the MIGRATE_BILLING as things prior to that will be ignored, so we need to make sure
    // that event reflects the correct entitlement information.
    //
    //
    final List<EntitlementEvent> reinsertFutureMigrateBillingEventOnChangeFromTransaction(final UUID subscriptionId, final List<EntitlementEvent> changeEvents, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) {
        final EntitlementEventModelDao migrateBillingEvent = findFutureEventFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, EventType.API_USER, ApiEventType.MIGRATE_BILLING, context);
        if (migrateBillingEvent == null) {
            // No future migrate billing : returns same list
            return changeEvents;
        }

        String prevPlan = null;
        String prevPhase = null;
        String prevPriceList = null;
        String curPlan = null;
        String curPhase = null;
        String curPriceList = null;
        for (EntitlementEvent cur : changeEvents) {
            switch (cur.getType()) {
                case API_USER:
                    final ApiEvent apiEvent = (ApiEvent) cur;
                    curPlan = apiEvent.getEventPlan();
                    curPhase = apiEvent.getEventPlanPhase();
                    curPriceList = apiEvent.getPriceList();
                    break;

                case PHASE:
                    final PhaseEvent phaseEvent = (PhaseEvent) cur;
                    curPhase = phaseEvent.getPhase();
                    break;

                default:
                    throw new EntitlementError("Unknown event type " + cur.getType());
            }

            if (cur.getEffectiveDate().compareTo(migrateBillingEvent.getEffectiveDate()) > 0) {
                if (cur.getType() == EventType.API_USER && ((ApiEvent) cur).getEventType() == ApiEventType.CHANGE) {
                    // This is an EOT change that is occurring after the MigrateBilling : returns same list
                    return changeEvents;
                }
                // We found the first event after the migrate billing
                break;
            }
            prevPlan = curPlan;
            prevPhase = curPhase;
            prevPriceList = curPriceList;
        }

        if (prevPlan != null) {
            // Create the new MIGRATE_BILLING with same effectiveDate but new plan information
            final DateTime now = clock.getUTCNow();
            final ApiEventBuilder builder = new ApiEventBuilder()
                    .setActive(true)
                    .setEventType(ApiEventType.MIGRATE_BILLING)
                    .setFromDisk(true)
                    .setTotalOrdering(migrateBillingEvent.getTotalOrdering())
                    .setUuid(UUID.randomUUID())
                    .setSubscriptionId(migrateBillingEvent.getSubscriptionId())
                    .setCreatedDate(now)
                    .setUpdatedDate(now)
                    .setRequestedDate(migrateBillingEvent.getRequestedDate())
                    .setEffectiveDate(migrateBillingEvent.getEffectiveDate())
                    .setProcessedDate(now)
                    .setActiveVersion(migrateBillingEvent.getCurrentVersion())
                    .setEventPlan(prevPlan)
                    .setEventPlanPhase(prevPhase)
                    .setEventPriceList(prevPriceList);

            final EntitlementEvent newMigrateBillingEvent = new ApiEventMigrateBilling(builder);
            changeEvents.add(newMigrateBillingEvent);

            Collections.sort(changeEvents, new Comparator<EntitlementEvent>() {
                @Override
                public int compare(final EntitlementEvent o1, final EntitlementEvent o2) {
                    return o1.getEffectiveDate().compareTo(o2.getEffectiveDate());
                }
            });
        }

        return changeEvents;
    }

    private void cancelSubscriptionFromTransaction(final SubscriptionData subscription, final EntitlementEvent cancelEvent, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context, final int seqId)
            throws EntityPersistenceException {
        final UUID subscriptionId = subscription.getId();
        cancelFutureEventsFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, context);
        entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class).create(new EntitlementEventModelDao(cancelEvent), context);

        final boolean isBusEvent = cancelEvent.getEffectiveDate().compareTo(clock.getUTCNow()) <= 0;
        recordBusOrFutureNotificationFromTransaction(subscription, cancelEvent, entitySqlDaoWrapperFactory, isBusEvent, seqId, context);

        // Notify the Bus of the requested change
        notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, cancelEvent, context);
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, EventType.PHASE, null, context);
    }

    private void cancelFutureEventsFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) {
        final Date now = clock.getUTCNow().toDate();
        final List<EntitlementEventModelDao> eventModels = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class).getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);
        for (final EntitlementEventModelDao cur : eventModels) {
            unactivateEventFromTransaction(cur, entitySqlDaoWrapperFactory, context);
        }
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory<EntitySqlDao> dao, final EventType type,
                                                  @Nullable final ApiEventType apiType, final InternalCallContext context) {
        final EntitlementEventModelDao futureEvent = findFutureEventFromTransaction(subscriptionId, dao, type, apiType, context);
        unactivateEventFromTransaction(futureEvent, dao, context);
    }

    private EntitlementEventModelDao findFutureEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory<EntitySqlDao> dao, final EventType type,
                                                                    @Nullable final ApiEventType apiType, final InternalCallContext context) {

        EntitlementEventModelDao futureEvent = null;
        final Date now = clock.getUTCNow().toDate();
        final List<EntitlementEventModelDao> eventModels = dao.become(EntitlementEventSqlDao.class).getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);
        for (final EntitlementEventModelDao cur : eventModels) {
            if (cur.getEventType() == type &&
                (apiType == null || apiType == cur.getUserType())) {
                if (futureEvent != null) {
                    throw new EntitlementError(String.format("Found multiple future events for type %s for subscriptions %s",
                                                             type, subscriptionId.toString()));
                }
                futureEvent = cur;
                // To check that there is only one such event
                //break;
            }
        }
        return futureEvent;
    }

    private void unactivateEventFromTransaction(final EntitlementEventModelDao event, final EntitySqlDaoWrapperFactory<EntitySqlDao> dao, final InternalCallContext context) {
        if (event != null) {
            final String eventId = event.getId().toString();
            dao.become(EntitlementEventSqlDao.class).unactiveEvent(eventId, context);
        }
    }

    private Subscription buildSubscription(final Subscription input, final InternalTenantContext context) {
        if (input == null) {
            return null;
        }

        final List<Subscription> bundleInput = new ArrayList<Subscription>();
        if (input.getCategory() == ProductCategory.ADD_ON) {
            final Subscription baseSubscription = getBaseSubscription(input.getBundleId(), false, context);
            if (baseSubscription == null) {
                return null;
            }

            bundleInput.add(baseSubscription);
            bundleInput.add(input);
        } else {
            bundleInput.add(input);
        }

        final List<Subscription> reloadedSubscriptions = buildBundleSubscriptions(input.getBundleId(), bundleInput, context);
        for (final Subscription cur : reloadedSubscriptions) {
            if (cur.getId().equals(input.getId())) {
                return cur;
            }
        }

        throw new EntitlementError("Unexpected code path in buildSubscription");
    }

    private List<Subscription> buildBundleSubscriptions(final UUID bundleId, final List<Subscription> input, final InternalTenantContext context) {
        if (input == null || input.size() == 0) {
            return Collections.emptyList();
        }

        // Make sure BasePlan -- if exists-- is first
        Collections.sort(input, new Comparator<Subscription>() {
            @Override
            public int compare(final Subscription o1, final Subscription o2) {
                if (o1.getCategory() == ProductCategory.BASE) {
                    return -1;
                } else if (o2.getCategory() == ProductCategory.BASE) {
                    return 1;
                } else {
                    return ((SubscriptionData) o1).getAlignStartDate().compareTo(((SubscriptionData) o2).getAlignStartDate());
                }
            }
        });

        EntitlementEvent futureBaseEvent = null;
        final List<Subscription> result = new ArrayList<Subscription>(input.size());
        for (final Subscription cur : input) {
            final List<EntitlementEvent> events = getEventsForSubscription(cur.getId(), context);
            Subscription reloaded = createSubscriptionForInternalUse(cur, events);

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

                    final boolean createCancelEvent = (futureBaseEvent != null && targetAddOnPlan != null) &&
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
                        reloaded = createSubscriptionForInternalUse(cur, events);
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
    public void migrate(final UUID accountId, final AccountMigrationData accountData, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);

                for (final BundleMigrationData curBundle : accountData.getData()) {
                    migrateBundleDataFromTransaction(curBundle, transactional, entitySqlDaoWrapperFactory, context);
                }
                return null;
            }
        });
    }

    @Override
    public void repair(final UUID accountId, final UUID bundleId, final List<SubscriptionDataRepair> inRepair, final InternalCallContext context) {
        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);

                final EntitlementEventSqlDao transEventDao = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);
                for (final SubscriptionDataRepair cur : inRepair) {
                    transactional.updateForRepair(cur.getId().toString(), cur.getActiveVersion(), cur.getAlignStartDate().toDate(), cur.getBundleStartDate().toDate(), context);
                    for (final EntitlementEvent event : cur.getInitialEvents()) {
                        transEventDao.updateVersion(event.getId().toString(), event.getActiveVersion(), context);
                    }
                    for (final EntitlementEvent event : cur.getNewEvents()) {
                        transEventDao.create(new EntitlementEventModelDao(event), context);
                        if (event.getEffectiveDate().isAfter(clock.getUTCNow())) {
                            recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                                    event.getEffectiveDate(),
                                                                    new EntitlementNotificationKey(event.getId()),
                                                                    context);
                        }
                    }
                }

                try {
                    // Note: we don't send a requested change event here, but a repair event
                    final RepairEntitlementInternalEvent busEvent = new DefaultRepairEntitlementEvent(context.getUserToken(), accountId, bundleId, clock.getUTCNow(),
                                                                                                      context.getAccountRecordId(), context.getTenantRecordId());
                    eventBus.postFromTransaction(busEvent, entitySqlDaoWrapperFactory, context);
                } catch (EventBusException e) {
                    log.warn("Failed to post repair entitlement event for bundle " + bundleId, e);
                }

                return null;
            }
        });
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleMigrationData bundleTransferData,
                         final List<TransferCancelData> transferCancelData, final InternalCallContext fromContext, final InternalCallContext toContext) {

        transactionalSqlDao.execute(new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory) throws Exception {
                final EntitlementEventSqlDao transactional = entitySqlDaoWrapperFactory.become(EntitlementEventSqlDao.class);

                // Cancel the subscriptions for the old bundle
                for (final TransferCancelData cancel : transferCancelData) {
                    cancelSubscriptionFromTransaction(cancel.getSubscription(), cancel.getCancelEvent(), entitySqlDaoWrapperFactory, fromContext, 0);
                }

                migrateBundleDataFromTransaction(bundleTransferData, transactional, entitySqlDaoWrapperFactory, toContext);
                return null;
            }
        });
    }

    private SubscriptionData createSubscriptionForInternalUse(final Subscription shellSubscription, final List<EntitlementEvent> events) {
        final SubscriptionData result = new SubscriptionData(new SubscriptionBuilder(((SubscriptionData) shellSubscription)), null, clock);
        if (events.size() > 0) {
            result.rebuildTransitions(events, catalogService.getFullCatalog());
        }
        return result;
    }

    private Subscription getBaseSubscription(final UUID bundleId, final boolean rebuildSubscription, final InternalTenantContext context) {
        final List<Subscription> subscriptions = getSubscriptionFromBundleId(bundleId, context);
        for (final Subscription cur : subscriptions) {
            if (cur.getCategory() == ProductCategory.BASE) {
                return rebuildSubscription ? buildSubscription(cur, context) : cur;
            }
        }
        return null;
    }


    //
    // Either records a notfication or sends a bus event is operation is immediate
    //
    private void recordBusOrFutureNotificationFromTransaction(final SubscriptionData subscription, final EntitlementEvent event, final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final boolean busEvent,
                                                              final int seqId, final InternalCallContext context) {
        if (busEvent) {
            notifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, event, seqId, context);
        } else {
            recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                    event.getEffectiveDate(),
                                                    new EntitlementNotificationKey(event.getId()),
                                                    context);
        }
    }

    //
    // Sends bus notification for event on effecfive date-- only used for operation that happen immediately:
    // - CREATE,
    // - IMM CANCEL or CHANGE
    //
    private void notifyBusOfEffectiveImmediateChange(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final SubscriptionData subscription,
                                            final EntitlementEvent immediateEvent, final int seqId, final InternalCallContext context) {
        try {
            final SubscriptionData upToDateSubscription = createSubscriptionWithNewEvent(subscription, immediateEvent);

            final SubscriptionTransitionData transition = upToDateSubscription.getTransitionFromEvent(immediateEvent, seqId);
            final EffectiveSubscriptionInternalEvent busEvent = new DefaultEffectiveSubscriptionEvent(transition, upToDateSubscription.getAlignStartDate(),
                                                                                                      context.getUserToken(),
                                                                                                      context.getAccountRecordId(), context.getTenantRecordId());


            eventBus.postFromTransaction(busEvent, entitySqlDaoWrapperFactory, context);
        } catch (EventBusException e) {
            log.warn("Failed to post effective event for subscription " + subscription.getId(), e);
        }
    }

    private void notifyBusOfRequestedChange(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final SubscriptionData subscription,
                                            final EntitlementEvent nextEvent, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultRequestedSubscriptionEvent(subscription, nextEvent, context.getAccountRecordId(), context.getTenantRecordId()), entitySqlDaoWrapperFactory, context);
        } catch (EventBusException e) {
            log.warn("Failed to post requested change event for subscription " + subscription.getId(), e);
        }
    }

    private void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final DateTime effectiveDate,
                                                         final NotificationKey notificationKey, final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(Engine.ENTITLEMENT_SERVICE_NAME,
                                                                                                           Engine.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory, effectiveDate, notificationKey, context);
        } catch (NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void migrateBundleDataFromTransaction(final BundleMigrationData bundleTransferData, final EntitlementEventSqlDao transactional,
                                                  final EntitySqlDaoWrapperFactory<EntitySqlDao> entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException {

        final SubscriptionSqlDao transSubDao = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
        final BundleSqlDao transBundleDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);

        final SubscriptionBundleData bundleData = bundleTransferData.getData();

        final SubscriptionBundleModelDao existingBundleModel = transBundleDao.getBundleFromAccountAndKey(bundleData.getAccountId().toString(), bundleData.getExternalKey(), context);
        if (existingBundleModel != null) {
            log.error(String.format("Attempted to create a bundle for account %s and key %s that already existed, skip...", bundleData.getAccountId().toString(), bundleData.getExternalKey()));
            return;
        }

        for (final SubscriptionMigrationData curSubscription : bundleTransferData.getSubscriptions()) {
            final SubscriptionData subData = curSubscription.getData();
            for (final EntitlementEvent curEvent : curSubscription.getInitialEvents()) {
                transactional.create(new EntitlementEventModelDao(curEvent), context);
                recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                        curEvent.getEffectiveDate(),
                                                        new EntitlementNotificationKey(curEvent.getId()),
                                                        context);
            }
            transSubDao.create(new SubscriptionModelDao(subData), context);

            // Notify the Bus of the latest requested change
            final EntitlementEvent finalEvent = curSubscription.getInitialEvents().get(curSubscription.getInitialEvents().size() - 1);
            notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subData, finalEvent, context);
        }

        transBundleDao.create(new SubscriptionBundleModelDao(bundleData), context);
    }

    //
    // Creates a copy of the existing subscriptions whose 'transitions' will reflect the new event
    //
    private SubscriptionData createSubscriptionWithNewEvent(final SubscriptionData subscription, EntitlementEvent newEvent) {

        final SubscriptionData subscriptionWithNewEvent = new SubscriptionData(subscription, null, clock);
        final List<EntitlementEvent> allEvents = new LinkedList<EntitlementEvent>();
        if (subscriptionWithNewEvent.getEvents() != null) {
            allEvents.addAll(subscriptionWithNewEvent.getEvents());
        }
        allEvents.add(newEvent);
        subscriptionWithNewEvent.rebuildTransitions(allEvents, catalogService.getFullCatalog());
        return subscriptionWithNewEvent;
    }

}
