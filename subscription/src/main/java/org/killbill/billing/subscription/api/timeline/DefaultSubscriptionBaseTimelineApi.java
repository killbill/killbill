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

package org.killbill.billing.subscription.api.timeline;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.subscription.api.SubscriptionApiBase;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.clock.Clock;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

public class DefaultSubscriptionBaseTimelineApi extends SubscriptionApiBase implements SubscriptionBaseTimelineApi {

    private final CatalogInternalApi catalogInternalApi;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultSubscriptionBaseTimelineApi(final CatalogInternalApi catalogService,
                                              final SubscriptionBaseApiService apiService,
                                              final SubscriptionDao dao,
                                              final InternalCallContextFactory internalCallContextFactory,
                                              final Clock clock) {
        super(dao, apiService, clock);
        this.catalogInternalApi = catalogService;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public BundleBaseTimeline getBundleTimeline(final SubscriptionBaseBundle bundle, final TenantContext context)
            throws SubscriptionBaseRepairException {
        try {


            final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(bundle.getAccountId(), context);
            final Catalog fullCatalog = catalogInternalApi.getFullCatalog(true, true, internalTenantContext);
            final List<DefaultSubscriptionBase> subscriptions = dao.getSubscriptions(bundle.getId(),
                                                                                     ImmutableList.<SubscriptionBaseEvent>of(),
                                                                                     fullCatalog,
                                                                                     internalTenantContext);
            if (subscriptions.size() == 0) {
                throw new SubscriptionBaseRepairException(ErrorCode.SUB_NO_ACTIVE_SUBSCRIPTIONS, bundle.getId());
            }
            final String viewId = getViewId(((DefaultSubscriptionBaseBundle) bundle).getLastSysUpdateDate(), subscriptions);
            final List<SubscriptionBaseTimeline> repairs = createGetSubscriptionRepairList(subscriptions, Collections.<SubscriptionBaseTimeline>emptyList(), fullCatalog, internalTenantContext);
            return createGetBundleRepair(bundle.getId(), bundle.getExternalKey(), viewId, repairs);
        } catch (CatalogApiException e) {
            throw new SubscriptionBaseRepairException(e);
        }
    }

    private String getViewId(final DateTime lastUpdateBundleDate, final List<DefaultSubscriptionBase> subscriptions) {
        final StringBuilder tmp = new StringBuilder();
        long lastOrderedId = -1;
        for (final SubscriptionBase cur : subscriptions) {
            lastOrderedId = lastOrderedId < ((DefaultSubscriptionBase) cur).getLastEventOrderedId() ? ((DefaultSubscriptionBase) cur).getLastEventOrderedId() : lastOrderedId;
        }
        tmp.append(lastOrderedId);
        tmp.append("-");
        tmp.append(lastUpdateBundleDate.toDate().getTime());

        return tmp.toString();
    }

    private BundleBaseTimeline createGetBundleRepair(final UUID bundleId, final String externalKey, final String viewId, final List<SubscriptionBaseTimeline> repairList) {
        return new BundleBaseTimeline() {
            @Override
            public String getViewId() {
                return viewId;
            }

            @Override
            public List<SubscriptionBaseTimeline> getSubscriptions() {
                return repairList;
            }

            @Override
            public UUID getId() {
                return bundleId;
            }

            @Override
            public DateTime getCreatedDate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public DateTime getUpdatedDate() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getExternalKey() {
                return externalKey;
            }
        };
    }

    private List<SubscriptionBaseTimeline> createGetSubscriptionRepairList(final List<DefaultSubscriptionBase> subscriptions, final List<SubscriptionBaseTimeline> inRepair, final Catalog fullCatalog, final InternalTenantContext tenantContext) throws CatalogApiException {

        final List<SubscriptionBaseTimeline> result = new LinkedList<SubscriptionBaseTimeline>();
        final Set<UUID> repairIds = new TreeSet<UUID>();
        for (final SubscriptionBaseTimeline cur : inRepair) {
            repairIds.add(cur.getId());
            result.add(cur);
        }

        for (final SubscriptionBase cur : subscriptions) {
            if (!repairIds.contains(cur.getId())) {
                result.add(new DefaultSubscriptionBaseTimeline((DefaultSubscriptionBase) cur, fullCatalog));
            }
        }
        return result;
    }

}

