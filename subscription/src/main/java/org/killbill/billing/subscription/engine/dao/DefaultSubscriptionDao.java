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

package org.killbill.billing.subscription.engine.dao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.SubscriptionBaseWithAddOns;
import org.killbill.billing.subscription.api.svcs.DefaultSubscriptionInternalApi;
import org.killbill.billing.subscription.api.transfer.BundleTransferData;
import org.killbill.billing.subscription.api.transfer.SubscriptionTransferData;
import org.killbill.billing.subscription.api.transfer.TransferCancelData;
import org.killbill.billing.subscription.api.user.DefaultEffectiveSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultRequestedSubscriptionEvent;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseTransitionData;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.subscription.engine.core.SubscriptionNotificationKey;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionEventModelDao;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionModelDao;
import org.killbill.billing.subscription.events.EventBaseBuilder;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent.EventType;
import org.killbill.billing.subscription.events.bcd.BCDEvent;
import org.killbill.billing.subscription.events.bcd.BCDEventBuilder;
import org.killbill.billing.subscription.events.phase.PhaseEvent;
import org.killbill.billing.subscription.events.phase.PhaseEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEvent;
import org.killbill.billing.subscription.events.user.ApiEventBuilder;
import org.killbill.billing.subscription.events.user.ApiEventCancel;
import org.killbill.billing.subscription.events.user.ApiEventChange;
import org.killbill.billing.subscription.events.user.ApiEventType;
import org.killbill.billing.subscription.exceptions.SubscriptionBaseError;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.Pagination;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.Ordering;
import org.killbill.billing.util.entity.dao.DefaultPaginationSqlDaoHelper.PaginationIteratorBuilder;
import org.killbill.billing.util.entity.dao.EntityDaoBase;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoTransactionalJdbiWrapper;
import org.killbill.billing.util.entity.dao.EntitySqlDaoWrapperFactory;
import org.killbill.bus.api.BusEvent;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.notificationq.api.NotificationQueueService.NoSuchNotificationQueue;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

import static org.killbill.billing.util.glue.IDBISetup.MAIN_RO_IDBI_NAMED;

public class DefaultSubscriptionDao extends EntityDaoBase<SubscriptionBundleModelDao, SubscriptionBaseBundle, SubscriptionApiException> implements SubscriptionDao {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionDao.class);

    private final Clock clock;
    private final NotificationQueueService notificationQueueService;
    private final AddonUtils addonUtils;
    private final PersistentBus eventBus;

    @Inject
    public DefaultSubscriptionDao(final IDBI dbi, @Named(MAIN_RO_IDBI_NAMED) final IDBI roDbi, final Clock clock, final AddonUtils addonUtils,
                                  final NotificationQueueService notificationQueueService, final PersistentBus eventBus,
                                  final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao,
                                  final InternalCallContextFactory internalCallContextFactory) {
        super(new EntitySqlDaoTransactionalJdbiWrapper(dbi, roDbi, clock, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory), BundleSqlDao.class);
        this.clock = clock;
        this.notificationQueueService = notificationQueueService;
        this.addonUtils = addonUtils;
        this.eventBus = eventBus;
    }

    @Override
    protected SubscriptionApiException generateAlreadyExistsException(final SubscriptionBundleModelDao entity, final InternalCallContext context) {
        return new SubscriptionApiException(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS, entity.getExternalKey());
    }

    @Override
    public SubscriptionBaseBundle getSubscriptionBundlesForAccountAndKey(final UUID accountId, final String bundleKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<SubscriptionBaseBundle>() {
            @Override
            public SubscriptionBaseBundle inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionBundleModelDao input = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundlesFromAccountAndKey(accountId.toString(), bundleKey, context);
                return SubscriptionBundleModelDao.toSubscriptionBundle(input);
            }
        });
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundleForAccount(final UUID accountId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseBundle>>() {
            @Override
            public List<SubscriptionBaseBundle> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionBundleModelDao> models = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundleFromAccount(accountId.toString(), context);

                return new ArrayList<SubscriptionBaseBundle>(Collections2.transform(models, new Function<SubscriptionBundleModelDao, SubscriptionBaseBundle>() {
                    @Override
                    public SubscriptionBaseBundle apply(@Nullable final SubscriptionBundleModelDao input) {
                        return SubscriptionBundleModelDao.toSubscriptionBundle(input);
                    }
                }));
            }
        });
    }

    @Override
    public SubscriptionBaseBundle getSubscriptionBundleFromId(final UUID bundleId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<SubscriptionBaseBundle>() {
            @Override
            public SubscriptionBaseBundle inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionBundleModelDao model = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getById(bundleId.toString(), context);
                return SubscriptionBundleModelDao.toSubscriptionBundle(model);
            }
        });
    }

    @Override
    public List<SubscriptionBaseBundle> getSubscriptionBundlesForKey(final String bundleKey, final InternalTenantContext context) {

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseBundle>>() {
            @Override
            public List<SubscriptionBaseBundle> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionBundleModelDao> models = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundlesForLikeKey(bundleKey, context);
                return new ArrayList<SubscriptionBaseBundle>(Collections2.transform(models, new Function<SubscriptionBundleModelDao, SubscriptionBaseBundle>() {
                    @Override
                    public SubscriptionBaseBundle apply(@Nullable final SubscriptionBundleModelDao input) {
                        return SubscriptionBundleModelDao.toSubscriptionBundle(input);
                    }
                }));
            }
        });
    }

    @Override
    public Pagination<SubscriptionBundleModelDao> searchSubscriptionBundles(final String searchKey, final Long offset, final Long limit, final InternalTenantContext context) {
        return paginationHelper.getPagination(BundleSqlDao.class,
                                              new PaginationIteratorBuilder<SubscriptionBundleModelDao, SubscriptionBaseBundle, BundleSqlDao>() {
                                                  @Override
                                                  public Long getCount(final BundleSqlDao bundleSqlDao, final InternalTenantContext context) {
                                                      return bundleSqlDao.getSearchCount(searchKey, String.format("%%%s%%", searchKey), context);
                                                  }

                                                  @Override
                                                  public Iterator<SubscriptionBundleModelDao> build(final BundleSqlDao bundleSqlDao, final Long offset, final Long limit, final Ordering ordering, final InternalTenantContext context) {
                                                      return bundleSqlDao.search(searchKey, String.format("%%%s%%", searchKey), offset, limit, ordering.toString(), context);
                                                  }
                                              },
                                              offset,
                                              limit,
                                              context);
    }

    @Override
    public Iterable<UUID> getNonAOSubscriptionIdsForKey(final String bundleKey, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<Iterable<UUID>>() {
            @Override
            public Iterable<UUID> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);

                final List<SubscriptionBundleModelDao> bundles = bundleSqlDao.getBundlesForLikeKey(bundleKey, context);

                final Collection<UUID> nonAOSubscriptionIdsForKey = new LinkedList<UUID>();
                final SubscriptionSqlDao subscriptionSqlDao = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
                for (final SubscriptionBundleModelDao bundle : bundles) {
                    final List<SubscriptionModelDao> subscriptions = subscriptionSqlDao.getSubscriptionsFromBundleId(bundle.getId().toString(), context);

                    final Collection<SubscriptionModelDao> nonAddonSubscriptions = Collections2.filter(subscriptions,
                                                                                                       new Predicate<SubscriptionModelDao>() {
                                                                                                           @Override
                                                                                                           public boolean apply(final SubscriptionModelDao input) {
                                                                                                               return input.getCategory() != ProductCategory.ADD_ON;
                                                                                                           }
                                                                                                       });

                    nonAOSubscriptionIdsForKey.addAll(Collections2.transform(nonAddonSubscriptions,
                                                                             new Function<SubscriptionModelDao, UUID>() {
                                                                                 @Override
                                                                                 public UUID apply(final SubscriptionModelDao input) {
                                                                                     return input.getId();
                                                                                 }
                                                                             }));

                }

                return nonAOSubscriptionIdsForKey;
            }
        });
    }

    @Override
    public SubscriptionBaseBundle createSubscriptionBundle(final DefaultSubscriptionBaseBundle bundle, final Catalog catalog, final boolean renameCancelledBundleIfExist, final InternalCallContext context) throws SubscriptionBaseApiException {

        return transactionalSqlDao.execute(false, SubscriptionBaseApiException.class, new EntitySqlDaoTransactionWrapper<SubscriptionBaseBundle>() {

            //
            // Because the creation of the SubscriptionBundle is not atomic (with creation of Subscription/SubscriptionEvent), we verify if we were left
            // with an empty SubscriptionBaseBundle form a past failing operation (See #684). We only allow reuse if such SubscriptionBaseBundle is fully
            // empty (and don't allow use case where all Subscription are cancelled, which is the condition for that key to be re-used)
            // Such condition should have been checked upstream (to decide whether that key is valid or not)
            //

            private SubscriptionBaseBundle findExistingUnusedBundleForExternalKeyAndAccount(final List<SubscriptionBundleModelDao> existingBundles, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) {
                final SubscriptionBundleModelDao existingBundleForAccount = Iterables.tryFind(existingBundles, new Predicate<SubscriptionBundleModelDao>() {
                    @Override
                    public boolean apply(final SubscriptionBundleModelDao input) {
                        return input.getAccountId().equals(bundle.getAccountId()) &&
                               // We look for strict equality ignoring tsf items with keys 'kbtsf-343453:'
                               bundle.getExternalKey().equals(input.getExternalKey());
                    }

                }).orNull();

                // If Bundle already exists, and there is 0 Subscription associated with this bundle, we reuse
                if (existingBundleForAccount != null) {
                    final List<SubscriptionModelDao> accountSubscriptions = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getByAccountRecordId(context);
                    if (accountSubscriptions == null ||
                        !Iterables.any(accountSubscriptions, new Predicate<SubscriptionModelDao>() {
                            @Override
                            public boolean apply(final SubscriptionModelDao input) {
                                return input.getBundleId().equals(existingBundleForAccount.getId());
                            }
                        })) {
                        return SubscriptionBundleModelDao.toSubscriptionBundle(existingBundleForAccount);
                    }
                }
                return null;
            }

            @Override
            public SubscriptionBaseBundle inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionBundleModelDao> existingBundles = bundle.getExternalKey() == null ? ImmutableList.<SubscriptionBundleModelDao>of()
                                                                                                         : entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getBundlesForLikeKey(bundle.getExternalKey(), context);

                final SubscriptionBaseBundle unusedBundle = findExistingUnusedBundleForExternalKeyAndAccount(existingBundles, entitySqlDaoWrapperFactory);
                if (unusedBundle != null) {
                    log.info("Found unused bundle for externalKey='{}': bundleId='{}'", bundle.getExternalKey(), unusedBundle.getId());
                    return unusedBundle;
                }
                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);

                for (SubscriptionBundleModelDao cur : existingBundles) {
                    final List<SubscriptionModelDao> subscriptions = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getSubscriptionsFromBundleId(cur.getId().toString(), context);
                    final Iterable<SubscriptionModelDao> filtered = subscriptions != null ? Iterables.filter(subscriptions, new Predicate<SubscriptionModelDao>() {
                        @Override
                        public boolean apply(final SubscriptionModelDao input) {
                            return input.getCategory() != ProductCategory.ADD_ON;
                        }
                    }) : ImmutableList.<SubscriptionModelDao>of();
                    for (SubscriptionModelDao f : filtered) {
                        try {
                            final SubscriptionBase s = buildSubscription(SubscriptionModelDao.toSubscription(f, cur.getExternalKey()), catalog, context);
                            if (s.getState() != EntitlementState.CANCELLED) {
                                throw new SubscriptionBaseApiException(ErrorCode.SUB_CREATE_ACTIVE_BUNDLE_KEY_EXISTS, bundle.getExternalKey());
                            } else if (renameCancelledBundleIfExist) {
                                log.info("Renaming bundles with externalKey='{}', prefix='cncl'", bundle.getExternalKey());
                                // Note that if bundle belongs to a different account, context is not the context for this target account,
                                // but the underlying sql operation does not use the account info
                                bundleSqlDao.renameBundleExternalKey(bundle.getExternalKey(), "cncl", context);
                            } /* else {
                                Code will throw SQLIntegrityConstraintViolationException because of unique constraint on externalKey; might be worth having an ErrorCode just for that
                            } */
                        } catch (CatalogApiException e) {
                            throw new SubscriptionBaseApiException(e);
                        }
                    }
                }

                final SubscriptionBundleModelDao model = new SubscriptionBundleModelDao(bundle);
                // Preserve Original created date
                if (!existingBundles.isEmpty()) {
                    model.setOriginalCreatedDate(existingBundles.get(0).getCreatedDate());
                }
                final SubscriptionBundleModelDao result = createAndRefresh(bundleSqlDao, model, context);
                return SubscriptionBundleModelDao.toSubscriptionBundle(result);
            }
        });
    }

    @Override
    public SubscriptionBase getBaseSubscription(final UUID bundleId, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        return getBaseSubscription(bundleId, true, catalog, context);
    }

    @Override
    public SubscriptionBase getSubscriptionFromId(final UUID subscriptionId, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final DefaultSubscriptionBase shellSubscription = transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<DefaultSubscriptionBase>() {
            @Override
            public DefaultSubscriptionBase inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionModelDao subscriptionModel = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getById(subscriptionId.toString(), context);
                final SubscriptionBundleModelDao bundleModel = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getById(subscriptionModel.getBundleId().toString(), context);
                return SubscriptionModelDao.toSubscription(subscriptionModel, bundleModel.getExternalKey());
            }
        });
        return buildSubscription(shellSubscription, catalog, context);
    }

    @Override
    public UUID getBundleIdFromSubscriptionId(final UUID subscriptionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<UUID>() {
            @Override
            public UUID inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionModelDao subscriptionModel = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getById(subscriptionId.toString(), context);
                return subscriptionModel.getBundleId();
            }
        });
    }

    @Override
    public List<DefaultSubscriptionBase> getSubscriptions(final UUID bundleId, final List<SubscriptionBaseEvent> dryRunEvents, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        return buildBundleSubscriptions(getSubscriptionFromBundleId(bundleId, context), null, dryRunEvents, catalog, context);
    }

    private List<DefaultSubscriptionBase> getSubscriptionFromBundleId(final UUID bundleId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<DefaultSubscriptionBase>>() {
            @Override
            public List<DefaultSubscriptionBase> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final SubscriptionBundleModelDao bundleModel = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getById(bundleId.toString(), context);

                final List<SubscriptionModelDao> models = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getSubscriptionsFromBundleId(bundleId.toString(), context);
                return new ArrayList<DefaultSubscriptionBase>(Collections2.transform(models, new Function<SubscriptionModelDao, DefaultSubscriptionBase>() {
                    @Override
                    public DefaultSubscriptionBase apply(@Nullable final SubscriptionModelDao input) {
                        return SubscriptionModelDao.toSubscription(input, bundleModel.getExternalKey());
                    }
                }));
            }
        });
    }

    @Override
    public Map<UUID, List<DefaultSubscriptionBase>> getSubscriptionsForAccount(final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final Map<UUID, List<DefaultSubscriptionBase>> subscriptionsFromAccountId = getSubscriptionsFromAccountId(context);

        final List<SubscriptionBaseEvent> eventsForAccount = getEventsForAccountId(context);

        final Map<UUID, List<DefaultSubscriptionBase>> result = new HashMap<UUID, List<DefaultSubscriptionBase>>();
        for (final UUID bundleId : subscriptionsFromAccountId.keySet()) {

            final List<DefaultSubscriptionBase> subscriptionsForBundle = subscriptionsFromAccountId.get(bundleId);
            final Multimap<UUID, SubscriptionBaseEvent> eventsForSubscriptions = ArrayListMultimap.create();

            for (final SubscriptionBase cur : subscriptionsForBundle) {
                final Collection<SubscriptionBaseEvent> events = Collections2.filter(eventsForAccount, new Predicate<SubscriptionBaseEvent>() {
                    @Override
                    public boolean apply(final SubscriptionBaseEvent input) {
                        return input.getSubscriptionId().equals(cur.getId());

                    }
                });
                eventsForSubscriptions.putAll(cur.getId(), ImmutableList.copyOf(events));
            }

            result.put(bundleId, buildBundleSubscriptions(subscriptionsForBundle, eventsForSubscriptions, null, catalog, context));
        }
        return result;
    }

    private Map<UUID, List<DefaultSubscriptionBase>> getSubscriptionsFromAccountId(final InternalTenantContext context) {
        final List<DefaultSubscriptionBase> allSubscriptions = transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<DefaultSubscriptionBase>>() {
            @Override
            public List<DefaultSubscriptionBase> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final List<SubscriptionBundleModelDao> bundleModels = entitySqlDaoWrapperFactory.become(BundleSqlDao.class).getByAccountRecordId(context);

                final List<SubscriptionModelDao> subscriptionModels = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class).getByAccountRecordId(context);
                return new ArrayList<DefaultSubscriptionBase>(Collections2.transform(subscriptionModels, new Function<SubscriptionModelDao, DefaultSubscriptionBase>() {
                    @Override
                    public DefaultSubscriptionBase apply(final SubscriptionModelDao input) {
                        final SubscriptionBundleModelDao bundleModel = Iterables.find(bundleModels, new Predicate<SubscriptionBundleModelDao>() {
                            @Override
                            public boolean apply(final SubscriptionBundleModelDao bundleInput) {
                                return bundleInput.getId().equals(input.getBundleId());
                            }
                        });
                        return SubscriptionModelDao.toSubscription(input, bundleModel.getExternalKey());
                    }
                }));
            }
        });

        final Map<UUID, List<DefaultSubscriptionBase>> result = new HashMap<UUID, List<DefaultSubscriptionBase>>();
        for (final DefaultSubscriptionBase subscriptionBase : allSubscriptions) {
            if (result.get(subscriptionBase.getBundleId()) == null) {
                result.put(subscriptionBase.getBundleId(), new LinkedList<DefaultSubscriptionBase>());
            }
            result.get(subscriptionBase.getBundleId()).add(subscriptionBase);
        }
        return result;
    }

    @Override
    public void updateChargedThroughDate(final DefaultSubscriptionBase subscription, final InternalCallContext context) {

        final Date ctd = (subscription.getChargedThroughDate() != null) ? subscription.getChargedThroughDate().toDate() : null;
        final InternalCallContext contextWithUpdatedDate = contextWithUpdatedDate(context);

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactionalDao = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
                transactionalDao.updateChargedThroughDate(subscription.getId().toString(), ctd, contextWithUpdatedDate);

                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);
                final String bundleId = subscription.getBundleId().toString();
                bundleSqlDao.updateBundleLastSysTime(bundleId, context.getCreatedDate().toDate(), contextWithUpdatedDate);
                return null;
            }
        });
    }

    @Override
    public void createNextPhaseEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent readyPhaseEvent, final SubscriptionBaseEvent nextPhaseEvent, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionEventSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);
                final UUID subscriptionId = subscription.getId();
                cancelNextPhaseEventFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, context);
                createAndRefresh(transactional, new SubscriptionEventModelDao(nextPhaseEvent), context);
                recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                        nextPhaseEvent.getEffectiveDate(),
                                                        new SubscriptionNotificationKey(nextPhaseEvent.getId()), context);

                // Notify the Bus
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, nextPhaseEvent, SubscriptionBaseTransitionType.PHASE, context);
                notifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, readyPhaseEvent, 0, context);

                return null;
            }
        });
    }

    @Override
    public SubscriptionBaseEvent getEventById(final UUID eventId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<SubscriptionBaseEvent>() {
            @Override
            public SubscriptionBaseEvent inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionEventModelDao model = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getById(eventId.toString(), context);
                return SubscriptionEventModelDao.toSubscriptionEvent(model);
            }
        });
    }

    @Override
    public List<SubscriptionBaseEvent> getEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseEvent>>() {
            @Override
            public List<SubscriptionBaseEvent> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                return getEventsForSubscriptionInTransaction(entitySqlDaoWrapperFactory, subscriptionId, context);
            }
        });
    }

    @Override
    public List<SubscriptionBaseEvent> getPendingEventsForSubscription(final UUID subscriptionId, final InternalTenantContext context) {
        final Date now = clock.getUTCNow().toDate();

        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseEvent>>() {
            @Override
            public List<SubscriptionBaseEvent> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionEventModelDao> eventModels = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);
                return toSubscriptionBaseEvents(eventModels);
            }
        });
    }

    @Override
    public List<SubscriptionBaseEvent> createSubscriptionsWithAddOns(final List<SubscriptionBaseWithAddOns> subscriptions, final Map<UUID, List<SubscriptionBaseEvent>> initialEventsMap, final Catalog catalog, final InternalCallContext context) {
        return transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseEvent>>() {
            @Override
            public List<SubscriptionBaseEvent> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
                final SubscriptionEventSqlDao eventsDaoFromSameTransaction = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);

                final List<SubscriptionEventModelDao> createdEvents = new LinkedList<SubscriptionEventModelDao>();
                for (final SubscriptionBaseWithAddOns subscription : subscriptions) {
                    for (final SubscriptionBase subscriptionBase : subscription.getSubscriptionBaseList()) {
                        // Safe cast
                        final DefaultSubscriptionBase defaultSubscriptionBase = (DefaultSubscriptionBase) subscriptionBase;
                        createAndRefresh(transactional, new SubscriptionModelDao(defaultSubscriptionBase), context);

                        final List<SubscriptionBaseEvent> initialEvents = initialEventsMap.get(defaultSubscriptionBase.getId());
                        for (final SubscriptionBaseEvent cur : initialEvents) {
                            createdEvents.add(createAndRefresh(eventsDaoFromSameTransaction, new SubscriptionEventModelDao(cur), context));

                            final boolean isBusEvent = cur.getEffectiveDate().compareTo(context.getCreatedDate()) <= 0 && (cur.getType() == EventType.API_USER);
                            recordBusOrFutureNotificationFromTransaction(defaultSubscriptionBase, cur, entitySqlDaoWrapperFactory, isBusEvent, 0, catalog, context);
                        }

                        // Notify the Bus of the latest requested change, if needed
                        if (!initialEvents.isEmpty()) {
                            notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, defaultSubscriptionBase, initialEvents.get(initialEvents.size() - 1), SubscriptionBaseTransitionType.CREATE, context);
                        }
                    }
                }
                return toSubscriptionBaseEvents(createdEvents);
            }
        });
    }

    @Override
    public void cancelSubscriptionsOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                cancelSubscriptionsFromTransaction(entitySqlDaoWrapperFactory, subscriptions, cancelEvents, catalog, context);
                // Make sure to always send the event, even if there were no subscriptions to cancel
                notifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, event, subscriptions.size(), context);
                return null;
            }
        });
    }

    @Override
    public void notifyOnBasePlanEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final Catalog catalog, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                notifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, event, 0, context);
                return null;
            }
        });

    }

    @Override
    public void cancelSubscriptions(final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                cancelSubscriptionsFromTransaction(entitySqlDaoWrapperFactory, subscriptions, cancelEvents, catalog, context);
                return null;
            }
        });
    }

    private void cancelSubscriptionsFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, final InternalCallContext context) throws EntityPersistenceException {
        for (int i = 0; i < subscriptions.size(); i++) {
            final DefaultSubscriptionBase subscription = subscriptions.get(i);
            final SubscriptionBaseEvent cancelEvent = cancelEvents.get(i);
            cancelSubscriptionFromTransaction(subscription, cancelEvent, entitySqlDaoWrapperFactory, catalog, context, subscriptions.size() - i - 1);
        }
    }

    @Override
    public void uncancelSubscription(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> uncancelEvents, final InternalCallContext context) {
        undoOperation(subscription, uncancelEvents, ApiEventType.CANCEL, SubscriptionBaseTransitionType.UNCANCEL, context);
    }

    @Override
    public void undoChangePlan(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> undoChangePlanEvents, final InternalCallContext context) {
        undoOperation(subscription, undoChangePlanEvents, ApiEventType.CHANGE, SubscriptionBaseTransitionType.UNDO_CHANGE, context);
    }

    private void undoOperation(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> inputEvents, final ApiEventType targetOperation, final SubscriptionBaseTransitionType transitionType, final InternalCallContext context) {

        final InternalCallContext contextWithUpdatedDate = contextWithUpdatedDate(context);
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionEventSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);

                final UUID subscriptionId = subscription.getId();

                Set<SubscriptionEventModelDao> targetEvents = new HashSet<SubscriptionEventModelDao>();
                final Date now = context.getCreatedDate().toDate();
                final List<SubscriptionEventModelDao> eventModels = transactional.getFutureActiveEventForSubscription(subscriptionId.toString(), now, contextWithUpdatedDate);

                for (final SubscriptionEventModelDao cur : eventModels) {
                    if (cur.getEventType() == EventType.API_USER && cur.getUserType() == targetOperation) {
                        targetEvents.add(cur);
                    } else if (cur.getEventType() == EventType.PHASE) {
                        targetEvents.add(cur);
                    }
                }

                if (!targetEvents.isEmpty()) {
                    for (SubscriptionEventModelDao target : targetEvents) {
                        transactional.unactiveEvent(target.getId().toString(), contextWithUpdatedDate);
                    }
                    for (final SubscriptionBaseEvent cur : inputEvents) {
                        transactional.create(new SubscriptionEventModelDao(cur), contextWithUpdatedDate);
                        recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                                cur.getEffectiveDate(),
                                                                new SubscriptionNotificationKey(cur.getId()),
                                                                contextWithUpdatedDate);
                    }

                    // Notify the Bus of the latest requested change
                    notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, inputEvents.get(inputEvents.size() - 1), transitionType, contextWithUpdatedDate);
                }

                return null;
            }
        });
    }

    @Override
    public void changePlan(final DefaultSubscriptionBase subscription, final List<SubscriptionBaseEvent> originalInputChangeEvents, final List<DefaultSubscriptionBase> subscriptionsToBeCancelled, final List<SubscriptionBaseEvent> cancelEvents, final Catalog catalog, final InternalCallContext context) {

        // First event is expected to be the subscription CHANGE event
        final SubscriptionBaseEvent inputChangeEvent = originalInputChangeEvents.get(0);
        Preconditions.checkState(inputChangeEvent.getType() == EventType.API_USER &&
                                 ((ApiEvent) inputChangeEvent).getApiEventType() == ApiEventType.CHANGE);
        Preconditions.checkState(inputChangeEvent.getSubscriptionId().equals(subscription.getId()));

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final SubscriptionEventSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);
                final List<SubscriptionEventModelDao> activeSubscriptionEvents = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getActiveEventsForSubscription(subscription.getId().toString(), context);

                // First event is CREATE/TRANSFER event
                final SubscriptionEventModelDao firstSubscriptionEvent = activeSubscriptionEvents.get(0);
                final Iterable<SubscriptionEventModelDao> activePresentOrFutureSubscriptionEvents = Iterables.filter(activeSubscriptionEvents, new Predicate<SubscriptionEventModelDao>() {
                    @Override
                    public boolean apply(SubscriptionEventModelDao input) {
                        return input.getEffectiveDate().compareTo(inputChangeEvent.getEffectiveDate()) >= 0;
                    }
                });

                // We do a little magic here in case the CHANGE coincides exactly with the CREATE event to invalidate original CREATE event and
                // change the input CHANGE event into a CREATE event.
                final boolean isChangePlanOnStartDate = firstSubscriptionEvent.getEffectiveDate().compareTo(inputChangeEvent.getEffectiveDate()) == 0;

                final List<SubscriptionBaseEvent> inputChangeEvents;
                if (isChangePlanOnStartDate) {

                    // Rebuild input event list with first the CREATE event and all original input events except for inputChangeEvent
                    inputChangeEvents = new ArrayList<SubscriptionBaseEvent>();
                    final SubscriptionBaseEvent newCreateEvent = new ApiEventBuilder((ApiEventChange) inputChangeEvent)
                            .setApiEventType(firstSubscriptionEvent.getUserType())
                            .build();

                    originalInputChangeEvents.remove(0);
                    inputChangeEvents.add(newCreateEvent);
                    inputChangeEvents.addAll(originalInputChangeEvents);

                    // Deactivate original CREATE event
                    unactivateEventFromTransaction(firstSubscriptionEvent, entitySqlDaoWrapperFactory, context);

                } else {
                    inputChangeEvents = originalInputChangeEvents;
                }

                cancelFutureEventsFromTransaction(activePresentOrFutureSubscriptionEvents, entitySqlDaoWrapperFactory, false, context);

                for (final SubscriptionBaseEvent cur : inputChangeEvents) {
                    createAndRefresh(transactional, new SubscriptionEventModelDao(cur), context);

                    final boolean isBusEvent = cur.getEffectiveDate().compareTo(context.getCreatedDate()) <= 0 && (cur.getType() == EventType.API_USER || cur.getType() == EventType.BCD_UPDATE);
                    recordBusOrFutureNotificationFromTransaction(subscription, cur, entitySqlDaoWrapperFactory, isBusEvent, 0, catalog, context);
                }

                // Notify the Bus of the latest requested change
                final SubscriptionBaseEvent finalEvent = inputChangeEvents.get(inputChangeEvents.size() - 1);
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, finalEvent, SubscriptionBaseTransitionType.CHANGE, context);

                // Cancel associated add-ons
                cancelSubscriptionsFromTransaction(entitySqlDaoWrapperFactory, subscriptionsToBeCancelled, cancelEvents, catalog, context);

                return null;
            }
        });
    }

    private List<SubscriptionBaseEvent> filterSubscriptionBaseEvents(final Collection<SubscriptionEventModelDao> models) {
        final Collection<SubscriptionEventModelDao> filteredModels = Collections2.filter(models, new Predicate<SubscriptionEventModelDao>() {
            @Override
            public boolean apply(@Nullable final SubscriptionEventModelDao input) {
                return input.getUserType() != ApiEventType.UNCANCEL && input.getUserType() != ApiEventType.UNDO_CHANGE;
            }
        });
        return toSubscriptionBaseEvents(filteredModels);
    }

    private List<SubscriptionBaseEvent> toSubscriptionBaseEvents(final Collection<SubscriptionEventModelDao> eventModels) {
        return new ArrayList<SubscriptionBaseEvent>(Collections2.transform(eventModels, new Function<SubscriptionEventModelDao, SubscriptionBaseEvent>() {
            @Override
            public SubscriptionBaseEvent apply(final SubscriptionEventModelDao input) {
                return SubscriptionEventModelDao.toSubscriptionEvent(input);
            }
        }));
    }

    private List<SubscriptionBaseEvent> getEventsForAccountId(final InternalTenantContext context) {
        return transactionalSqlDao.execute(true, new EntitySqlDaoTransactionWrapper<List<SubscriptionBaseEvent>>() {
            @Override
            public List<SubscriptionBaseEvent> inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final List<SubscriptionEventModelDao> models = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getByAccountRecordId(context);
                return filterSubscriptionBaseEvents(models);
            }
        });
    }

    private void cancelSubscriptionFromTransaction(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent cancelEvent, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final Catalog catalog, final InternalCallContext context, final int seqId)
            throws EntityPersistenceException {
        final UUID subscriptionId = subscription.getId();
        cancelFutureEventsFromTransaction(subscriptionId, cancelEvent.getEffectiveDate(), entitySqlDaoWrapperFactory, true, context);
        final SubscriptionEventSqlDao subscriptionEventSqlDao = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);
        final SubscriptionEventModelDao cancelEventWithUpdatedTotalOrdering = createAndRefresh(subscriptionEventSqlDao, new SubscriptionEventModelDao(cancelEvent), context);

        final SubscriptionBaseEvent refreshedSubscriptionEvent = SubscriptionEventModelDao.toSubscriptionEvent(cancelEventWithUpdatedTotalOrdering);
        final boolean isBusEvent = refreshedSubscriptionEvent.getEffectiveDate().compareTo(context.getCreatedDate()) <= 0;
        recordBusOrFutureNotificationFromTransaction(subscription, refreshedSubscriptionEvent, entitySqlDaoWrapperFactory, isBusEvent, seqId, catalog, context);

        // Notify the Bus of the requested change
        notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, refreshedSubscriptionEvent, SubscriptionBaseTransitionType.CANCEL, context);
    }

    private void cancelNextPhaseEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) {
        cancelFutureEventFromTransaction(subscriptionId, entitySqlDaoWrapperFactory, EventType.PHASE, null, context);
    }

    private void cancelFutureEventsFromTransaction(final UUID subscriptionId, final DateTime effectiveDate, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final boolean includingBCDChange, final InternalCallContext context) {
        final List<SubscriptionEventModelDao> eventModels = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getFutureOrPresentActiveEventForSubscription(subscriptionId.toString(), effectiveDate.toDate(), context);
        cancelFutureEventsFromTransaction(eventModels, entitySqlDaoWrapperFactory, includingBCDChange, context);
    }

    private void cancelFutureEventsFromTransaction(final Iterable<SubscriptionEventModelDao> eventModels, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final boolean includingBCDChange, final InternalCallContext context) {
        for (final SubscriptionEventModelDao cur : eventModels) {
            // Skip CREATE event (because of date equality in the query and we don't want to invalidate CREATE event that match a CANCEL event)
            if (cur.getEventType() == EventType.API_USER && (cur.getUserType() == ApiEventType.CREATE || cur.getUserType() == ApiEventType.TRANSFER)) {
                continue;
            }

            if (includingBCDChange || cur.getEventType() != EventType.BCD_UPDATE) {
                unactivateEventFromTransaction(cur, entitySqlDaoWrapperFactory, context);
            }
        }
    }

    private void cancelFutureEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory dao, final EventType type,
                                                  @Nullable final ApiEventType apiType, final InternalCallContext context) {
        final SubscriptionEventModelDao futureEvent = findFutureEventFromTransaction(subscriptionId, dao, type, apiType, context);
        unactivateEventFromTransaction(futureEvent, dao, context);
    }

    private SubscriptionEventModelDao findFutureEventFromTransaction(final UUID subscriptionId, final EntitySqlDaoWrapperFactory dao, final EventType type,
                                                                     @Nullable final ApiEventType apiType, final InternalCallContext context) {

        SubscriptionEventModelDao futureEvent = null;
        final Date now = context.getCreatedDate().toDate();

        final List<SubscriptionEventModelDao> eventModels = dao.become(SubscriptionEventSqlDao.class).getFutureActiveEventForSubscription(subscriptionId.toString(), now, context);
        for (final SubscriptionEventModelDao cur : eventModels) {
            if (cur.getEventType() == type &&
                (apiType == null || apiType == cur.getUserType())) {
                if (futureEvent != null) {
                    throw new SubscriptionBaseError(String.format("Found multiple future events for type %s for subscriptions %s",
                                                                  type, subscriptionId.toString()));
                }
                futureEvent = cur;
                // To check that there is only one such event
                //break;
            }
        }
        return futureEvent;
    }

    private void unactivateEventFromTransaction(final Entity event, final EntitySqlDaoWrapperFactory dao, final InternalCallContext context) {
        if (event != null) {
            final String eventId = event.getId().toString();
            dao.become(SubscriptionEventSqlDao.class).unactiveEvent(eventId, context);
        }
    }

    private DefaultSubscriptionBase buildSubscription(final DefaultSubscriptionBase input, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {

        if (input == null) {
            return null;
        }
        final List<DefaultSubscriptionBase> bundleInput = new ArrayList<DefaultSubscriptionBase>();
        if (input.getCategory() == ProductCategory.ADD_ON) {
            final DefaultSubscriptionBase baseSubscription = getBaseSubscription(input.getBundleId(), false, catalog, context);
            if (baseSubscription == null) {
                return null;
            }

            bundleInput.add(baseSubscription);
            bundleInput.add(input);
        } else {
            bundleInput.add(input);
        }

        final List<DefaultSubscriptionBase> reloadedSubscriptions = buildBundleSubscriptions(bundleInput, null, null, catalog, context);
        for (final DefaultSubscriptionBase cur : reloadedSubscriptions) {
            if (cur.getId().equals(input.getId())) {
                return cur;
            }
        }

        throw new SubscriptionBaseError("Unexpected code path in buildSubscription");
    }

    private List<DefaultSubscriptionBase> buildBundleSubscriptions(final List<DefaultSubscriptionBase> input, @Nullable final Multimap<UUID, SubscriptionBaseEvent> eventsForSubscription,
                                                            @Nullable final Collection<SubscriptionBaseEvent> dryRunEvents, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }

        // Make sure BasePlan -- if exists-- is first
        Collections.sort(input, DefaultSubscriptionInternalApi.SUBSCRIPTIONS_COMPARATOR);

        final Collection<ApiEventChange> baseChangeEvents = new LinkedList<ApiEventChange>();
        ApiEventCancel baseCancellationEvent = null;
        final List<DefaultSubscriptionBase> result = new ArrayList<DefaultSubscriptionBase>(input.size());
        for (final DefaultSubscriptionBase cur : input) {
            final List<SubscriptionBaseEvent> events = eventsForSubscription != null ?
                                                       (List<SubscriptionBaseEvent>) eventsForSubscription.get(cur.getId()) :
                                                       getEventsForSubscription(cur.getId(), context);
            mergeDryRunEvents(cur.getId(), events, dryRunEvents);

            DefaultSubscriptionBase reloaded = createSubscriptionForInternalUse(cur, events, catalog, context);

            switch (cur.getCategory()) {
                case BASE:
                    for (final SubscriptionBaseEvent event : events) {
                        if (!event.isActive()) {
                            continue;
                        } else if (event instanceof ApiEventCancel) {
                            baseCancellationEvent = (ApiEventCancel) event;
                            break;
                        } else if (event instanceof ApiEventChange) {
                            // Need to track all changes, see https://github.com/killbill/killbill/issues/268
                            baseChangeEvents.add((ApiEventChange) event);
                        }
                    }
                    break;
                case ADD_ON:
                    final Plan targetAddOnPlan = reloaded.getCurrentPlan();
                    if (targetAddOnPlan == null || reloaded.getFutureEndDate() != null) {
                        // TODO What if reloaded.getFutureEndDate() is not null but a base plan change
                        // triggers another cancellation before?
                        break;
                    }

                    SubscriptionBaseEvent baseTriggerEventForAddOnCancellation = baseCancellationEvent;
                    for (final ApiEventChange baseChangeEvent : baseChangeEvents) {
                        final Plan basePlan = catalog.findPlan(baseChangeEvent.getEventPlan(), baseChangeEvent.getEffectiveDate(), cur.getAlignStartDate());
                        final Product baseProduct = basePlan.getProduct();

                        if ((!addonUtils.isAddonAvailable(baseProduct, targetAddOnPlan)) ||
                            (addonUtils.isAddonIncluded(baseProduct, targetAddOnPlan))) {
                            if (baseTriggerEventForAddOnCancellation != null) {
                                if (baseTriggerEventForAddOnCancellation.getEffectiveDate().isAfter(baseChangeEvent.getEffectiveDate())) {
                                    baseTriggerEventForAddOnCancellation = baseChangeEvent;
                                }
                            } else {
                                baseTriggerEventForAddOnCancellation = baseChangeEvent;
                            }
                        }
                    }

                    if (baseTriggerEventForAddOnCancellation != null) {
                        final SubscriptionBaseEvent addOnCancelEvent = new ApiEventCancel(new ApiEventBuilder()
                                                                                                  .setSubscriptionId(reloaded.getId())
                                                                                                  .setEffectiveDate(baseTriggerEventForAddOnCancellation.getEffectiveDate())
                                                                                                  .setCreatedDate(baseTriggerEventForAddOnCancellation.getCreatedDate())
                                                                                                  // This event is only there to indicate the ADD_ON is future canceled, but it is not there
                                                                                                  // on disk until the base plan cancellation becomes effective
                                                                                                  .setFromDisk(false));

                        events.add(addOnCancelEvent);
                        // Finally reload subscription with full set of events
                        reloaded = createSubscriptionForInternalUse(cur, events, catalog, context);
                    }
                    break;
                default:
                    break;
            }

            result.add(reloaded);
        }

        return result;
    }

    private void mergeDryRunEvents(final UUID subscriptionId, final List<SubscriptionBaseEvent> events, @Nullable final Collection<SubscriptionBaseEvent> dryRunEvents) {
        if (dryRunEvents == null || dryRunEvents.isEmpty()) {
            return;
        }
        for (final SubscriptionBaseEvent curDryRun : dryRunEvents) {

            boolean swapChangeEventWithCreate = false;

            if (curDryRun.getSubscriptionId() != null && curDryRun.getSubscriptionId().equals(subscriptionId)) {

                final boolean isApiChange = curDryRun.getType() == EventType.API_USER && ((ApiEvent) curDryRun).getApiEventType() == ApiEventType.CHANGE;
                final Iterator<SubscriptionBaseEvent> it = events.iterator();
                while (it.hasNext()) {
                    final SubscriptionBaseEvent event = it.next();
                    if (event.getEffectiveDate().isAfter(curDryRun.getEffectiveDate())) {
                        it.remove();
                    } else if (event.getEffectiveDate().compareTo(curDryRun.getEffectiveDate()) == 0 &&
                               isApiChange &&
                               (event.getType() == EventType.API_USER && (((ApiEvent) event).getApiEventType() == ApiEventType.CREATE) || ((ApiEvent) event).getApiEventType() == ApiEventType.TRANSFER)) {
                        it.remove();
                        swapChangeEventWithCreate = true;
                    }
                }
                // Set total ordering value of the fake dryRun event to make sure billing events are correctly ordered
                // and also transform CHANGE event into CREATE in case of perfect effectiveDate match
                final EventBaseBuilder eventBuilder;
                switch (curDryRun.getType()) {
                    case PHASE:
                        eventBuilder = new PhaseEventBuilder((PhaseEvent) curDryRun);
                        break;
                    case BCD_UPDATE:
                        eventBuilder = new BCDEventBuilder((BCDEvent) curDryRun);
                        break;
                    case API_USER:
                    default:
                        eventBuilder = new ApiEventBuilder((ApiEvent) curDryRun);
                        if (swapChangeEventWithCreate) {
                            ((ApiEventBuilder) eventBuilder).setApiEventType(ApiEventType.CREATE);
                        }
                        break;
                }
                if (!events.isEmpty()) {
                    eventBuilder.setTotalOrdering(events.get(events.size() - 1).getTotalOrdering() + 1);
                }
                events.add(eventBuilder.build());
            }
        }
    }

    @Override
    public void transfer(final UUID srcAccountId, final UUID destAccountId, final BundleTransferData bundleTransferData,
                         final List<TransferCancelData> transferCancelData, final Catalog catalog, final InternalCallContext fromContext, final InternalCallContext toContext) {

        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                // Cancel the subscriptions for the old bundle
                for (final TransferCancelData cancel : transferCancelData) {
                    cancelSubscriptionFromTransaction(cancel.getSubscription(), cancel.getCancelEvent(), entitySqlDaoWrapperFactory, catalog, fromContext, 0);
                }

                // Rename externalKey from source bundle
                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);
                bundleSqlDao.renameBundleExternalKey(bundleTransferData.getData().getExternalKey(), "tsf", fromContext);

                final SubscriptionEventSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);
                transferBundleDataFromTransaction(bundleTransferData, transactional, entitySqlDaoWrapperFactory, toContext);
                return null;
            }
        });
    }

    @Override
    public void updateBundleExternalKey(final UUID bundleId, final String externalKey, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {

                final BundleSqlDao bundleSqlDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);
                bundleSqlDao.updateBundleExternalKey(bundleId.toString(), externalKey, contextWithUpdatedDate(context));
                return null;
            }
        });
    }

    @Override
    public void createBCDChangeEvent(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent bcdEvent, final Catalog catalog, final InternalCallContext context) {
        transactionalSqlDao.execute(false, new EntitySqlDaoTransactionWrapper<Void>() {
            @Override
            public Void inTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory) throws Exception {
                final SubscriptionEventSqlDao transactional = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class);
                createAndRefresh(transactional, new SubscriptionEventModelDao(bcdEvent), context);

                // Notify the Bus
                notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subscription, bcdEvent, SubscriptionBaseTransitionType.BCD_CHANGE, context);
                final boolean isBusEvent = bcdEvent.getEffectiveDate().compareTo(context.getCreatedDate()) <= 0;
                recordBusOrFutureNotificationFromTransaction(subscription, bcdEvent, entitySqlDaoWrapperFactory, isBusEvent, 0, catalog, context);

                return null;
            }
        });

    }

    private DefaultSubscriptionBase createSubscriptionForInternalUse(final SubscriptionBase shellSubscription, final List<SubscriptionBaseEvent> events, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final DefaultSubscriptionBase result = new DefaultSubscriptionBase(new SubscriptionBuilder(((DefaultSubscriptionBase) shellSubscription)), null, clock);

        if (!events.isEmpty()) {
            result.rebuildTransitions(events, catalog);
        }
        return result;
    }

    private DefaultSubscriptionBase getBaseSubscription(final UUID bundleId, final boolean rebuildSubscription, final Catalog catalog, final InternalTenantContext context) throws CatalogApiException {
        final List<DefaultSubscriptionBase> subscriptions = getSubscriptionFromBundleId(bundleId, context);
        for (final DefaultSubscriptionBase cur : subscriptions) {
            if (cur.getCategory() == ProductCategory.BASE) {
                return rebuildSubscription ? buildSubscription(cur, catalog, context) : cur;
            }
        }
        return null;
    }

    //
    // Either records a notification or sends a bus event if operation is immediate
    //
    private void recordBusOrFutureNotificationFromTransaction(final DefaultSubscriptionBase subscription, final SubscriptionBaseEvent event, final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final boolean busEvent,
                                                              final int seqId, final Catalog catalog, final InternalCallContext context) {
        if (busEvent) {
            rebuildSubscriptionAndNotifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, event, seqId, catalog, context);
        } else {
            recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                    event.getEffectiveDate(),
                                                    new SubscriptionNotificationKey(event.getId()),
                                                    context);
        }
    }

    private List<SubscriptionBaseEvent> getEventsForSubscriptionInTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final UUID subscriptionId, final InternalTenantContext context) {
        final List<SubscriptionEventModelDao> models = entitySqlDaoWrapperFactory.become(SubscriptionEventSqlDao.class).getActiveEventsForSubscription(subscriptionId.toString(), context);
        return filterSubscriptionBaseEvents(models);
    }

    // Sends bus notification for event on effective date -- only used for operation that happen immediately
    private void rebuildSubscriptionAndNotifyBusOfEffectiveImmediateChange(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final DefaultSubscriptionBase subscription,
                                                                           final SubscriptionBaseEvent immediateEvent, final int seqId, final Catalog catalog, final InternalCallContext context) {
        try {
            // We need to rehydrate the subscription, as some events might have been canceled on disk (e.g. future PHASE after while doing a change plan)
            final List<SubscriptionBaseEvent> activeSubscriptionEvents = getEventsForSubscriptionInTransaction(entitySqlDaoWrapperFactory, subscription.getId(), context);
            subscription.rebuildTransitions(activeSubscriptionEvents, catalog);
            notifyBusOfEffectiveImmediateChange(entitySqlDaoWrapperFactory, subscription, immediateEvent, seqId, context);
        } catch (final CatalogApiException e) {
            log.warn("Failed to post effective event for subscriptionId='{}'", subscription.getId(), e);
        }
    }

    private void notifyBusOfEffectiveImmediateChange(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final DefaultSubscriptionBase subscription,
                                                     final SubscriptionBaseEvent immediateEvent, final int seqId, final InternalCallContext context) {
        try {
            final SubscriptionBaseTransitionData transition = subscription.getTransitionFromEvent(immediateEvent, seqId);
            if (transition != null) {
                final BusEvent busEvent = new DefaultEffectiveSubscriptionEvent(transition,
                                                                                subscription.getAlignStartDate(),
                                                                                context.getUserToken(),
                                                                                context.getAccountRecordId(),
                                                                                context.getTenantRecordId());

                eventBus.postFromTransaction(busEvent, entitySqlDaoWrapperFactory.getHandle().getConnection());
            }
        } catch (final EventBusException e) {
            log.warn("Failed to post effective event for subscriptionId='{}'", subscription.getId(), e);
        }
    }

    private void notifyBusOfRequestedChange(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final DefaultSubscriptionBase subscription,
                                            final SubscriptionBaseEvent nextEvent, final SubscriptionBaseTransitionType transitionType, final InternalCallContext context) {
        try {
            eventBus.postFromTransaction(new DefaultRequestedSubscriptionEvent(subscription, nextEvent, transitionType, context.getAccountRecordId(), context.getTenantRecordId(), context.getUserToken()), entitySqlDaoWrapperFactory.getHandle().getConnection());
        } catch (final EventBusException e) {
            log.warn("Failed to post requested change event for subscriptionId='{}'", subscription.getId(), e);
        }
    }

    private void recordFutureNotificationFromTransaction(final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final DateTime effectiveDate,
                                                         final NotificationEvent notificationKey, final InternalCallContext context) {
        try {
            final NotificationQueue subscriptionEventQueue = notificationQueueService.getNotificationQueue(DefaultSubscriptionBaseService.SUBSCRIPTION_SERVICE_NAME,
                                                                                                           DefaultSubscriptionBaseService.NOTIFICATION_QUEUE_NAME);
            subscriptionEventQueue.recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory.getHandle().getConnection(), effectiveDate, notificationKey, context.getUserToken(), context.getAccountRecordId(), context.getTenantRecordId());
        } catch (final NoSuchNotificationQueue e) {
            throw new RuntimeException(e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void transferBundleDataFromTransaction(final BundleTransferData bundleTransferData, final EntitySqlDao transactional,
                                                   final EntitySqlDaoWrapperFactory entitySqlDaoWrapperFactory, final InternalCallContext context) throws EntityPersistenceException {

        final SubscriptionSqlDao transSubDao = entitySqlDaoWrapperFactory.become(SubscriptionSqlDao.class);
        final BundleSqlDao transBundleDao = entitySqlDaoWrapperFactory.become(BundleSqlDao.class);

        final DefaultSubscriptionBaseBundle bundleData = bundleTransferData.getData();

        final SubscriptionBundleModelDao existingBundleForAccount = transBundleDao.getBundlesFromAccountAndKey(bundleData.getAccountId().toString(), bundleData.getExternalKey(), context);
        if (existingBundleForAccount != null) {
            log.warn("Bundle already exists for accountId='{}', bundleExternalKey='{}'", bundleData.getAccountId(), bundleData.getExternalKey());
            return;
        }

        for (final SubscriptionTransferData curSubscription : bundleTransferData.getSubscriptions()) {
            final DefaultSubscriptionBase subData = curSubscription.getData();
            for (final SubscriptionBaseEvent curEvent : curSubscription.getInitialEvents()) {
                createAndRefresh(transactional, new SubscriptionEventModelDao(curEvent), context);
                recordFutureNotificationFromTransaction(entitySqlDaoWrapperFactory,
                                                        curEvent.getEffectiveDate(),
                                                        new SubscriptionNotificationKey(curEvent.getId()),
                                                        context);
            }
            createAndRefresh(transSubDao, new SubscriptionModelDao(subData), context);

            // Notify the Bus of the latest requested change
            final SubscriptionBaseEvent finalEvent = curSubscription.getInitialEvents().get(curSubscription.getInitialEvents().size() - 1);
            notifyBusOfRequestedChange(entitySqlDaoWrapperFactory, subData, finalEvent, SubscriptionBaseTransitionType.TRANSFER, context);
        }

        createAndRefresh(transBundleDao, new SubscriptionBundleModelDao(bundleData), context);
    }

    private InternalCallContext contextWithUpdatedDate(final InternalCallContext input) {
        return new InternalCallContext(input, input.getCreatedDate());
    }

}
