/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.account;

import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.account.dao.AccountDao;
import org.killbill.billing.account.glue.TestAccountModuleNoDB;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.tag.api.user.TagEventBuilder;
import org.killbill.billing.util.tag.dao.TagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.clock.Clock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class AccountTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountDao accountDao;
    @Inject
    protected ImmutableAccountInternalApi immutableAccountInternalApi;
    @Inject
    protected AccountUserApi accountUserApi;
    @Inject
    protected AuditDao auditDao;
    @Inject
    protected CacheControllerDispatcher controllerDispatcher;
    @Inject
    protected Clock clock;
    @Inject
    protected CustomFieldDao customFieldDao;
    @Inject
    protected PersistentBus bus;
    @Inject
    protected TagDao tagDao;
    @Inject
    protected TagDefinitionDao tagDefinitionDao;
    @Inject
    protected TagEventBuilder tagEventBuilder;
    @Inject
    protected NonEntityDao nonEntityDao;

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestAccountModuleNoDB(configSource));
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
    public void afterMethod() throws Exception {
        bus.stop();
    }
}
