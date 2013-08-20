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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTimeZone;

import com.ning.billing.ErrorCode;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;

public class DefaultSubscriptionApi implements SubscriptionApi  {

    private final SubscriptionBaseInternalApi subscriptionInternalApi;
    private final EntitlementApi entitlementApi;
    private final BlockingChecker checker;
    private final BlockingStateDao blockingStateDao;
    private final EntitlementDateHelper dateHelper;
    private final Clock clock;
    private final InternalCallContextFactory internalCallContextFactory;
    private final AccountInternalApi accountApi;

    @Inject
    public DefaultSubscriptionApi(final SubscriptionBaseInternalApi subscriptionInternalApi, final EntitlementApi entitlementApi, final BlockingChecker checker, final BlockingStateDao blockingStateDao, final AccountInternalApi accountApi, final Clock clock, final InternalCallContextFactory internalCallContextFactory) {
        this.subscriptionInternalApi = subscriptionInternalApi;
        this.entitlementApi = entitlementApi;
        this.accountApi = accountApi;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
        this.dateHelper = new EntitlementDateHelper(accountApi, clock);;
        this.clock = clock;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Subscription getSubscriptionForEntitlementId(final UUID entitlementId, final TenantContext context) throws SubscriptionApiException {
        try {
            final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, context);
            final InternalTenantContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalTenantContext(entitlement.getAccountId(), context);
            return fromEntitlement(entitlement, contextWithValidAccountRecordId);
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public SubscriptionBundle getSubscriptionBundle(final UUID bundleId, final TenantContext context) throws SubscriptionApiException {

        try {

            final List<Entitlement> entitlements = entitlementApi.getAllEntitlementsForBundle(bundleId, context);
            if (entitlements.isEmpty()) {
                throw new SubscriptionApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_ID, bundleId);
            }
            return getSubscriptionBundleFromEntitlements(bundleId, entitlements, context);
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        } catch (AccountApiException e) {
            throw new SubscriptionApiException(e);
        }
    }



    @Override
    public SubscriptionBundle getSubscriptionBundleForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext context) throws SubscriptionApiException {

        try {
            final List<Entitlement> entitlements = entitlementApi.getAllEntitlementsForAccountIdAndExternalKey(accountId, externalKey, context);
            if (entitlements.isEmpty()) {
                throw new SubscriptionApiException(ErrorCode.SUB_GET_INVALID_BUNDLE_KEY, externalKey);
            }
            return getSubscriptionBundleFromEntitlements(entitlements.get(0).getBundleId(), entitlements, context);
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        } catch (AccountApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForExternalKey(final String externalKey, final TenantContext context) throws SubscriptionApiException {

        final InternalTenantContext internalContext = internalCallContextFactory.createInternalTenantContext(context);
        final List<SubscriptionBaseBundle> bundles = subscriptionInternalApi.getBundlesForKey(externalKey, internalContext);

        final  List<SubscriptionBundle> result = new ArrayList<SubscriptionBundle>(bundles.size());
        for (SubscriptionBaseBundle cur : bundles) {
            final SubscriptionBundle bundle = getSubscriptionBundleForAccountIdAndExternalKey(cur.getAccountId(), cur.getExternalKey(), context);
            result.add(bundle);
        }
        return result;
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountId(final UUID accountId, final TenantContext context) throws SubscriptionApiException {
        try {

            final List<Entitlement> entitlements = entitlementApi.getAllEntitlementsForAccountId(accountId, context);
            if (entitlements.isEmpty()) {
                return Collections.emptyList();
            }

            final ListMultimap<UUID, Entitlement> perBundleEntitlements = LinkedListMultimap.create();
            for (Entitlement cur : entitlements) {
                perBundleEntitlements.put(cur.getBundleId(), cur);
            }

            final List<SubscriptionBundle> result  = new ArrayList<SubscriptionBundle>(perBundleEntitlements.keySet().size());
            for (UUID bundleId : perBundleEntitlements.keySet()) {
                final List<Entitlement> e = perBundleEntitlements.get(bundleId);
                final SubscriptionBundle b = getSubscriptionBundleFromEntitlements(bundleId, e, context);
                result.add(b);
            }
            return result;
        } catch (EntitlementApiException e) {
            throw new SubscriptionApiException(e);
        } catch (SubscriptionBaseApiException e) {
            throw new SubscriptionApiException(e);
        } catch (AccountApiException e) {
            throw new SubscriptionApiException(e);
        }
    }

    private Subscription fromEntitlement(final Entitlement entitlement, final InternalTenantContext internalTenantContext) {

        final List<BlockingState> states = blockingStateDao.getBlockingState(entitlement.getId(), internalTenantContext);
        final Subscription result = new DefaultSubscription((DefaultEntitlement) entitlement, states);
        return result;
    }

    private SubscriptionBundle getSubscriptionBundleFromEntitlements(final UUID bundleId, final List<Entitlement> entitlements, final TenantContext context) throws SubscriptionBaseApiException, AccountApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(context);
        final SubscriptionBaseBundle baseBundle = subscriptionInternalApi.getBundleFromId(bundleId, internalTenantContext);
        final List<Subscription> subscriptions = new ArrayList<Subscription>();
        subscriptions.addAll(Collections2.transform(entitlements, new Function<Entitlement, Subscription>() {
            @Override
            public Subscription apply(final Entitlement input) {
                return fromEntitlement(input, internalTenantContext);
            }
        }));

        final Account account = accountApi.getAccountById(baseBundle.getAccountId(), internalTenantContext);
        final DateTimeZone accountTimeZone = account.getTimeZone();

        final List<BlockingState> allBlockingStates = new ArrayList<BlockingState>();
        allBlockingStates.addAll(blockingStateDao.getBlockingAll(account.getId(), internalTenantContext));
        allBlockingStates.addAll(blockingStateDao.getBlockingAll(bundleId, internalTenantContext));
        for (Entitlement cur : entitlements) {
            allBlockingStates.addAll(blockingStateDao.getBlockingAll(cur.getId(), internalTenantContext));
        }

        final SubscriptionBundleTimeline timeline = new DefaultSubscriptionBundleTimeline(accountTimeZone, account.getId(), bundleId, baseBundle.getExternalKey(), entitlements, allBlockingStates);
        final DefaultSubscriptionBundle bundle = new DefaultSubscriptionBundle(bundleId, baseBundle.getAccountId(), baseBundle.getExternalKey(), subscriptions, timeline, baseBundle.getCreatedDate(), baseBundle.getUpdatedDate());
        return bundle;
    }
}
