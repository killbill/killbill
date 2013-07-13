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

package com.ning.billing.subscription.api.timeline;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.clock.Clock;
import com.ning.billing.subscription.alignment.PlanAligner;
import com.ning.billing.subscription.api.SubscriptionApiService;
import com.ning.billing.subscription.api.user.DefaultSubscriptionApiService;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.subscription.engine.addon.AddonUtils;
import com.ning.billing.subscription.engine.dao.SubscriptionDao;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class RepairSubscriptionApiService extends DefaultSubscriptionApiService implements SubscriptionApiService {

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
    public int cancelAddOnsIfRequired(final SubscriptionData baseSubscription, final DateTime effectiveDate, final InternalCallContext context) {
        return 0;
    }
}
