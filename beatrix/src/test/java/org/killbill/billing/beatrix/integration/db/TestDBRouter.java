/*
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

package org.killbill.billing.beatrix.integration.db;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.callcontext.DefaultTenantContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.osgi.api.ROTenantContext;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestDBRouter extends TestIntegrationBase {

    @Inject
    private TestDBRouterAPI testDBRouterAPI;

    private PublicListener publicListener;
    private AtomicInteger externalBusCount;

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        this.externalBusCount = new AtomicInteger(0);
        testDBRouterAPI.reset();
    }

    @Override
    protected void registerHandlers() throws EventBusException {
        super.registerHandlers();

        publicListener = new PublicListener();
        externalBus.register(publicListener);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        externalBus.unregister(publicListener);
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void testWithBusEvents() throws Exception {
        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        clock.setTime(initialDate);

        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(2));
        assertNotNull(account);

        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        await().atMost(10, SECONDS)
               .until(new Callable<Boolean>() {
                   @Override
                   public Boolean call() throws Exception {
                       // Expecting ACCOUNT_CREATE, ACCOUNT_CHANGE, SUBSCRIPTION_CREATION (2), ENTITLEMENT_CREATE INVOICE_CREATION
                       return externalBusCount.get() == 6;
                   }
               });
    }

    private void assertNbCalls(final int expectedNbRWCalls, final int expectedNbROCalls) {
        assertEquals(testDBRouterAPI.getNbRWCalls(), expectedNbRWCalls);
        assertEquals(testDBRouterAPI.getNbRoCalls(), expectedNbROCalls);
    }

    public class PublicListener {

        @Subscribe
        public void handleExternalEvents(final ExtBusEvent event) {
            testDBRouterAPI.reset();

            final TenantContext tenantContext = new DefaultTenantContext(callContext.getAccountId(), callContext.getTenantId());
            // Only RO tenant will trigger use of RO DBI (initiated by plugins)
            final ROTenantContext roTenantContext = new ROTenantContext(tenantContext);

            // RO calls goes to RW DB by default
            testDBRouterAPI.doROCall(tenantContext);
            assertNbCalls(1, 0);

            testDBRouterAPI.doROCall(callContext);
            assertNbCalls(2, 0);

            // Even if the thread is dirty (previous RW calls), the plugin asked for RO DBI
            testDBRouterAPI.doROCall(roTenantContext);
            assertNbCalls(2, 1);

            // Make sure subsequent calls go back to the RW DB
            testDBRouterAPI.doROCall(tenantContext);
            assertNbCalls(3, 1);

            testDBRouterAPI.doRWCall(callContext);
            assertNbCalls(4, 1);

            testDBRouterAPI.doROCall(roTenantContext);
            assertNbCalls(4, 2);

            testDBRouterAPI.doROCall(callContext);
            assertNbCalls(5, 2);

            testDBRouterAPI.doROCall(tenantContext);
            assertNbCalls(6, 2);

            testDBRouterAPI.doChainedROCall(tenantContext);
            assertNbCalls(7, 2);

            testDBRouterAPI.doChainedRWCall(callContext);
            assertNbCalls(8, 2);

            // Increment only if there are no errors
            externalBusCount.incrementAndGet();
        }
    }
}
