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

package com.ning.billing.osgi.bundles.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.user.DefaultAccountCreationEvent;
import com.ning.billing.account.dao.AccountModelDao;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.MockPriceList;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.DefaultEffectiveSubscriptionEvent;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionTransitionData;
import com.ning.billing.entitlement.events.EntitlementEvent;
import com.ning.billing.entitlement.events.user.ApiEventType;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.user.DefaultInvoiceCreationEvent;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.FixedPriceInvoiceItem;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.mock.MockPlan;
import com.ning.billing.osgi.bundles.analytics.AnalyticsTestSuiteWithEmbeddedDB;
import com.ning.billing.osgi.bundles.analytics.MockDuration;
import com.ning.billing.osgi.bundles.analytics.MockPhase;
import com.ning.billing.osgi.bundles.analytics.MockProduct;
import com.ning.billing.payment.api.DefaultPaymentInfoEvent;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.events.AccountCreationInternalEvent;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.events.InvoiceCreationInternalEvent;
import com.ning.billing.util.events.PaymentInfoInternalEvent;

import com.google.common.collect.ImmutableList;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.fail;

public class TestAnalyticsService extends AnalyticsTestSuiteWithEmbeddedDB {

    final Product product = new MockProduct("platinum", "subscription", ProductCategory.BASE);
    final Plan plan = new MockPlan("platinum-monthly", product);
    final PlanPhase phase = new MockPhase(PhaseType.EVERGREEN, plan, MockDuration.UNLIMITED(), 25.95);

    private static final Long TOTAL_ORDERING = 11L;
    private final String ACCOUNT_KEY = UUID.randomUUID().toString();
    private final String BUNDLE_KEY = UUID.randomUUID().toString();
    private static final Currency ACCOUNT_CURRENCY = Currency.EUR;
    private static final BigDecimal INVOICE_AMOUNT = BigDecimal.valueOf(1243.11);
    private final UUID bundleId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    private EffectiveSubscriptionInternalEvent transition;

    private AccountCreationInternalEvent accountCreationNotification;
    private InvoiceCreationInternalEvent invoiceCreationNotification;
    private PaymentInfoInternalEvent paymentInfoNotification;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        Mockito.when(catalogService.getFullCatalog()).thenReturn(new MockCatalog());

        final PaymentMethod paymentMethod = Mockito.mock(PaymentMethod.class);
        final UUID paymentMethodId = UUID.randomUUID();
        Mockito.when(paymentMethod.getId()).thenReturn(paymentMethodId);
        final Account account = new MockAccountBuilder(UUID.randomUUID())
                .externalKey(ACCOUNT_KEY)
                .currency(ACCOUNT_CURRENCY)
                .paymentMethodId(paymentMethodId)
                .build();
        Mockito.when(accountInternalApi.getAccountById(Mockito.eq(account.getId()), Mockito.<InternalCallContext>any())).thenReturn(account);

        try {
            // Create events for the bus and expected results
            createSubscriptionTransitionEvent(account);
            createAccountCreationEvent(account);
            createInvoiceAndPaymentCreationEvents(account);
        } catch (Throwable t) {
            fail("Initializing accounts failed.", t);
        }
    }

    private void createSubscriptionTransitionEvent(final Account account) throws EntitlementUserApiException {
        final DateTime effectiveTransitionTime = clock.getUTCNow();
        final DateTime requestedTransitionTime = clock.getUTCNow();
        final PriceList priceList = new MockPriceList().setName("something");

        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bundleId);
        Mockito.when(bundle.getAccountId()).thenReturn(account.getId());
        Mockito.when(bundle.getExternalKey()).thenReturn(BUNDLE_KEY);
        Mockito.when(entitlementInternalApi.getBundleFromId(Mockito.eq(bundleId), Mockito.<InternalCallContext>any())).thenReturn(bundle);

        final Subscription subscription = Mockito.mock(Subscription.class);
        Mockito.when(subscription.getId()).thenReturn(subscriptionId);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);
        Mockito.when(entitlementInternalApi.getSubscriptionFromId(Mockito.eq(subscriptionId), Mockito.<InternalCallContext>any())).thenReturn(subscription);
        Mockito.when(entitlementInternalApi.getSubscriptionsForBundle(Mockito.eq(bundleId), Mockito.<InternalCallContext>any())).thenReturn(ImmutableList.<Subscription>of(subscription));

        final EffectiveSubscriptionInternalEvent event = Mockito.mock(EffectiveSubscriptionInternalEvent.class);
        Mockito.when(event.getEffectiveTransitionTime()).thenReturn(effectiveTransitionTime);
        Mockito.when(event.getRequestedTransitionTime()).thenReturn(requestedTransitionTime);
        Mockito.when(event.getTransitionType()).thenReturn(SubscriptionTransitionType.CREATE);
        Mockito.when(entitlementInternalApi.getAllTransitions(Mockito.eq(subscription), Mockito.<InternalCallContext>any())).thenReturn(ImmutableList.<EffectiveSubscriptionInternalEvent>of(event));

        // Create a subscription transition event
        transition = new DefaultEffectiveSubscriptionEvent(new SubscriptionTransitionData(
                UUID.randomUUID(),
                subscriptionId,
                bundleId,
                EntitlementEvent.EventType.API_USER,
                ApiEventType.CREATE,
                requestedTransitionTime,
                effectiveTransitionTime,
                null,
                null,
                null,
                null,
                Subscription.SubscriptionState.ACTIVE,
                plan,
                phase,
                priceList,
                TOTAL_ORDERING,
                null,
                true), null, null, 1L, 1L);
    }

    private void createAccountCreationEvent(final Account account) {
        accountCreationNotification = new DefaultAccountCreationEvent(new AccountModelDao(account.getId(), account), null, 1L, 1L);
    }

    private void createInvoiceAndPaymentCreationEvents(final Account account) {
        final DefaultInvoice invoice = new DefaultInvoice(account.getId(), clock.getUTCToday(), clock.getUTCToday(), ACCOUNT_CURRENCY);
        final FixedPriceInvoiceItem invoiceItem = new FixedPriceInvoiceItem(invoice.getId(), account.getId(), bundleId, subscriptionId, "somePlan", "somePhase", clock.getUTCToday(),
                                                                            INVOICE_AMOUNT, ACCOUNT_CURRENCY);
        invoice.addInvoiceItem(invoiceItem);
        Mockito.when(invoiceInternalApi.getInvoicesByAccountId(Mockito.eq(account.getId()), Mockito.<InternalCallContext>any())).thenReturn(ImmutableList.<Invoice>of(invoice));

        // It doesn't really matter what the events contain - the listener will go back to the db
        invoiceCreationNotification = new DefaultInvoiceCreationEvent(invoice.getId(), account.getId(),
                                                                      INVOICE_AMOUNT, ACCOUNT_CURRENCY, null, 1L, 1L);

        paymentInfoNotification = new DefaultPaymentInfoEvent(account.getId(), invoice.getId(), null, INVOICE_AMOUNT, -1,
                                                              PaymentStatus.UNKNOWN, null, clock.getUTCNow(), 1L, 1L);
    }

    @Test(groups = "slow")
    public void testRegisterForNotifications() throws Exception {
        // Make sure the service has been instantiated
        Assert.assertEquals(service.getName(), "analytics-service");

        Assert.assertNull(accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext));

        // Send events and wait for the async part...
        bus.post(accountCreationNotification, internalCallContext);
        waitALittle(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return (accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext) != null);
            }
        });

        // Test subscriptions integration - this is just to exercise the code. It's hard to test the actual subscriptions
        // as we would need to mock a bunch of APIs (see integration tests in Beatrix instead)
        bus.post(transition, internalCallContext);
        waitALittle(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return (subscriptionTransitionSqlDao.getTransitionsForAccount(ACCOUNT_KEY, internalCallContext).size() == 1);
            }
        });

        // Test invoice integration - the account creation notification has triggered a BAC update
        bus.post(invoiceCreationNotification, internalCallContext);
        waitALittle(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                // Test invoice integration - the account creation notification has triggered a BAC update
                return (accountSqlDao.getAccountByKey(ACCOUNT_KEY, internalCallContext).getTotalInvoiceBalance().compareTo(INVOICE_AMOUNT) == 0);
            }
        });

        // Test payment integration - the fields have already been populated, just make sure the code is exercised
        // It's hard to test the actual payments fields though in bac, since we should mock the plugin
        bus.post(paymentInfoNotification, internalCallContext);
    }

    private void waitALittle(final Callable<Boolean> callable) {
        try {
            await().atMost(5, SECONDS).until(callable);
        } catch (Exception e) {
            Assert.fail("Exception in TestAnalyticsService", e);
        }
    }
}
