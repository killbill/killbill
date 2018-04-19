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

package org.killbill.billing.entitlement.dao;

import java.util.Collection;
import java.util.LinkedList;

import javax.annotation.Nullable;

import org.killbill.billing.account.api.ImmutableAccountData;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.EventsStream;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.engine.core.EventsStreamBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.IDBI;

import com.google.common.collect.ImmutableList;

public class OptimizedProxyBlockingStateDao extends ProxyBlockingStateDao {

    public OptimizedProxyBlockingStateDao(final EventsStreamBuilder eventsStreamBuilder, final SubscriptionBaseInternalApi subscriptionBaseInternalApi,
                                          final IDBI dbi, final IDBI roDbi, final Clock clock, final NotificationQueueService notificationQueueService, final PersistentBus eventBus,
                                          final CacheControllerDispatcher cacheControllerDispatcher, final NonEntityDao nonEntityDao, final InternalCallContextFactory internalCallContextFactory) {
        super(eventsStreamBuilder, subscriptionBaseInternalApi, dbi, roDbi, clock, notificationQueueService, eventBus, cacheControllerDispatcher, nonEntityDao, internalCallContextFactory);
    }

    /**
     * Retrieve blocking states for a given subscription
     * <p/>
     * If the specified subscription is not an add-on, we already have the blocking states
     * (they are all on disk) - we simply return them and there is nothing to do.
     * Otherwise, for add-ons, we will need to compute the blocking states not on disk.
     * <p/>
     * This is a special method for EventsStreamBuilder to save some DAO calls.
     *
     * @param subscriptionBlockingStatesOnDisk  blocking states on disk for that subscription
     * @param allBlockingStatesOnDiskForAccount all blocking states on disk for that account
     * @param account                           account associated with the subscription
     * @param bundle                            bundle associated with the subscription
     * @param baseSubscription                  base subscription (ProductCategory.BASE) associated with that bundle
     * @param subscription                      subscription for which to build blocking states
     * @param allSubscriptionsForBundle         all subscriptions associated with that bundle
     * @param accountBCD                        account BCD
     * @param catalog                           full Catalog
     * @param context                           call context
     * @return blocking states for that subscription
     * @throws EntitlementApiException
     */
    public Collection<BlockingState> getBlockingHistory(final Collection<BlockingState> subscriptionBlockingStatesOnDisk,
                                                        final Collection<BlockingState> allBlockingStatesOnDiskForAccount,
                                                        final ImmutableAccountData account,
                                                        final SubscriptionBaseBundle bundle,
                                                        @Nullable final SubscriptionBase baseSubscription,
                                                        final SubscriptionBase subscription,
                                                        final Collection<SubscriptionBase> allSubscriptionsForBundle,
                                                        final int accountBCD,
                                                        final Catalog catalog,
                                                        final InternalTenantContext context) throws EntitlementApiException {
        // blockable id points to a subscription, but make sure it's an add-on
        if (!ProductCategory.ADD_ON.equals(subscription.getCategory())) {
            // blockable id points to a base or standalone subscription, there is nothing to do
            return subscriptionBlockingStatesOnDisk;
        }

        // Find all base entitlements that we care about (for which we want to find future cancelled add-ons)
        final Iterable<EventsStream> eventsStreams = ImmutableList.<EventsStream>of(eventsStreamBuilder.buildForEntitlement(allBlockingStatesOnDiskForAccount,
                                                                                                                            account,
                                                                                                                            bundle,
                                                                                                                            baseSubscription,
                                                                                                                            allSubscriptionsForBundle,
                                                                                                                            accountBCD,
                                                                                                                            catalog,
                                                                                                                            context));

        return addBlockingStatesNotOnDisk(subscription.getId(),
                                          BlockingStateType.SUBSCRIPTION,
                                          new LinkedList<BlockingState>(subscriptionBlockingStatesOnDisk),
                                          ImmutableList.<SubscriptionBase>of(baseSubscription),
                                          eventsStreams);
    }
}
