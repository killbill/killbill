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

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.junction.glue.TestJunctionModuleNoDB;
import com.ning.billing.junction.plumbing.billing.BillCycleDayCalculator;
import com.ning.billing.junction.plumbing.billing.BlockingCalculator;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.subscription.SubscriptionInternalApi;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.dao.TagDao;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class JunctionTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BillCycleDayCalculator billCycleDayCalculator;
    @Inject
    protected BillingInternalApi billingInternalApi;
    @Inject
    protected BlockingCalculator blockingCalculator;
    @Inject
    protected CatalogService catalogService;
    @Inject
    protected SubscriptionInternalApi subscriptionInternalApi;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagInternalApi tagInternalApi;
    @Inject
    protected BlockingStateDao blockingStateDao;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        final Injector injector = Guice.createInjector(new TestJunctionModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        bus.stop();
    }
}
