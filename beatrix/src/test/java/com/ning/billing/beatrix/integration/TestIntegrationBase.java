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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountData;
import com.ning.billing.account.api.AccountService;
import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.analytics.AnalyticsListener;
import com.ning.billing.analytics.api.user.DefaultAnalyticsUserApi;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.beatrix.BeatrixTestSuiteWithEmbeddedDB;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.beatrix.util.InvoiceChecker;
import com.ning.billing.beatrix.util.PaymentChecker;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.dbi.MysqlTestingHelper;
import com.ning.billing.entitlement.api.EntitlementService;
import com.ning.billing.entitlement.api.timeline.EntitlementTimelineApi;
import com.ning.billing.entitlement.api.transfer.EntitlementTransferApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApi;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.junction.plumbing.api.BlockingSubscription;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.mock.api.MockBillCycleDay;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.bus.BusService;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.DefaultCallContext;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.clock.ClockMock;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIntegrationBase extends BeatrixTestSuiteWithEmbeddedDB implements TestListenerStatus {

    protected static final DateTimeZone testTimeZone = DateTimeZone.UTC;

    protected static final int NUMBER_OF_DECIMALS = InvoicingConfiguration.getNumberOfDecimals();
    protected static final int ROUNDING_METHOD = InvoicingConfiguration.getRoundingMode();

    protected static final BigDecimal ONE = new BigDecimal("1.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal TWENTY_NINE = new BigDecimal("29.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY = new BigDecimal("30.0000").setScale(NUMBER_OF_DECIMALS);
    protected static final BigDecimal THIRTY_ONE = new BigDecimal("31.0000").setScale(NUMBER_OF_DECIMALS);

    protected static final Logger log = LoggerFactory.getLogger(TestIntegration.class);
    protected static long AT_LEAST_ONE_MONTH_MS = 32L * 24L * 3600L * 1000L;

    protected static final long DELAY = 5000;

    @Inject
    protected IDBI dbi;

    @Inject
    protected ClockMock clock;

    protected CallContext context;

    @Inject
    protected Lifecycle lifecycle;

    @Inject
    protected BusService busService;

    @Inject
    protected EntitlementService entitlementService;

    @Inject
    protected InvoiceService invoiceService;

    @Inject
    protected AccountService accountService;

    @Inject
    protected MysqlTestingHelper helper;

    @Inject
    protected EntitlementUserApi entitlementUserApi;

    @Inject
    protected EntitlementTransferApi transferApi;

    @Inject
    protected EntitlementTimelineApi repairApi;

    @Inject
    protected InvoiceUserApi invoiceUserApi;

    @Inject
    protected InvoicePaymentApi invoicePaymentApi;

    @Inject
    protected PaymentApi paymentApi;

    @Named(BeatrixModule.PLUGIN_NAME)
    @Inject
    protected MockPaymentProviderPlugin paymentPlugin;

    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    protected AccountUserApi accountUserApi;

    @Inject
    protected DefaultAnalyticsUserApi analyticsUserApi;

    @Inject
    protected TagUserApi tagUserApi;

    @Inject
    protected AnalyticsListener analyticsListener;

    @Inject
    protected InvoiceChecker invoiceChecker;

    @Inject
    protected PaymentChecker paymentChecker;

    protected TestApiListener busHandler;

    private boolean isListenerFailed;
    private String listenerFailedMsg;

    @Override
    public void failed(final String msg) {
        isListenerFailed = true;
        listenerFailedMsg = msg;
    }

    @Override
    public void resetTestListenerStatus() {
        isListenerFailed = false;
        listenerFailedMsg = null;
    }

    protected void assertListenerStatus() {
        if (isListenerFailed) {
            log.error(listenerFailedMsg);
            Assert.fail(listenerFailedMsg);
        }
    }

    @BeforeClass(groups = "slow")
    public void setup() throws Exception {
        context = new DefaultCallContextFactory(clock).createCallContext("Integration Test", CallOrigin.TEST, UserType.TEST);
        busHandler = new TestApiListener(this);
    }

    @BeforeMethod(groups = "slow")
    public void setupTest() throws Exception {
        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");

        clock.resetDeltaFromReality();
        resetTestListenerStatus();
        busHandler.reset();

        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        lifecycle.fireStartupSequencePostEventRegistration();
    }

    @AfterMethod(groups = "slow")
    public void cleanupTest() throws Exception {
        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();

        log.warn("DONE WITH TEST\n");
    }

    protected void verifyTestResult(final UUID accountId, final UUID subscriptionId,
            final DateTime startDate, @Nullable final DateTime endDate,
            final BigDecimal amount, final DateTime chargeThroughDate,
            final int totalInvoiceItemCount) throws EntitlementUserApiException {
        final SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscriptionId));


        /*
        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        final List<InvoiceItem> invoiceItems = new ArrayList<InvoiceItem>();
        for (final Invoice invoice : invoices) {
            invoiceItems.addAll(invoice.getInvoiceItems());
        }
        assertEquals(invoiceItems.size(), totalInvoiceItemCount);

        boolean wasFound = false;

        // We implicitly assume here that the account timezone is the same as the one for startDate/endDate
        for (final InvoiceItem item : invoiceItems) {
            if (item.getStartDate().compareTo(new LocalDate(startDate)) == 0) {
                if ((item.getEndDate() == null && endDate == null) || (item.getEndDate() != null && new LocalDate(endDate).compareTo(item.getEndDate()) == 0)) {
                    if (item.getAmount().compareTo(amount) == 0) {
                        wasFound = true;
                        break;
                    }
                }
            }
        }

        if (!wasFound) {
            fail();
        }
*/
        final DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        // Either the ctd is today (start of the trial) or the clock is strictly before the CTD
        assertTrue(clock.getUTCToday().compareTo(new LocalDate(ctd)) == 0 || clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.toDateTime(testTimeZone).toLocalDate().compareTo(new LocalDate(chargeThroughDate.getYear(), chargeThroughDate.getMonthOfYear(), chargeThroughDate.getDayOfMonth())) == 0);
    }

    protected SubscriptionData subscriptionDataFromSubscription(final Subscription sub) {
        return (SubscriptionData) ((BlockingSubscription) sub).getDelegateSubscription();
    }

    protected Account createAccountWithPaymentMethod(final AccountData accountData) throws Exception {
        final Account account = accountUserApi.createAccount(accountData, context);
        assertNotNull(account);

        final PaymentMethodPlugin info = new PaymentMethodPlugin() {
            @Override
            public boolean isDefaultPaymentMethod() {
                return false;
            }

            @Override
            public String getValueString(final String key) {
                return null;
            }

            @Override
            public List<PaymentMethodKVInfo> getProperties() {
                return null;
            }

            @Override
            public String getExternalPaymentMethodId() {
                return UUID.randomUUID().toString();
            }
        };

        paymentApi.addPaymentMethod(BeatrixModule.PLUGIN_NAME, account, true, info, context);
        return accountUserApi.getAccountById(account.getId());
    }

    protected AccountData getAccountData(final int billingDay) {
        return new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
        .firstNameLength(6)
        .email(UUID.randomUUID().toString().substring(1, 8))
        .phone(UUID.randomUUID().toString().substring(1, 8))
        .migrated(false)
        .isNotifiedForInvoices(false)
        .externalKey(UUID.randomUUID().toString().substring(1, 8))
        .billingCycleDay(new MockBillCycleDay(billingDay))
        .currency(Currency.USD)
        .paymentMethodId(UUID.randomUUID())
        .timeZone(DateTimeZone.UTC)
        .build();
    }

    protected void addMonthsAndCheckForCompletion(final int nbMonth, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void dontcare) {
                clock.addMonths(nbMonth);
                return null;
            }
        }, events);
    }

    protected void setDateAndCheckForCompletion(final DateTime date, final List<NextEvent> events) {
        setDateAndCheckForCompletion(date, events.toArray(new NextEvent[events.size()]));
    }

    protected void setDateAndCheckForCompletion(final DateTime date, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void dontcare) {
                //final Interval it = new Interval(clock.getUTCNow(), date);
                //final int days = it.toPeriod().toStandardDays().getDays();
                clock.setTime(date);
                return null;
            }
        }, events);
    }


    protected void addDaysAndCheckForCompletion(final int nbDays, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void dontcare) {
                clock.addDays(nbDays);
                return null;
            }
        }, events);
    }

    protected void createPaymentAndCheckForCompletion(final Account account, final Invoice invoice, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    paymentApi.createPayment(account, invoice.getId(), invoice.getBalance(), new DefaultCallContext("test", null, null, clock));
                } catch (PaymentApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void createExternalPaymentAndCheckForCompletion(final Account account, final Invoice invoice, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    paymentApi.createExternalPayment(account, invoice.getId(), invoice.getBalance(), new DefaultCallContext("test", null, null, clock));
                } catch (PaymentApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void refundPaymentAndCheckForCompletion(final Account account, final Payment payment, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    paymentApi.createRefund(account, payment.getId(), payment.getPaidAmount(), new DefaultCallContext("test", null, null, clock));
                } catch (PaymentApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void createChargeBackAndCheckForCompletion(final InvoicePayment payment, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    invoicePaymentApi.createChargeback(payment.getId(), payment.getAmount(), new DefaultCallContext("test", null, null, clock));
                } catch (InvoiceApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected Subscription createSubscriptionAndCheckForCompletion(final UUID bundleId,
            final String productName,
            final ProductCategory productCategory,
            final BillingPeriod billingPeriod,
            final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Subscription>() {
            @Override
            public Subscription apply(@Nullable final Void dontcare) {
                try {
                    final Subscription subscription = entitlementUserApi.createSubscription(bundleId,
                            new PlanPhaseSpecifier(productName, productCategory, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null),
                            null,
                            context);
                    assertNotNull(subscription);
                    return subscription;
                } catch (EntitlementUserApiException e) {
                    fail();
                    return null;
                }
            }
        }, events);
    }

    protected Subscription changeSubscriptionAndCheckForCompletion(final Subscription subscription,
            final String productName,
            final BillingPeriod billingPeriod,
            final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Subscription>() {
            @Override
            public Subscription apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    final Subscription refreshedSubscription = entitlementUserApi.getSubscriptionFromId(subscription.getId());
                    refreshedSubscription.changePlan(productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow(), context);
                    return refreshedSubscription;
                } catch (EntitlementUserApiException e) {
                    fail();
                    return null;
                }
            }
        }, events);
    }

    protected Subscription cancelSubscriptionAndCheckForCompletion(final Subscription subscription,
            final DateTime requestedDate,
            final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Subscription>() {
            @Override
            public Subscription apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    final Subscription refreshedSubscription = entitlementUserApi.getSubscriptionFromId(subscription.getId());
                    refreshedSubscription.cancel(requestedDate, true, context);
                    return refreshedSubscription;
                } catch (EntitlementUserApiException e) {
                    fail();
                    return null;
                }
            }
        }, events);
    }

    protected void fullyAdjustInvoiceAndCheckForCompletion(final Account account, final Invoice invoice, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    invoiceUserApi.insertCreditForInvoice(account.getId(), invoice.getId(), invoice.getBalance(), invoice.getInvoiceDate(),
                                                          account.getCurrency(), new DefaultCallContext("test", null, null, clock));
                } catch (InvoiceApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void fullyAdjustInvoiceItemAndCheckForCompletion(final Account account, final Invoice invoice, final int itemNb, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    invoiceUserApi.insertInvoiceItemAdjustment(account.getId(), invoice.getId(), invoice.getInvoiceItems().get(itemNb - 1).getId(),
                                                               invoice.getInvoiceDate(), new DefaultCallContext("test", null, null, clock));
                } catch (InvoiceApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    private <T> T doCallAndCheckForCompletion(Function<Void, T> f, final NextEvent... events) {

        Joiner joiner = Joiner.on(", ");
        log.info("            ************    STARTING BUS HANDLER CHECK : {} ********************", joiner.join(events));

        busHandler.pushExpectedEvents(events);

        final T result = f.apply(null);
        assertTrue(busHandler.isCompleted(DELAY), "Were expecting events " + joiner.join(events));
        assertListenerStatus();

        log.info("            ************    DONE WITH BUS HANDLER CHECK    ********************");
        return result;
    }
}
