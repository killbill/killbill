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

package org.killbill.billing.junction;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.catalog.api.CatalogInternalApi;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.entitlement.dao.BlockingStateDao;
import org.killbill.billing.junction.glue.TestJunctionModuleNoDB;
import org.killbill.billing.junction.plumbing.billing.BlockingCalculator;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.tag.TagInternalApi;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.bus.api.PersistentBus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class JunctionTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountInternalApi accountInternalApi;
    @Inject
    protected BillingInternalApi billingInternalApi;
    @Inject
    protected BlockingCalculator blockingCalculator;
    @Inject
    protected CatalogService catalogService;
    @Inject
    protected CatalogInternalApi catalogInternalApi;
    @Inject
    protected SubscriptionBaseInternalApi subscriptionInternalApi;
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
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestJunctionModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        if (hasFailed()) {
            return;
        }

        bus.stop();
    }
}
