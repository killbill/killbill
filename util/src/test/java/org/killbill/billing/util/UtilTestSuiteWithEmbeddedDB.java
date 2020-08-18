/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.shiro.realm.Realm;
import org.killbill.billing.GuicyKillbillTestSuiteWithEmbeddedDB;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.security.api.SecurityApi;
import org.killbill.billing.util.audit.dao.AuditDao;
import org.killbill.billing.util.broadcast.dao.BroadcastDao;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
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
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Update;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.skife.jdbi.v2.util.LongMapper;
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
    @Inject
    protected Set<Realm> realms;
    @Inject
    protected CacheControllerDispatcher cacheControllerDispatcher;

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector g = Guice.createInjector(Stage.PRODUCTION, new TestUtilModuleWithEmbeddedDB(configSource, clock));
        g.injectMembers(this);

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

        eventBus.startQueue();
        eventBus.register(eventsListener);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        eventBus.unregister(eventsListener);
        eventBus.stopQueue();
    }

    @Override
    protected void assertListenerStatus() {
        eventsListener.assertListenerStatus();
    }

    protected Long generateAccountRecordId(final UUID accountId) {
        return dbi.withHandle(new HandleCallback<Long>() {
            @Override
            public Long withHandle(final Handle handle) throws Exception {
                // Note: we always create an accounts table, see MysqlTestingHelper
                return update(handle,
                              "insert into accounts (id, external_key, email, name, first_name_length, reference_time, time_zone, created_date, created_by, updated_date, updated_by, tenant_record_id) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                              accountId.toString(), accountId.toString(), "yo@t.com", "toto", 4, new Date(), "UTC", new Date(), "i", new Date(), "j", internalCallContext.getTenantRecordId());
            }

            Long update(final Handle handle, final String sql, final Object... args) {
                final Update stmt = handle.createStatement(sql);
                int position = 0;
                for (final Object arg : args) {
                    stmt.bind(position++, arg);
                }
                return stmt.executeAndReturnGeneratedKeys(new LongMapper(), "record_id").first();
            }
        });
    }
}
