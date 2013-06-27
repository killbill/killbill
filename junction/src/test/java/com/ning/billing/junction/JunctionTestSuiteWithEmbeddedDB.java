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

package com.ning.billing.junction;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.bus.PersistentBus;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.junction.block.BlockingChecker;
import com.ning.billing.junction.dao.BlockingStateDao;
import com.ning.billing.junction.glue.TestJunctionModuleWithEmbeddedDB;
import com.ning.billing.junction.plumbing.billing.BlockingCalculator;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.dao.TagDao;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class JunctionTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BillingInternalApi billingInternalApi;
    @Inject
    protected BlockingCalculator blockingCalculator;
    @Inject
    protected BlockingChecker blockingChecker;
    @Inject
    protected BlockingInternalApi blockingInternalApi;
    @Inject
    protected BlockingStateDao blockingStateDao;
    @Inject
    protected CatalogService catalogService;
    @Inject
    @RealImplementation
    protected EntitlementUserApi entitlementUserApi;
    @Inject
    protected EntitlementInternalApi entitlementInternalApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagInternalApi tagInternalApi;

    @BeforeClass(groups = "slow")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestJunctionModuleWithEmbeddedDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        bus.start();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() {
        bus.stop();
    }
}
