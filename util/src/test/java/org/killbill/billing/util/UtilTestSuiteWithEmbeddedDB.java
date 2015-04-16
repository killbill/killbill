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

package org.killbill.billing.util;

import javax.inject.Inject;

import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.security.shiro.dao.UserDao;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.api.TestApiListener;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.customfield.api.DefaultCustomFieldUserApi;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.export.dao.DatabaseExportDao;
import org.killbill.billing.util.glue.TestUtilModuleWithEmbeddedDB;
import org.killbill.billing.util.tag.dao.DefaultTagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public abstract class UtilTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    private static final Logger log = LoggerFactory.getLogger(UtilTestSuiteWithEmbeddedDB.class);

    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected DefaultCustomFieldUserApi customFieldUserApi;
    @Inject
    protected CustomFieldDao customFieldDao;
    @Inject
    protected DatabaseExportDao dao;
    @Inject
    protected NotificationQueueService queueService;
    @Inject
    protected TagDefinitionDao tagDefinitionDao;
    @Inject
    protected DefaultTagDao tagDao;
    @Inject
    protected AuditDao auditDao;
    @Inject
    protected GlobalLocker locker;
    @Inject
    protected IDBI idbi;
    @Inject
    protected TestApiListener eventsListener;
    @Inject
    protected SecurityApi securityApi;


    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleWithEmbeddedDB(configSource));
        g.injectMembers(this);
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        eventsListener.reset();

        eventBus.start();
        eventBus.register(eventsListener);

        controlCacheDispatcher.clearAll();

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        // Make sure we finish in a clean state
        assertListenerStatus();

        eventBus.unregister(eventsListener);
        eventBus.stop();
    }

    protected void assertListenerStatus() {
        eventsListener.assertListenerStatus();
    }
}
