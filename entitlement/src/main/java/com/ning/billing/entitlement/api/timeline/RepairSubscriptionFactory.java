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

package com.ning.billing.entitlement.api.timeline;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.glue.DefaultEntitlementModule;
import com.ning.billing.util.clock.Clock;

public class RepairSubscriptionFactory extends DefaultSubscriptionFactory implements SubscriptionFactory {
    private final AddonUtils addonUtils;
    private final EntitlementDao repairDao;

    @Inject
    public RepairSubscriptionFactory(@Named(DefaultEntitlementModule.REPAIR_NAMED) final SubscriptionApiService apiService,
                                     @Named(DefaultEntitlementModule.REPAIR_NAMED) final EntitlementDao dao,
                                     final Clock clock, final CatalogService catalogService, final AddonUtils addonUtils) {
        super(apiService, clock, catalogService);
        this.addonUtils = addonUtils;
        this.repairDao = dao;
    }

    @Override
    public SubscriptionData createSubscription(final SubscriptionBuilder builder,
                                               final List<EntitlementEvent> events) {
        final SubscriptionData subscription = new SubscriptionDataRepair(builder, events, getApiService(), repairDao, getClock(), addonUtils, getCatalogService());
        subscription.rebuildTransitions(events, getCatalogService().getFullCatalog());
        return subscription;
    }
}
