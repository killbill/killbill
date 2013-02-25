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

package com.ning.billing.osgi.bundles.test;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.UUID;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.osgi.api.OSGIPluginProperties;
import com.ning.billing.osgi.bundles.test.dao.TestDao;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.util.callcontext.TenantContext;
import com.ning.killbill.osgi.libs.killbill.KillbillActivatorBase;
import com.ning.killbill.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIKillbillEventHandler;

/**
 * Test class used by Beatrix OSGI test to verify that:
 * - "test" bundle is started
 * - test bundle is able to make API call
 * - test bundle is able to register a fake PaymentApi service
 * - test bundle can use the DataSource from Killbill and write on disk
 */
public class TestActivator extends KillbillActivatorBase implements OSGIKillbillEventHandler {

    private TestDao testDao;

    @Override
    public void start(final BundleContext context) {

        final String bundleName = context.getBundle().getSymbolicName();
        System.out.println("TestActivator starting bundle = " + bundleName);

        super.start(context);


        final IDBI dbi = new DBI(dataSource.getDataSource());
        testDao = new TestDao(dbi);
        testDao.createTable();
        testDao.insertStarted();
        registerPaymentApi(context, testDao);
    }

    @Override
    public void stop(final BundleContext context) {
        super.stop(context);
        System.out.println("Good bye world from TestActivator!");
    }

    @Override
    public OSGIKillbillEventHandler getOSGIKillbillEventHandler() {
        return this;
    }

    private void registerPaymentApi(final BundleContext context, final TestDao dao) {

        final Dictionary props = new Hashtable();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, "test");
        registrar.registerService(context, PaymentPluginApi.class, new TestPaymentPluginApi("test", dao), props);
    }

    @Override
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {

        logService.log(LogService.LOG_INFO, "Received external event " + killbillEvent.toString());

        // Only looking at account creation
        if (killbillEvent.getEventType() != ExtBusEventType.ACCOUNT_CREATION) {
            return;
        }

        final TenantContext tenantContext = new TenantContext() {
            @Override
            public UUID getTenantId() {
                return null;
            }
        };

        try {
            Account account = api.getAccountUserApi().getAccountById(killbillEvent.getAccountId(), tenantContext);
            testDao.insertAccountExternalKey(account.getExternalKey());

        } catch (AccountApiException e) {
            logService.log(LogService.LOG_ERROR, e.getMessage());
        }
    }

}
