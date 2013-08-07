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

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.beatrix.BeatrixTestSuiteWithEmbeddedDB;
import com.ning.billing.beatrix.glue.BeatrixModule;
import com.ning.billing.beatrix.lifecycle.Lifecycle;
import com.ning.billing.beatrix.osgi.SetupBundleWithAssertion;
import com.ning.billing.beatrix.util.AccountChecker;
import com.ning.billing.beatrix.util.InvoiceChecker;
import com.ning.billing.beatrix.util.PaymentChecker;
import com.ning.billing.beatrix.util.RefundChecker;
import com.ning.billing.beatrix.util.SubscriptionChecker;
import com.ning.billing.bus.api.PersistentBus;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.DefaultEntitlement;
import com.ning.billing.entitlement.api.Entitlement;
import com.ning.billing.entitlement.api.EntitlementApi;
import com.ning.billing.entitlement.api.EntitlementApiException;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceApiException;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.api.InvoicePaymentApi;
import com.ning.billing.invoice.api.InvoiceService;
import com.ning.billing.invoice.api.InvoiceUserApi;
import com.ning.billing.invoice.model.InvoicingConfiguration;
import com.ning.billing.mock.MockAccountBuilder;
import com.ning.billing.overdue.OverdueUserApi;
import com.ning.billing.overdue.wrapper.OverdueWrapperFactory;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.PaymentApi;
import com.ning.billing.payment.api.PaymentApiException;
import com.ning.billing.payment.api.PaymentMethodKVInfo;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.api.TestPaymentMethodPluginBase;
import com.ning.billing.payment.provider.MockPaymentProviderPlugin;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.SubscriptionService;
import com.ning.billing.subscription.api.timeline.SubscriptionTimelineApi;
import com.ning.billing.subscription.api.transfer.SubscriptionTransferApi;
import com.ning.billing.subscription.api.user.SubscriptionData;
import com.ning.billing.util.api.RecordIdApi;
import com.ning.billing.util.api.TagUserApi;
import com.ning.billing.util.cache.CacheControllerDispatcher;
import com.ning.billing.util.config.OSGIConfig;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcsapi.bus.BusService;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

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

    protected static final long DELAY = 10000;


    @Inject
    protected Lifecycle lifecycle;

    @Inject
    protected BusService busService;

    @Inject
    protected SubscriptionService subscriptionService;

    @Inject
    protected InvoiceService invoiceService;

    @Inject
    protected AccountService accountService;

    @Inject
    protected SubscriptionTransferApi transferApi;

    @Inject
    protected SubscriptionTimelineApi repairApi;

    @Inject
    protected OverdueUserApi overdueUserApi;

    @Inject
    protected InvoiceUserApi invoiceUserApi;

    @Inject
    protected InvoicePaymentApi invoicePaymentApi;

    @Inject
    protected BlockingInternalApi blockingApi;

    @Inject
    protected PaymentApi paymentApi;

    @Inject
    protected EntitlementApi entitlementApi;

    @Named(BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME)
    @Inject
    protected MockPaymentProviderPlugin paymentPlugin;

    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    protected AccountUserApi accountUserApi;

    @Inject
    protected TagUserApi tagUserApi;

    @Inject
    protected InvoiceChecker invoiceChecker;

    @Inject
    protected PaymentChecker paymentChecker;

    @Inject
    protected AccountChecker accountChecker;

    @Inject
    protected
    @javax.inject.Named(BeatrixModule.EXTERNAL_BUS)
    PersistentBus externalBus;

    @Inject
    protected RefundChecker refundChecker;

    @Inject
    protected SubscriptionChecker subscriptionChecker;

    @Inject
    protected AccountInternalApi accountInternalApi;

    @Inject
    protected OSGIConfig osgiConfig;

    @Inject
    protected RecordIdApi recordIdApi;

    @Inject
    protected IDBI idbi;

    @javax.inject.Inject
    protected CacheControllerDispatcher controlCacheDispatcher;

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
    public void beforeClass() throws Exception {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new BeatrixIntegrationModule(configSource));
        g.injectMembers(this);
        busHandler = new TestApiListener(this, idbi);

        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion("whatever", osgiConfig, "whatever");
        setupTest.cleanBundleInstallDir();

    }


    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {

        super.beforeMethod();

        log.info("beforeMethod context classLoader = " + (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader().toString() : "null"));
        //Thread.currentThread().setContextClassLoader(null);

        log.warn("\n");
        log.warn("RESET TEST FRAMEWORK\n\n");

        controlCacheDispatcher.clearAll();

        clock.resetDeltaFromReality();
        resetTestListenerStatus();
        busHandler.reset();

        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        lifecycle.fireStartupSequencePostEventRegistration();

        paymentPlugin.clear();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();

        log.info("afterMethod context classLoader = " + (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader().toString() : "null"));

        log.warn("DONE WITH TEST\n");
    }

    protected void verifyTestResult(final UUID accountId, final UUID subscriptionId,
                                    final DateTime startDate, @Nullable final DateTime endDate,
                                    final BigDecimal amount, final DateTime chargeThroughDate,
                                    final int totalInvoiceItemCount) throws EntitlementApiException {

        final Entitlement entitlement = entitlementApi.getEntitlementFromId(subscriptionId, callContext);

        final SubscriptionBase subscription = ((DefaultEntitlement) entitlement).getSubscription();
        final DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        // Either the ctd is today (start of the trial) or the clock is strictly before the CTD
        assertTrue(clock.getUTCToday().compareTo(new LocalDate(ctd)) == 0 || clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.toDateTime(testTimeZone).toLocalDate().compareTo(new LocalDate(chargeThroughDate.getYear(), chargeThroughDate.getMonthOfYear(), chargeThroughDate.getDayOfMonth())) == 0);
    }

    protected SubscriptionData subscriptionDataFromSubscription(final SubscriptionBase sub) {
        // STEPH_ENT
        return (SubscriptionData) sub;
    }

    protected Account createAccountWithOsgiPaymentMethod(final AccountData accountData) throws Exception {
        return createAccountWithPaymentMethod(accountData, BeatrixIntegrationModule.OSGI_PLUGIN_NAME);
    }

    protected Account createAccountWithNonOsgiPaymentMethod(final AccountData accountData) throws Exception {
        return createAccountWithPaymentMethod(accountData, BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME);
    }

    private Account createAccountWithPaymentMethod(final AccountData accountData, final String paymentPluginName) throws Exception {
        final Account account = accountUserApi.createAccount(accountData, callContext);
        assertNotNull(account);

        final PaymentMethodPlugin info = createPaymentMethodPlugin();

        paymentApi.addPaymentMethod(paymentPluginName, account, true, info, callContext);
        return accountUserApi.getAccountById(account.getId(), callContext);
    }

    private class TestPaymentMethodPlugin extends TestPaymentMethodPluginBase {

        @Override
        public List<PaymentMethodKVInfo> getProperties() {
            PaymentMethodKVInfo prop = new PaymentMethodKVInfo("whatever", "cool", Boolean.TRUE);
            List<PaymentMethodKVInfo> res = new ArrayList<PaymentMethodKVInfo>();
            res.add(prop);
            return res;
        }
    }

    protected PaymentMethodPlugin createPaymentMethodPlugin() {
        return new TestPaymentMethodPlugin();
    }

    protected AccountData getAccountData(final int billingDay) {
        return new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                       .firstNameLength(6)
                                       .email(UUID.randomUUID().toString().substring(1, 8))
                                       .phone(UUID.randomUUID().toString().substring(1, 8))
                                       .migrated(false)
                                       .isNotifiedForInvoices(false)
                                       .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                       .billingCycleDayLocal(billingDay)
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
                    paymentApi.createPayment(account, invoice.getId(), invoice.getBalance(), callContext);
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
                    paymentApi.createExternalPayment(account, invoice.getId(), invoice.getBalance(), callContext);
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
                    paymentApi.createRefund(account, payment.getId(), payment.getPaidAmount(), callContext);
                } catch (PaymentApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void refundPaymentWithAdjustmenttAndCheckForCompletion(final Account account, final Payment payment, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    paymentApi.createRefundWithAdjustment(account, payment.getId(), payment.getPaidAmount(), callContext);
                } catch (PaymentApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void refundPaymentWithInvoiceItemAdjAndCheckForCompletion(final Account account, final Payment payment, final Set<UUID> invoiceItems, final NextEvent... events) {
        doCallAndCheckForCompletion(new Function<Void, Void>() {
            @Override
            public Void apply(@Nullable final Void input) {
                try {
                    paymentApi.createRefundWithItemsAdjustments(account, payment.getId(), invoiceItems, callContext);
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
                    invoicePaymentApi.createChargeback(payment.getId(), payment.getAmount(), callContext);
                } catch (InvoiceApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected DefaultEntitlement createBaseEntitlementAndCheckForCompletion(final UUID accountId,
                                                                            final String bundleExternalKey,
                                                                            final String productName,
                                                                            final ProductCategory productCategory,
                                                                            final BillingPeriod billingPeriod,
                                                                            final NextEvent... events) {
        if (productCategory == ProductCategory.ADD_ON) {
            throw new RuntimeException("Unxepected Call for creating ADD_ON");
        }

        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, productCategory, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null);
                    final Entitlement entitlement = entitlementApi.createBaseEntitlement(accountId, spec, bundleExternalKey, callContext);
                    assertNotNull(entitlement);
                    return entitlement;
                } catch (EntitlementApiException e) {
                    fail();
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement addAOEntitlementAndCheckForCompletion(final UUID basePlanId,
                                                                       final String productName,
                                                                       final ProductCategory productCategory,
                                                                       final BillingPeriod billingPeriod,
                                                                       final NextEvent... events) {
        if (productCategory != ProductCategory.ADD_ON) {
            throw new RuntimeException("Unexpected Call for creating a productCategory " + productCategory);
        }

        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, productCategory, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null);
                    final Entitlement entitlement = entitlementApi.addEntitlement(basePlanId, spec, callContext);
                    assertNotNull(entitlement);
                    return entitlement;
                } catch (EntitlementApiException e) {
                    fail(e.getMessage());
                    return null;
                }
            }
        }, events);
    }


    protected DefaultEntitlement changeEntitlementAndCheckForCompletion(final Entitlement entitlement,
                                                                        final String productName,
                                                                        final BillingPeriod billingPeriod,
                                                                        final ActionPolicy billingPolicy,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    final Entitlement refreshedEntitlement = entitlementApi.getEntitlementFromId(entitlement.getId(), callContext);
                    if (billingPolicy == null) {
                        refreshedEntitlement.changePlan(productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow().toLocalDate(), callContext);
                    } else {
                        refreshedEntitlement.changePlanOverrideBillingPolicy(productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, clock.getUTCNow().toLocalDate(), billingPolicy, callContext);
                    }
                    return refreshedEntitlement;
                } catch (EntitlementApiException e) {
                    fail(e.getMessage());
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement cancelEntitlementAndCheckForCompletion(final Entitlement entitlement,
                                                                        final DateTime requestedDate,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    final Entitlement refreshedEntitlement = entitlementApi.getEntitlementFromId(entitlement.getId(), callContext);
                    refreshedEntitlement.cancelEntitlementWithDate(requestedDate.toLocalDate(), callContext);
                    return refreshedEntitlement;
                } catch (EntitlementApiException e) {
                    fail(e.getMessage());
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
                                                          account.getCurrency(), callContext);
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
                                                               invoice.getInvoiceDate(), callContext);
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
