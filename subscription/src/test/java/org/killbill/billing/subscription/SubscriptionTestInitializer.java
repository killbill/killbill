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

package org.killbill.billing.subscription;

import java.util.UUID;

import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;

public interface SubscriptionTestInitializer {

    public Catalog initCatalog(final CatalogService catalogService, final InternalTenantContext context) throws Exception;

    public AccountData initAccountData(Clock clock);

    public SubscriptionBaseBundle initBundle(final UUID accountId, final SubscriptionBaseInternalApi subscriptionApi, final Clock clock, final InternalCallContext callContext) throws Exception;

    public void startTestFramework(final TestApiListener testListener,
                                   final ClockMock clock,
                                   final BusService busService,
                                   final SubscriptionBaseService subscriptionBaseService) throws Exception;

    public void stopTestFramework(final TestApiListener testListener,
                                  final BusService busService,
                                  final SubscriptionBaseService subscriptionBaseService) throws Exception;
}
