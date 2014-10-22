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

import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Product;
import org.killbill.billing.subscription.alignment.PlanAligner;
import org.killbill.billing.subscription.api.SubscriptionBaseApiService;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseApiService;
import org.killbill.billing.subscription.engine.addon.AddonUtils;
import org.killbill.billing.subscription.engine.dao.SubscriptionDao;
import org.killbill.billing.subscription.glue.DefaultSubscriptionModule;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.clock.Clock;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class RepairSubscriptionApiService extends DefaultSubscriptionBaseApiService implements SubscriptionBaseApiService {

    @Inject
    public RepairSubscriptionApiService(final Clock clock,
                                        @Named(DefaultSubscriptionModule.REPAIR_NAMED) final SubscriptionDao dao,
                                        final CatalogService catalogService,
                                        final PlanAligner planAligner,
                                        final AddonUtils addonUtils,
                                        final InternalCallContextFactory internalCallContextFactory) {
        super(clock, dao, catalogService, planAligner, addonUtils, internalCallContextFactory);
    }

    // Nothing to do for repair as we pass all the repair events in the stream
    @Override
    public int cancelAddOnsIfRequired(final Product baseProduct, final UUID bundleId, final DateTime effectiveDate, final CallContext context) {
        return 0;
    }
}
