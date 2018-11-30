/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.subscription;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.DefaultCatalogService;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBaseBundle;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.subscription.engine.core.DefaultSubscriptionBaseService;
import org.killbill.billing.util.UUIDs;
import org.killbill.clock.Clock;
import org.killbill.clock.ClockMock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.testng.Assert.assertNotNull;

public class DefaultSubscriptionTestInitializer implements SubscriptionTestInitializer {

    public static final String DEFAULT_BUNDLE_KEY = "myDefaultBundle";

    protected static final Logger log = LoggerFactory.getLogger(DefaultSubscriptionTestInitializer.class);

    public DefaultSubscriptionTestInitializer() {
    }

    public Catalog initCatalog(final CatalogService catalogService, final InternalTenantContext context) throws Exception {
        ((DefaultCatalogService) catalogService).loadCatalog();
        final Catalog catalog = catalogService.getFullCatalog(true, true, context);
        assertNotNull(catalog);
        return catalog;
    }

    public AccountData initAccountData(final Clock clock) {
        final AccountData accountData = new MockAccountBuilder().name(UUIDs.randomUUID().toString().substring(1, 8))
                                                                .firstNameLength(6)
                                                                .email(UUIDs.randomUUID().toString().substring(1, 8))
                                                                .phone(UUIDs.randomUUID().toString().substring(1, 8))
                                                                .migrated(false)
                                                                .externalKey(UUIDs.randomUUID().toString())
                                                                .billingCycleDayLocal(1)
                                                                .currency(Currency.USD)
                                                                .paymentMethodId(UUIDs.randomUUID())
                                                                .referenceTime(clock.getUTCNow())
                                                                .timeZone(DateTimeZone.forID("Europe/Paris"))
                                                                .build();

        assertNotNull(accountData);
        return accountData;
    }

    public SubscriptionBaseBundle initBundle(final UUID accountId, final SubscriptionBaseInternalApi subscriptionApi, final Clock clock, final InternalCallContext callContext) throws Exception {
        final DateTime utcNow = clock.getUTCNow();
        return new DefaultSubscriptionBaseBundle(DEFAULT_BUNDLE_KEY, accountId, utcNow, utcNow, utcNow, utcNow);
    }

    public void startTestFramework(final TestApiListener testListener,
                                   final ClockMock clock,
                                   final BusService busService,
                                   final SubscriptionBaseService subscriptionBaseService) throws Exception {
        log.debug("STARTING TEST FRAMEWORK");

        resetTestListener(testListener);

        resetClockToStartOfTest(clock);

        startBusAndRegisterListener(busService, testListener);

        restartSubscriptionService(subscriptionBaseService);

        log.debug("STARTED TEST FRAMEWORK");
    }

    public void stopTestFramework(final TestApiListener testListener,
                                  final BusService busService,
                                  final SubscriptionBaseService subscriptionBaseService) throws Exception {
        log.debug("STOPPING TEST FRAMEWORK");
        stopBusAndUnregisterListener(busService, testListener);

        stopSubscriptionService(subscriptionBaseService);

        log.debug("STOPPED TEST FRAMEWORK");
    }

    private void resetTestListener(final TestApiListener testListener) {
        // RESET LIST OF EXPECTED EVENTS
        if (testListener != null) {
            testListener.reset();
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
