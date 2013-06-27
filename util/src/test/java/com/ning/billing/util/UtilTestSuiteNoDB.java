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

package com.ning.billing.util;

import javax.inject.Inject;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.GuicyKillbillTestSuiteNoDB;
import com.ning.billing.bus.PersistentBus;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.dao.AuditDao;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.glue.TestUtilModuleNoDB;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public class UtilTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {


    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;
    @Inject
    protected AuditDao auditDao;
    @Inject
    protected AuditUserApi auditUserApi;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleNoDB(configSource));
        g.injectMembers(this);
    }

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        eventBus.start();
    }

    @AfterMethod(groups = "fast")
    public void afterMethod() throws Exception {
        eventBus.stop();
    }

}
