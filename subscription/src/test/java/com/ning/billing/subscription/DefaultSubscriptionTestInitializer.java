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

package com.ning.billing.subscription;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.account.api.AccountData;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.DefaultCatalogService;
import com.ning.billing.catalog.api.Catalog;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.clock.ClockMock;
import com.ning.billing.subscription.api.SubscriptionService;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionService;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.subscription.api.user.SubscriptionUserApi;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.svcsapi.bus.BusService;

import static org.testng.Assert.assertNotNull;

public class DefaultSubscriptionTestInitializer implements SubscriptionTestInitializer {


    protected static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionTestInitializer.class);

    public DefaultSubscriptionTestInitializer() {

    }

    public Catalog initCatalog(final CatalogService catalogService) throws Exception {

        ((DefaultCatalogService) catalogService).loadCatalog();
        final Catalog catalog = catalogService.getFullCatalog();
        assertNotNull(catalog);
        return catalog;
    }

    public AccountData initAccountData() {
        final AccountData accountData = new MockAccountBuilder().name(UUID.randomUUID().toString())
                                                                .firstNameLength(6)
                                                                .email(UUID.randomUUID().toString())
                                                                .phone(UUID.randomUUID().toString())
                                                                .migrated(false)
                                                                .isNotifiedForInvoices(false)
                                                                .externalKey(UUID.randomUUID().toString())
                                                                .billingCycleDayLocal(1)
                                                                .currency(Currency.USD)
                                                                .paymentMethodId(UUID.randomUUID())
                                                                .timeZone(DateTimeZone.forID("Europe/Paris"))
                                                                .build();

        assertNotNull(accountData);
        return accountData;
    }

    public SubscriptionBundle initBundle(final SubscriptionUserApi subscriptionApi, final CallContext callContext) throws Exception {
        final UUID accountId = UUID.randomUUID();
        final SubscriptionBundle bundle = subscriptionApi.createBundleForAccount(accountId, "myDefaultBundle", callContext);
        assertNotNull(bundle);
        return bundle;
    }


    public void startTestFamework(final TestApiListener testListener,
                                  final TestListenerStatus testListenerStatus,
                                  final ClockMock clock,
                                  final BusService busService,
                                  final SubscriptionService subscriptionService) throws Exception {
        log.warn("STARTING TEST FRAMEWORK");

        resetTestListener(testListener, testListenerStatus);

        resetClockToStartOfTest(clock);

        startBusAndRegisterListener(busService, testListener);

        restartSubscriptionService(subscriptionService);

        log.warn("STARTED TEST FRAMEWORK");
    }

    public void stopTestFramework(final TestApiListener testListener,
                                  final BusService busService,
                                  final SubscriptionService subscriptionService) throws Exception {
        log.warn("STOPPING TEST FRAMEWORK");
        stopBusAndUnregisterListener(busService, testListener);

        stopSubscriptionService(subscriptionService);

        log.warn("STOPPED TEST FRAMEWORK");
    }


    private void resetTestListener(final TestApiListener testListener, final TestListenerStatus testListenerStatus) {
        // RESET LIST OF EXPECTED EVENTS
        if (testListener != null) {
            testListener.reset();
            testListenerStatus.resetTestListenerStatus();
        }
    }

    private void resetClockToStartOfTest(final ClockMock clock) {
        clock.resetDeltaFromReality();

        // Date at which all tests start-- we create the date object here after the system properties which set the JVM in UTC have been set.
        final DateTime testStartDate = new DateTime(2012, 5, 7, 0, 3, 42, 0);
        clock.setDeltaFromReality(testStartDate.getMillis() - clock.getUTCNow().getMillis());
    }

    private void startBusAndRegisterListener(final BusService busService, final TestApiListener testListener) throws Exception {
        busService.getBus().start();
        busService.getBus().register(testListener);
    }

    private void restartSubscriptionService(final SubscriptionService subscriptionService) {
        // START NOTIFICATION QUEUE FOR SUBSCRIPTION
        ((DefaultSubscriptionService) subscriptionService).initialize();
        ((DefaultSubscriptionService) subscriptionService).start();
    }

    private void stopBusAndUnregisterListener(final BusService busService, final TestApiListener testListener) throws Exception {
        busService.getBus().unregister(testListener);
        busService.getBus().stop();
    }

    private void stopSubscriptionService(final SubscriptionService subscriptionService) throws Exception {
        ((DefaultSubscriptionService) subscriptionService).stop();
    }
}
