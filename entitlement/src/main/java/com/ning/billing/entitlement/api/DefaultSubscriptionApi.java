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

package com.ning.billing.entitlement.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;

import org.joda.time.DateTimeZone;

import com.ning.billing.ErrorCode;
import com.ning.billing.ObjectType;
import com.ning.billing.callcontext.InternalTenantContext;
import com.ning.billing.entitlement.AccountEntitlements;
import com.ning.billing.entitlement.EntitlementInternalApi;
import com.ning.billing.subscription.api.SubscriptionBaseInternalApi;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.cache.Cachable.CacheType;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.customfield.ShouldntHappenException;
import com.ning.billing.util.dao.NonEntityDao;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;

public class DefaultSubscriptionApi implements SubscriptionApi {

    private static final Comparator<SubscriptionBundle> SUBSCRIPTION_BUNDLE_COMPARATOR = new Comparator<SubscriptionBundle>() {
        @Override
        public int compare(final SubscriptionBundle o1, final SubscriptionBundle o2) {
            final int compared = o1.getOriginalCreatedDate().compareTo(o2.getOriginalCreatedDate());
            if (compared != 0) {
                return compared;
            } else {
                final int compared2 = o1.getUpdatedDate().compareTo(o2.getUpdatedDate());
                if (compared2 != 0) {
                    return compared2;
                } else {
                    // Default, stable, ordering
                    return o1.getId().compareTo(o2.getId());
                }
            }
        }
    };

    private final EntitlementInternalApi entitlementInternalApi;
    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;
    private final NonEntityDao nonEntityDao;
    private final CacheControllerDispatcher cacheControllerDispatcher;

    @Inject
    public DefaultSubscriptionApi(final EntitlementInternalApi entitlementInternalApi, final SubscriptionBaseInternalApi subscriptionInternalApi,
                                  final InternalCallContextFactory internalCallContextFactory, final NonEntityDao nonEntityDao, final CacheControllerDispatcher cacheControllerDispatcher) {
        this.entitlementInternalApi = entitlementInternalApi;
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.internalCallContextFactory = internalCallContextFactory;
        this.nonEntityDao = nonEntityDao;
        this.cacheControllerDispatcher = cacheControllerDispatcher;
    }

    @Override
    public Subscription getSubscriptionForEntitlementId(final UUID entitlementId, final TenantContext context) throws SubscriptionApiException {
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(entitlementId, ObjectType.SUBSCRIPTION, cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID));
        final UUID accountId = nonEntityDao.retrieveIdFromObject(accountRecordId, ObjectType.ACCOUNT);

        // Retrieve entitlements
        final AccountEntitlements accountEntitlements;
        try {
            accountEntitlements = entitlementInternalApi.getAllEntitlementsForAccountId(accountId, context);
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }

        // Build subscriptions
        final Iterable<Subscription> accountSubscriptions = Iterables.<Subscription>concat(buildSubscriptionsFromEntitlements(accountEntitlements).values());

        return Iterables.<Subscription>find(accountSubscriptions,
                                            new Predicate<Subscription>() {
                                                @Override
                                                public boolean apply(final Subscription subscription) {
                                                    return subscription.getId().equals(entitlementId);
                                                }
                                            });
    }

    @Override
    public SubscriptionBundle getSubscriptionBundle(final UUID bundleId, final TenantContext context) throws SubscriptionApiException {
        final Long accountRecordId = nonEntityDao.retrieveAccountRecordIdFromObject(bundleId, ObjectType.BUNDLE, cacheControllerDispatcher.getCacheController(CacheType.ACCOUNT_RECORD_ID));
        final UUID accountId = nonEntityDao.retrieveIdFromObject(accountRecordId, ObjectType.ACCOUNT);

        final Optional<SubscriptionBundle> bundleOptional = Iterables.<SubscriptionBundle>tryFind(getSubscriptionBundlesForAccount(accountId, context),
                                                                                                  new Predicate<SubscriptionBundle>() {
                                                                                                      @Override
                                                                                                      public boolean apply(final SubscriptionBundle bundle) {
                                                                                                          return bundle.getId().equals(bundleId);
                                                                                                      }
                                                                                                  });
        if (!bundleOptional.isPresent()) {
            throw new SubscriptionApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_ID, bundleId);
        } else {
            return bundleOptional.get();
        }
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext context) throws SubscriptionApiException {
        return ImmutableList.<SubscriptionBundle>copyOf(Iterables.<SubscriptionBundle>filter(getSubscriptionBundlesForAccount(accountId, context),
                                                                                             new Predicate<SubscriptionBundle>() {
                                                                                                 @Override
                                                                                                 public boolean apply(final SubscriptionBundle bundle) {
                                                                                                     return bundle.getExternalKey().equals(externalKey);
                                                                                                 }
                                                                                             }));
    }

    @Override
    public SubscriptionBundle getActiveSubscriptionBundleForExternalKey(final String externalKey, final TenantContext context) throws SubscriptionApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        try {
            final SubscriptionBaseBundle baseBundle = subscriptionInternalApi.getActiveBundleForKey(externalKey, internalContext);
            return getSubscriptionBundle(baseBundle.getId(), context);
        } catch (SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForExternalKey(final String externalKey, final TenantContext context) throws SubscriptionApiException {
        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        final List<SubscriptionBaseBundle> baseBundles = subscriptionInternalApi.getBundlesForKey(externalKey, internalContext);

        final List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>(baseBundles.size());
        for (final SubscriptionBaseBundle cur : baseBundles) {
            final SubscriptionBundle bundle = getSubscriptionBundle(cur.getId(), context);
            result.add(bundle);
        }

        return result;
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountId(final UUID accountId, final TenantContext context) throws SubscriptionApiException {
        return getSubscriptionBundlesForAccount(accountId, context);
    }

    private List<SubscriptionBundle> getSubscriptionBundlesForAccount(final UUID accountId, final TenantContext tenantContext) throws SubscriptionApiException {
        // Retrieve entitlements
        final AccountEntitlements accountEntitlements;
        try {
            accountEntitlements = entitlementInternalApi.getAllEntitlementsForAccountId(accountId, tenantContext);
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }

        // Build subscriptions
        final Map<UUID, List<Subscription>> subscriptionsPerBundle = buildSubscriptionsFromEntitlements(accountEntitlements);

        final DateTimeZone accountTimeZone = accountEntitlements.getAccount().getTimeZone();

        // Build subscription bundles
        final List<SubscriptionBundle> bundles = new LinkedList<SubscriptionBundle>();
        for (final UUID bundleId : subscriptionsPerBundle.keySet()) {
            final List<Subscription> subscriptionsForBundle = subscriptionsPerBundle.get(bundleId);
            final String externalKey = subscriptionsForBundle.get(0).getExternalKey();

            final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountTimeZone,
                                                                                              accountId,
                                                                                              bundleId,
                                                                                              externalKey,
                                                                                              accountEntitlements.getEntitlements().get(bundleId));

            final SubscriptionBaseBundle baseBundle = accountEntitlements.getBundles().get(bundleId);
            final SubscriptionBundle subscriptionBundle = new DefaultSubscriptionBundle(bundleId,
                                                                                        accountId,
                                                                                        externalKey,
                                                                                        subscriptionsForBundle,
                                                                                        timeline,
                                                                                        baseBundle.getOriginalCreatedDate(),
                                                                                        baseBundle.getCreatedDate(),
                                                                                        baseBundle.getUpdatedDate());
            bundles.add(subscriptionBundle);
        }

        // Sort the results for predictability
        return Ordering.<SubscriptionBundle>from(SUBSCRIPTION_BUNDLE_COMPARATOR).sortedCopy(bundles);
    }

    private Map<UUID, List<Subscription>> buildSubscriptionsFromEntitlements(final AccountEntitlements accountEntitlements) {
        final Map<UUID, List<Subscription>> subscriptionsPerBundle = new HashMap<UUID, List<Subscription>>();
        for (final UUID bundleId : accountEntitlements.getEntitlements().keySet()) {
            if (subscriptionsPerBundle.get(bundleId) == null) {
                subscriptionsPerBundle.put(bundleId, new LinkedList<Subscription>());
            }

            for (final Entitlement entitlement : accountEntitlements.getEntitlements().get(bundleId)) {
                if (entitlement instanceof DefaultEntitlement) {
                    subscriptionsPerBundle.get(bundleId).add(new DefaultSubscription((DefaultEntitlement) entitlement));
                } else {
                    throw new ShouldntHappenException("Entitlement should be a DefaultEntitlement instance");
                }
            }
        }
        return subscriptionsPerBundle;
    }
}
