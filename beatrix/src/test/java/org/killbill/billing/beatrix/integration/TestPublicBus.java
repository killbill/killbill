/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.joda.time.DateTime;
import org.killbill.billing.DBTestingHelper;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.extbus.DefaultBusExternalEvent;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.notification.plugin.api.ExtBusEvent;
import org.killbill.billing.notification.plugin.api.ExtBusEventType;
import org.killbill.billing.notification.plugin.api.SubscriptionMetadata;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.platform.api.KillbillConfigSource;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantData;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.killbill.billing.util.nodes.NodeCommand;
import org.killbill.billing.util.nodes.NodeCommandMetadata;
import org.killbill.billing.util.nodes.NodeCommandProperty;
import org.killbill.billing.util.nodes.PluginNodeCommandMetadata;
import org.killbill.billing.util.nodes.SystemNodeCommandType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.Subscribe;

import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertNotNull;

public class TestPublicBus extends TestIntegrationBase {

    private PublicListener publicListener;

    private AtomicInteger externalBusCount;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected KillbillConfigSource getConfigSource() {
        ImmutableMap additionalProperties = new ImmutableMap.Builder()
                .put("org.killbill.billing.util.broadcast.rate", "500ms")
                .build();
        return getConfigSource("/beatrix.properties", additionalProperties);
    }


    public class PublicListener {

        @Subscribe
        public void handleExternalEvents(final ExtBusEvent event) {
            log.info("GOT EXT EVENT " + event);

            if (event.getEventType() == ExtBusEventType.SUBSCRIPTION_CREATION ||
                event.getEventType() == ExtBusEventType.SUBSCRIPTION_CANCEL ||
                event.getEventType() == ExtBusEventType.SUBSCRIPTION_PHASE ||
                event.getEventType() == ExtBusEventType.SUBSCRIPTION_CHANGE ||
                event.getEventType() == ExtBusEventType.SUBSCRIPTION_UNCANCEL ||
                event.getEventType() == ExtBusEventType.SUBSCRIPTION_BCD_CHANGE) {
                try {
                    final SubscriptionMetadata obj = (SubscriptionMetadata) mapper.readValue(event.getMetaData(), SubscriptionMetadata.class);
                    Assert.assertNotNull(obj.getBundleExternalKey());
                    Assert.assertNotNull(obj.getActionType());
                } catch (JsonParseException e) {
                    Assert.fail("Could not deserialize metada section", e);
                } catch (JsonMappingException e) {
                    Assert.fail("Could not deserialize metada section", e);
                } catch (IOException e) {
                    Assert.fail("Could not deserialize metada section", e);
                }
            }

            externalBusCount.incrementAndGet();

        }
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {

        /*
        We copy the initialization instead of invoking the super method so we can add the registration
        of the publicBus event;
        TODO modify sequence to allow optional registration of publicListener
         */

        try {
            DBTestingHelper.get().getInstance().cleanupAllTables();
        } catch (final Exception ignored) {
        }

        log.debug("RESET TEST FRAMEWORK");

        controllerDispatcher.clearAll();

        overdueConfigCache.loadDefaultOverdueConfig((OverdueConfig) null);

        clock.resetDeltaFromReality();
        busHandler.reset();

        // Start services
        publicListener = new PublicListener();

        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        externalBus.register(publicListener);

        lifecycle.fireStartupSequencePostEventRegistration();

        paymentPlugin.clear();

        this.externalBusCount = new AtomicInteger(0);

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        externalBus.unregister(publicListener);
        super.afterMethod();
    }

    @Test(groups = "slow")
    public void testSimple() throws Exception {

        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        final int billingDay = 2;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithNonOsgiPaymentMethod(getAccountData(billingDay));
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE, NextEvent.BLOCK NextEvent.INVOICE
        //
        final DefaultEntitlement bpEntitlement = createBaseEntitlementAndCheckForCompletion(account.getId(), "externalKey", "Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, NextEvent.CREATE, NextEvent.BLOCK, NextEvent.INVOICE);
        assertNotNull(bpEntitlement);

        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // expecting ACCOUNT_CREATE, ACCOUNT_CHANGE, SUBSCRIPTION_CREATION (2), ENTITLEMENT_CREATE INVOICE_CREATION
                return externalBusCount.get() == 6;
            }
        });

        addDaysAndCheckForCompletion(31, NextEvent.PHASE, NextEvent.INVOICE, NextEvent.PAYMENT, NextEvent.INVOICE_PAYMENT);


        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // 5 + SUBSCRIPTION_TRANSITION, INVOICE, PAYMENT, INVOICE_PAYMENT
                return externalBusCount.get() == 10;
            }
        });

    }

    @Test(groups = "slow")
    public void testTenantKVChange() throws Exception {

        final TenantData tenantData = new DefaultTenant(null, clock.getUTCNow(), clock.getUTCNow(), "MY_TENANT", "key", "s3Cr3T");
        final CallContext contextWithNoTenant = new DefaultCallContext(null, "loulou", CallOrigin.EXTERNAL, UserType.ADMIN, "no reason", "hum", UUID.randomUUID(), clock);
        final Tenant tenant = tenantUserApi.createTenant(tenantData, contextWithNoTenant);

        final CallContext contextWithTenant = new DefaultCallContext(tenant.getId(), "loulou", CallOrigin.EXTERNAL, UserType.ADMIN, "no reason", "hum", UUID.randomUUID(), clock);
        final String tenantKey = TenantKey.PLUGIN_CONFIG_ + "FOO";
        tenantUserApi.addTenantKeyValue(tenantKey, "FOO", contextWithTenant);

        await().atMost(10, SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // expecting  TENANT_CONFIG_CHANGE
                return externalBusCount.get() == 1;
            }
        });
    }
}
