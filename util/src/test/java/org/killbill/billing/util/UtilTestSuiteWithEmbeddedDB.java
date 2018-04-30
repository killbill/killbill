/*
 * Copyright 2010-2014 Ning, Inc.
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

package org.killbill.billing.util;

import javax.inject.Inject;

import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.broadcast.dao.BroadcastDao;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.SecurityConfig;
import org.killbill.billing.util.customfield.api.DefaultCustomFieldUserApi;
import org.killbill.billing.util.customfield.dao.CustomFieldDao;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.export.dao.DatabaseExportDao;
import org.killbill.billing.util.glue.TestUtilModuleWithEmbeddedDB;
import org.killbill.billing.util.nodes.dao.NodeInfoDao;
import org.killbill.billing.util.tag.api.DefaultTagUserApi;
import org.killbill.billing.util.tag.dao.DefaultTagDao;
import org.killbill.billing.util.tag.dao.TagDefinitionDao;
import org.killbill.bus.api.PersistentBus;
import org.killbill.commons.embeddeddb.EmbeddedDB.DBEngine;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.memory.MemoryGlobalLocker;
import org.killbill.commons.locker.mysql.MySqlGlobalLocker;
import org.killbill.commons.locker.postgresql.PostgreSQLGlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

public abstract class UtilTestSuiteWithEmbeddedDB extends GuicyKillbillTestSuiteWithEmbeddedDB {

    private static final Logger log = LoggerFactory.getLogger(UtilTestSuiteWithEmbeddedDB.class);

    @Inject
    protected PersistentBus eventBus;
    @Inject
    protected NonEntityDao nonEntityDao;
    @Inject
    protected InternalCallContextFactory internalCallContextFactory;
    @Inject
    protected ImmutableAccountInternalApi immutableAccountInternalApi;
    @Inject
    protected DefaultTagUserApi tagUserApi;
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
    protected TestApiListener eventsListener;
    @Inject
    protected SecurityApi securityApi;
    @Inject
    protected SecurityConfig securityConfig;
    @Inject
    protected NodeInfoDao nodeInfoDao;
    @Inject
    protected BroadcastDao broadcastDao;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleWithEmbeddedDB(configSource));
        g.injectMembers(this);

        if (DBEngine.MYSQL.equals(helper.getDBEngine())) {
            Assert.assertTrue(locker instanceof MySqlGlobalLocker);
        } else if (DBEngine.POSTGRESQL.equals(helper.getDBEngine())) {
            Assert.assertTrue(locker instanceof PostgreSQLGlobalLocker);
        } else {
            Assert.assertTrue(locker instanceof MemoryGlobalLocker);
        }
        Assert.assertTrue(locker.isFree("a", "b"));
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        eventsListener.reset();

        eventBus.start();
        eventBus.register(eventsListener);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        eventBus.unregister(eventsListener);
        eventBus.stop();
    }

    @Override
    protected void assertListenerStatus() {
        eventsListener.assertListenerStatus();
    }
}
