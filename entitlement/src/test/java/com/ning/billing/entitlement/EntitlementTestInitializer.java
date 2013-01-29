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

package com.ning.billing.entitlement;

import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.svcsapi.bus.BusService;

public interface EntitlementTestInitializer {

    public Catalog initCatalog(final CatalogService catalogService) throws Exception;

    public AccountData initAccountData();

    public SubscriptionBundle initBundle(final EntitlementUserApi entitlementApi, final CallContext callContext) throws Exception;

    public void startTestFamework(final TestApiListener testListener,
                                  final TestListenerStatus testListenerStatus,
                                  final ClockMock clock,
                                  final BusService busService,
                                  final EntitlementService entitlementService) throws Exception;

    public void stopTestFramework(final TestApiListener testListener,
                                  final BusService busService,
                                  final EntitlementService entitlementService) throws Exception;
}
