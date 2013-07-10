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

package com.ning.billing.account;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.glue.TestAccountModuleNoDB;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.clock.Clock;
import com.ning.billing.util.customfield.dao.CustomFieldDao;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.glue.RealImplementation;
import com.ning.billing.util.tag.api.user.TagEventBuilder;
import com.ning.billing.util.tag.dao.TagDao;
import com.ning.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public abstract class AccountTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @Inject
    protected AccountDao accountDao;
    @Inject
    @RealImplementation
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
        final Injector injector = Guice.createInjector(new TestAccountModuleNoDB(configSource));
        injector.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        bus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        bus.stop();
    }
}
