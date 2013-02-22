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

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.IDBI;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.beatrix.bus.api.ExtBusEvent;
import com.ning.billing.beatrix.bus.api.ExtBusEventType;
import com.ning.billing.beatrix.bus.api.ExternalBus;
import com.ning.billing.osgi.api.OSGIKillbill;
import com.ning.billing.osgi.api.OSGIPluginProperties;
import com.ning.billing.osgi.bundles.test.dao.TestDao;
import com.ning.billing.payment.plugin.api.PaymentPluginApi;
import com.ning.billing.util.callcontext.TenantContext;

import com.google.common.eventbus.Subscribe;

/**
 * Test class used by Beatrix OSGI test to verify that:
 *  - "test" bundle is started
 *  - test bundle is able to make API call
 *  - test bundle is able to register a fake PaymentApi service
 *  - test bundle can use the DataSource from Killbill and write on disk
 */
public class TestActivator implements BundleActivator {

    private OSGIKillbill osgiKillbill;
    private volatile ServiceReference<OSGIKillbill> osgiKillbillReference;

    private final Logger logger = new Logger();

    private volatile boolean isRunning;
    private volatile ServiceRegistration paymentInfoPluginRegistration;

    private TestDao testDao;

    @Override
    public void start(final BundleContext context) {

        final String bundleName = context.getBundle().getSymbolicName();
        System.out.println("TestActivator starting bundle = " + bundleName);

        fetchOSGIKIllbill(context);
        logger.start(context);

        final IDBI dbi = new DBI(osgiKillbill.getDataSource());
        testDao = new TestDao(dbi);
        registerPaymentApi(context, testDao);

        registerForKillbillEvents(context);

        testDao.createTable();

        testDao.insertStarted();

        this.isRunning = true;
    }

    @Override
    public void stop(final BundleContext context) {
        this.isRunning = false;
        releaseOSGIKIllbill(context);
        this.osgiKillbill = null;
        unregisterPlaymentPluginApi(context);
        logger.close();
        System.out.println("Good bye world from TestActivator!");
    }

    @Subscribe
    public void handleKillbillEvent(final ExtBusEvent killbillEvent) {

        logger.log(LogService.LOG_INFO, "Received external event " + killbillEvent.toString());

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
            Account account = osgiKillbill.getAccountUserApi().getAccountById(killbillEvent.getAccountId(), tenantContext);
            testDao.insertAccountExternalKey(account.getExternalKey());

        } catch (AccountApiException e) {
            logger.log(LogService.LOG_ERROR, e.getMessage());
        }
    }


    private void registerForKillbillEvents(final BundleContext context) {
        try {
            final ExternalBus externalBus = osgiKillbill.getExternalBus();
            externalBus.register(this);
        } catch (Exception e) {
            System.err.println("Error in TestActivator: " + e.getLocalizedMessage());
        } finally {
        }
    }

    private void fetchOSGIKIllbill(final BundleContext context) {
        this.osgiKillbillReference = (ServiceReference<OSGIKillbill>) context.getServiceReference(OSGIKillbill.class.getName());
        try {
            this.osgiKillbill = context.getService(osgiKillbillReference);
        } catch (Exception e) {
            System.err.println("Error in TestActivator: " + e.getLocalizedMessage());
        }
    }

    private void releaseOSGIKIllbill(final BundleContext context) {
        if (osgiKillbillReference != null) {
            context.ungetService(osgiKillbillReference);
        }
    }

    private void registerPaymentApi(final BundleContext context, final TestDao dao) {

        final Dictionary props = new Hashtable();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, "test");

        this.paymentInfoPluginRegistration = context.registerService(PaymentPluginApi.class.getName(),
                                                                     new TestPaymentPluginApi("test", dao), props);
    }

    private void unregisterPlaymentPluginApi(final BundleContext context) {
        if (paymentInfoPluginRegistration != null) {
            paymentInfoPluginRegistration.unregister();
            paymentInfoPluginRegistration = null;
        }
    }
}
