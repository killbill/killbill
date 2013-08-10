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
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.subscription.api.SubscriptionBaseService;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.svcapi.subscription.SubscriptionBaseInternalApi;
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

    public SubscriptionBaseBundle initBundle(final SubscriptionBaseInternalApi subscriptionApi, final InternalCallContext callContext) throws Exception {
        final UUID accountId = UUID.randomUUID();
        final SubscriptionBaseBundle bundle = subscriptionApi.createBundleForAccount(accountId, "myDefaultBundle",  callContext);
        assertNotNull(bundle);
        return bundle;
    }


    public void startTestFamework(final TestApiListener testListener,
                                  final TestListenerStatus testListenerStatus,
                                  final ClockMock clock,
                                  final BusService busService,
                                  final SubscriptionBaseService subscriptionBaseService) throws Exception {
        log.warn("STARTING TEST FRAMEWORK");

        resetTestListener(testListener, testListenerStatus);

        resetClockToStartOfTest(clock);

        startBusAndRegisterListener(busService, testListener);

        restartSubscriptionService(subscriptionBaseService);

        log.warn("STARTED TEST FRAMEWORK");
    }

    public void stopTestFramework(final TestApiListener testListener,
                                  final BusService busService,
                                  final SubscriptionBaseService subscriptionBaseService) throws Exception {
        log.warn("STOPPING TEST FRAMEWORK");
        stopBusAndUnregisterListener(busService, testListener);

        stopSubscriptionService(subscriptionBaseService);

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

    private void restartSubscriptionService(final SubscriptionBaseService subscriptionBaseService) {
        // START NOTIFICATION QUEUE FOR SUBSCRIPTION
        ((DefaultSubscriptionBaseService) subscriptionBaseService).initialize();
        ((DefaultSubscriptionBaseService) subscriptionBaseService).start();
    }

    private void stopBusAndUnregisterListener(final BusService busService, final TestApiListener testListener) throws Exception {
        busService.getBus().unregister(testListener);
        busService.getBus().stop();
    }

    private void stopSubscriptionService(final SubscriptionBaseService subscriptionBaseService) throws Exception {
        ((DefaultSubscriptionBaseService) subscriptionBaseService).stop();
    }
}
