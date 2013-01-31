/*
 * Copyright 2010-2012 Ning, Inc.
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

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.migration.EntitlementMigrationApi;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.TestUtil;
import com.ning.billing.entitlement.engine.dao.EntitlementDao;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.config.EntitlementConfig;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class EntitlementTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    protected static final Logger log = LoggerFactory.getLogger(EntitlementTestSuiteWithEmbeddedDB.class);

    @Inject
    protected EntitlementService entitlementService;
    @Inject
    protected EntitlementUserApi entitlementApi;
    @Inject
    protected EntitlementInternalApi entitlementInternalApi;
    @Inject
    protected EntitlementTransferApi transferApi;

    @Inject
    protected EntitlementMigrationApi migrationApi;
    @Inject
    protected EntitlementTimelineApi repairApi;

    @Inject
    protected CatalogService catalogService;
    @Inject
    protected EntitlementConfig config;
    @Inject
    protected EntitlementDao dao;
    @Inject
    protected ClockMock clock;
    @Inject
    protected BusService busService;

    @Inject
    protected TestUtil testUtil;
    @Inject
    protected TestApiListener testListener;
    @Inject
    protected TestListenerStatus testListenerStatus;

    @Inject
    protected EntitlementTestInitializer entitlementTestInitializer;

    protected Catalog catalog;
    protected AccountData accountData;
    protected SubscriptionBundle bundle;

    @BeforeClass(groups = "slow")
    public void setup() throws Exception {
        DefaultEntitlementTestInitializer.loadSystemPropertiesFromClasspath("/entitlement.properties");
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new MockEngineModuleSql());
        g.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {
        entitlementTestInitializer.startTestFamework(testListener, testListenerStatus, clock, busService, entitlementService);

        this.catalog = entitlementTestInitializer.initCatalog(catalogService);
        this.accountData = entitlementTestInitializer.initAccountData();
        this.bundle = entitlementTestInitializer.initBundle(entitlementApi, callContext);
    }

    @AfterMethod(groups = "slow")
    public void cleanupTest() throws Exception {
        entitlementTestInitializer.stopTestFramework(testListener, busService, entitlementService);
    }

    protected void assertListenerStatus() {
        ((EntitlementTestListenerStatus) testListenerStatus).assertListenerStatus();
    }
}
