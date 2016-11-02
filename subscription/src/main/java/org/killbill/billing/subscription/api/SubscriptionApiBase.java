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

package org.killbill.billing.subscription.api;

import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.SubscriptionBuilder;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.events.SubscriptionBaseEvent;
import org.killbill.clock.Clock;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class SubscriptionApiBase {

    protected final SubscriptionDao dao;

    protected final SubscriptionBaseApiService apiService;
    protected final Clock clock;
    protected final CatalogService catalogService;

    public SubscriptionApiBase(final SubscriptionDao dao, final SubscriptionBaseApiService apiService, final Clock clock, final CatalogService catalogService) {
        this.dao = dao;
        this.apiService = apiService;
        this.clock = clock;
        this.catalogService = catalogService;
    }

    protected List<SubscriptionBase> createSubscriptionsForApiUse(final List<SubscriptionBase> internalSubscriptions) {
        return new ArrayList<SubscriptionBase>(Collections2.transform(internalSubscriptions, new Function<SubscriptionBase, SubscriptionBase>() {
            @Override
            public SubscriptionBase apply(final SubscriptionBase subscription) {
                return createSubscriptionForApiUse((DefaultSubscriptionBase) subscription);
            }
        }));
    }

    protected DefaultSubscriptionBase createSubscriptionForApiUse(final SubscriptionBase internalSubscription) {
        return new DefaultSubscriptionBase((DefaultSubscriptionBase) internalSubscription, apiService, clock);
    }

    protected DefaultSubscriptionBase createSubscriptionForApiUse(SubscriptionBuilder builder, List<SubscriptionBaseEvent> events, final InternalTenantContext context) throws CatalogApiException {
        final DefaultSubscriptionBase subscription = new DefaultSubscriptionBase(builder, apiService, clock);
        if (events.size() > 0) {
            subscription.rebuildTransitions(events, catalogService.getFullCatalog(true, true, context));
        }
        return subscription;
    }
}
