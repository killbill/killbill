/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc.
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

package org.killbill.billing.beatrix.integration;

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
import org.killbill.billing.ObjectType;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.Tag;
import org.skife.jdbi.v2.IDBI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountService;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.BeatrixTestSuiteWithEmbeddedDB;
import org.killbill.billing.beatrix.glue.BeatrixModule;
import org.killbill.billing.beatrix.lifecycle.Lifecycle;
import org.killbill.billing.beatrix.osgi.SetupBundleWithAssertion;
import org.killbill.billing.beatrix.util.AccountChecker;
import org.killbill.billing.beatrix.util.InvoiceChecker;
import org.killbill.billing.beatrix.util.PaymentChecker;
import org.killbill.billing.beatrix.util.RefundChecker;
import org.killbill.billing.beatrix.util.SubscriptionChecker;
import org.killbill.bus.api.PersistentBus;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoicePaymentApi;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.overdue.OverdueUserApi;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethodKVInfo;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.TestPaymentMethodPluginBase;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.config.OSGIConfig;
import org.killbill.billing.util.svcsapi.bus.BusService;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIntegrationBase extends BeatrixTestSuiteWithEmbeddedDB {

    protected static final DateTimeZone testTimeZone = DateTimeZone.UTC;

    protected static final Logger log = LoggerFactory.getLogger(TestIntegrationBase.class);
    protected static long AT_LEAST_ONE_MONTH_MS = 32L * 24L * 3600L * 1000L;

    @Inject
    protected Lifecycle lifecycle;

    @Inject
    protected BusService busService;

    @Inject
    protected SubscriptionBaseService subscriptionBaseService;

    @Inject
    protected InvoiceService invoiceService;

    @Inject
    protected AccountService accountService;

    @Inject
    protected SubscriptionBaseTransferApi transferApi;

    @Inject
    protected SubscriptionBaseTimelineApi repairApi;

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

    @Inject
    protected SubscriptionApi subscriptionApi;

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
    @Named(BeatrixModule.EXTERNAL_BUS)
    protected PersistentBus externalBus;

    @Inject
    protected RefundChecker refundChecker;

    @Inject
    protected SubscriptionChecker subscriptionChecker;

    @Inject
    protected AccountInternalApi accountInternalApi;

    @Inject
    protected UsageUserApi usageUserApi;

    @Inject
    protected OSGIConfig osgiConfig;

    @Inject
    protected RecordIdApi recordIdApi;

    @Inject
    protected IDBI idbi;

    @Inject
    protected CacheControllerDispatcher controlCacheDispatcher;

    @Inject
    protected TestApiListener busHandler;

    protected void assertListenerStatus() {
        busHandler.assertListenerStatus();
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new BeatrixIntegrationModule(configSource));
        g.injectMembers(this);

        SetupBundleWithAssertion setupTest = new SetupBundleWithAssertion("whatever", osgiConfig, "whatever");
        setupTest.cleanBundleInstallDir();
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();

        log.debug("beforeMethod callcontext classLoader = " + (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader().toString() : "null"));
        //Thread.currentThread().setContextClassLoader(null);

        log.debug("RESET TEST FRAMEWORK");

        controlCacheDispatcher.clearAll();

        clock.resetDeltaFromReality();
        busHandler.reset();

        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        busService.getBus().register(busHandler);
        lifecycle.fireStartupSequencePostEventRegistration();

        paymentPlugin.clear();

        // Make sure we start with a clean state
        assertListenerStatus();
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        // Make sure we finish in a clean state
        assertListenerStatus();

        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();

        log.debug("afterMethod callcontext classLoader = " + (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader().toString() : "null"));

        log.debug("DONE WITH TEST");
    }

    protected void verifyTestResult(final UUID accountId, final UUID subscriptionId,
                                    final DateTime startDate, @Nullable final DateTime endDate,
                                    final BigDecimal amount, final DateTime chargeThroughDate,
                                    final int totalInvoiceItemCount) throws EntitlementApiException {

        final Entitlement entitlement = entitlementApi.getEntitlementForId(subscriptionId, callContext);

        final SubscriptionBase subscription = ((DefaultEntitlement) entitlement).getSubscriptionBase();
        final DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        // Either the ctd is today (start of the trial) or the clock is strictly before the CTD
        assertTrue(clock.getUTCToday().compareTo(new LocalDate(ctd)) == 0 || clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.toDateTime(testTimeZone).toLocalDate().compareTo(new LocalDate(chargeThroughDate.getYear(), chargeThroughDate.getMonthOfYear(), chargeThroughDate.getDayOfMonth())) == 0);
    }

    protected DefaultSubscriptionBase subscriptionDataFromSubscription(final SubscriptionBase sub) {
        return (DefaultSubscriptionBase) sub;
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
                    final LocalDate effectiveDate = new LocalDate(clock.getUTCNow());
                    final Entitlement entitlement = entitlementApi.createBaseEntitlement(accountId, spec, bundleExternalKey, effectiveDate, callContext);
                    assertNotNull(entitlement);
                    return entitlement;
                } catch (EntitlementApiException e) {
                    fail();
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement addAOEntitlementAndCheckForCompletion(final UUID bundleId,
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
                    final LocalDate effectiveDate = new LocalDate(clock.getUTCNow());
                    final Entitlement entitlement = entitlementApi.addEntitlement(bundleId, spec, effectiveDate, callContext);
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
                                                                        final String priceList,
                                                                        final BillingActionPolicy billingPolicy,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
                    if (billingPolicy == null) {
                        refreshedEntitlement = refreshedEntitlement.changePlan(productName, billingPeriod, priceList, callContext);
                    } else {
                        refreshedEntitlement = refreshedEntitlement.changePlanOverrideBillingPolicy(productName, billingPeriod, priceList, clock.getUTCNow().toLocalDate(), billingPolicy, callContext);
                    }
                    return refreshedEntitlement;
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
                                                                        final BillingActionPolicy billingPolicy,
                                                                        final NextEvent... events) {
        return changeEntitlementAndCheckForCompletion(entitlement, productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, billingPolicy, events);
    }

    protected DefaultEntitlement cancelEntitlementAndCheckForCompletion(final Entitlement entitlement,
                                                                        final DateTime requestedDate,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
                    refreshedEntitlement = refreshedEntitlement.cancelEntitlementWithDate(requestedDate.toLocalDate(), false, callContext);
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

    protected void add_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.addTag(id, type, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertListenerStatus();

        final List<Tag> tags = tagUserApi.getTagsForObject(id, type, false, callContext);
        assertEquals(tags.size(), 1);
    }

    protected void remove_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.removeTag(id, type, ControlTagType.AUTO_PAY_OFF.getId(), callContext);
        assertListenerStatus();
    }

    private <T> T doCallAndCheckForCompletion(Function<Void, T> f, final NextEvent... events) {
        Joiner joiner = Joiner.on(", ");
        log.debug("            ************    STARTING BUS HANDLER CHECK : {} ********************", joiner.join(events));

        busHandler.pushExpectedEvents(events);

        final T result = f.apply(null);
        assertListenerStatus();

        log.debug("            ************    DONE WITH BUS HANDLER CHECK    ********************");
        return result;
    }
}
