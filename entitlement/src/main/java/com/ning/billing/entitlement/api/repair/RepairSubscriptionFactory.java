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
package com.ning.billing.entitlement.api.repair;

import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.SubscriptionApiService;
import com.ning.billing.entitlement.api.SubscriptionFactory;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionApiService;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.entitlement.api.user.DefaultSubscriptionFactory.SubscriptionBuilder;
import com.ning.billing.entitlement.engine.addon.AddonUtils;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.glue.EntitlementModule;
import com.ning.billing.util.clock.Clock;

public class RepairSubscriptionFactory extends DefaultSubscriptionFactory implements SubscriptionFactory {

    private final AddonUtils addonUtils;
    private final EntitlementDao repairDao;
    
    @Inject
    public RepairSubscriptionFactory(@Named(EntitlementModule.REPAIR_NAMED) SubscriptionApiService apiService,
            @Named(EntitlementModule.REPAIR_NAMED) EntitlementDao dao,
            Clock clock, CatalogService catalogService, AddonUtils addonUtils) {
        super(apiService, clock, catalogService);
        this.addonUtils = addonUtils;
        this.repairDao = dao;
    }
     
    @Override
    public SubscriptionData createSubscription(SubscriptionBuilder builder,
            List<EntitlementEvent> events) {
        SubscriptionData subscription = new SubscriptionDataRepair(builder, events, apiService, repairDao, clock, addonUtils, catalogService);
        subscription.rebuildTransitions(events, catalogService.getFullCatalog());
        return subscription;
    }
}
