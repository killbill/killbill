/*
 * Copyright 2010-2011 Ning, Inc.
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
package com.ning.billing.beatrix.integration;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.UUID;

import org.joda.time.DateTime;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.beatrix.bus.BusEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;

import com.google.common.eventbus.Subscribe;

@Guice(modules = {BeatrixModule.class})
public class TestPublicBus extends TestIntegrationBase {

    private PublicListener publicListener;


    public class PublicListener {
        @Subscribe
        public void handleExternalEvents(final BusEvent event) {
            log.info("GOT EXT EVENT " + event.toString());
        }
    }

    @Override
    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {

        publicListener = new PublicListener();

        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");

        clock.resetDeltaFromReality();
        resetTestListenerStatus();
        busHandler.reset();

        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        externalBus.register(publicListener);
        lifecycle.fireStartupSequencePostEventRegistration();
    }


    @Test(groups= "{slow}")
    public void testSimple() throws Exception {

        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0, testTimeZone);
        final int billingDay = 2;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever2", callContext);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, callContext));

        assertNotNull(subscription);
        assertTrue(busHandler.isCompleted(DELAY));
    }




}
