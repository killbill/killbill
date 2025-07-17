/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.awaitility.Awaitility;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountData;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountService;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.api.TestApiListener;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.beatrix.BeatrixTestSuiteWithEmbeddedDB;
import org.killbill.billing.beatrix.util.AccountChecker;
import org.killbill.billing.beatrix.util.InvoiceChecker;
import org.killbill.billing.beatrix.util.PaymentChecker;
import org.killbill.billing.beatrix.util.RefundChecker;
import org.killbill.billing.beatrix.util.SubscriptionChecker;
import org.killbill.billing.callcontext.DefaultCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PhaseType;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.DefaultEntitlement;
import org.killbill.billing.entitlement.api.DefaultEntitlementSpecifier;
import org.killbill.billing.entitlement.api.Entitlement;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import org.killbill.billing.entitlement.api.EntitlementApi;
import org.killbill.billing.entitlement.api.EntitlementApiException;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.invoice.ParkedAccountsManager;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.api.InvoiceService;
import org.killbill.billing.invoice.api.InvoiceUserApi;
import org.killbill.billing.junction.BlockingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.lifecycle.api.Lifecycle;
import org.killbill.billing.lifecycle.glue.BusModule;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.osgi.config.OSGIConfig;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.OverdueApi;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.caching.OverdueConfigCache;
import org.killbill.billing.overdue.listener.OverdueListener;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.billing.overdue.wrapper.OverdueWrapperFactory;
import org.killbill.billing.payment.api.AdminPaymentApi;
import org.killbill.billing.payment.api.InvoicePaymentApi;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentApi;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PaymentOptions;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.api.TestPaymentMethodPluginBase;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.api.TransactionType;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.payment.invoice.InvoicePaymentControlPluginApi;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.killbill.billing.platform.jndi.ReferenceableDataSourceSpy;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseService;
import org.killbill.billing.subscription.api.timeline.SubscriptionBaseTimelineApi;
import org.killbill.billing.subscription.api.transfer.SubscriptionBaseTransferApi;
import org.killbill.billing.subscription.api.user.DefaultSubscriptionBase;
import org.killbill.billing.tenant.api.DefaultTenant;
import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.usage.api.SubscriptionUsageRecord;
import org.killbill.billing.usage.api.UnitUsageRecord;
import org.killbill.billing.usage.api.UsageApiException;
import org.killbill.billing.usage.api.UsageRecord;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.api.CustomFieldUserApi;
import org.killbill.billing.util.api.RecordIdApi;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.api.TagDefinitionApiException;
import org.killbill.billing.util.api.TagUserApi;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.features.KillbillFeatures;
import org.killbill.billing.util.nodes.KillbillNodesApi;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.bus.api.PersistentBus;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.killbill.commons.utils.Joiner;
import org.killbill.notificationq.api.NotificationEvent;
import org.killbill.notificationq.api.NotificationEventWithMetadata;
import org.killbill.notificationq.api.NotificationQueue;
import org.killbill.notificationq.api.NotificationQueueService;
import org.skife.config.AugmentedConfigurationObjectFactory;
import org.skife.config.TimeSpan;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.IHookable;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.zaxxer.hikari.HikariDataSource;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestIntegrationBase extends BeatrixTestSuiteWithEmbeddedDB implements IHookable {

    protected static final DateTimeZone testTimeZone = DateTimeZone.UTC;
    protected static final Logger log = LoggerFactory.getLogger(TestIntegrationBase.class);
    protected static long AT_LEAST_ONE_MONTH_MS = 32L * 24L * 3600L * 1000L;

    protected static final PaymentOptions PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return false;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return List.of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
    };
    protected static final PaymentOptions EXTERNAL_PAYMENT_OPTIONS = new PaymentOptions() {
        @Override
        public boolean isExternalPayment() {
            return true;
        }

        @Override
        public List<String> getPaymentControlPluginNames() {
            return List.of(InvoicePaymentControlPluginApi.PLUGIN_NAME);
        }
    };

    protected final Iterable<PluginProperty> PLUGIN_PROPERTIES = Collections.emptyList();

    @Inject
    @Named("osgi")
    protected DataSource osgiDataSource;

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
    protected OverdueApi overdueUserApi;

    @Inject
    protected InvoiceUserApi invoiceUserApi;

    @Inject
    protected InvoicePaymentApi invoicePaymentApi;

    @Inject
    protected BlockingInternalApi blockingApi;

    @Inject
    protected PaymentApi paymentApi;

    @Inject
    protected AdminPaymentApi adminPaymentApi;

    @Inject
    protected EntitlementApi entitlementApi;

    @Inject
    protected SubscriptionApi subscriptionApi;

    @Inject
    protected SubscriptionBaseInternalApi subscriptionBaseInternalApiApi;

    @Named(BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME)
    @Inject
    protected MockPaymentProviderPlugin paymentPlugin;

    @Inject
    protected OverdueWrapperFactory overdueWrapperFactory;

    @Inject
    protected OverdueListener overdueListener;

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
    @Named(BusModule.EXTERNAL_BUS_NAMED)
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
    protected NonEntityDao nonEntityDao;

    @Inject
    protected TestApiListener busHandler;

    @Inject
    protected OverdueConfigCache overdueConfigCache;

    @Inject
    protected TenantUserApi tenantUserApi;

    @Inject
    protected KillbillNodesApi nodesApi;

    @Inject
    protected CatalogUserApi catalogUserApi;

    @Inject
    protected CacheControllerDispatcher controllerDispatcher;

    @Inject
    protected ParkedAccountsManager parkedAccountsManager;

    @Inject
    protected PaymentConfig paymentConfig;

    @Inject
    protected AuditUserApi auditUserApi;

    @Inject
    protected PaymentDao paymentDao;

    @Inject
    protected CustomFieldUserApi customFieldUserApi;

    @Inject
    protected NotificationQueueService notificationQueueService;

    protected ConfigurableInvoiceConfig invoiceConfig;
    protected KillbillFeatures killbillFeatures = new KillbillFeatures();

    @Override
    protected void assertListenerStatus() {
        busHandler.assertListenerStatus();
    }

    @BeforeClass(groups = "slow")
    public void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final InvoiceConfig defaultInvoiceConfig = new AugmentedConfigurationObjectFactory(skifeConfigSource).build(InvoiceConfig.class);
        invoiceConfig = new ConfigurableInvoiceConfig(defaultInvoiceConfig);
        final Injector g = Guice.createInjector(Stage.PRODUCTION, new BeatrixIntegrationModule(configSource, clock, invoiceConfig, killbillFeatures));
        g.injectMembers(this);
    }

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        overdueConfigCache.loadDefaultOverdueConfig((OverdueConfig) null);

        invoiceConfig.reset();
        clock.resetDeltaFromReality();
        busHandler.reset();

        // Start services
        lifecycle.fireStartupSequencePriorEventRegistration();
        registerHandlers();
        lifecycle.fireStartupSequencePostEventRegistration();

        paymentPlugin.clear();
    }

    protected void registerHandlers() throws EventBusException {
        busService.getBus().register(busHandler);
    }

    @AfterMethod(groups = "slow")
    public void afterMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        lifecycle.fireShutdownSequencePriorEventUnRegistration();
        busService.getBus().unregister(busHandler);
        lifecycle.fireShutdownSequencePostEventUnRegistration();

        log.debug("afterMethod callcontext classLoader = " + (Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader().toString() : "null"));

        log.debug("DONE WITH TEST");
    }

    @AfterClass(groups = "slow")
    public void afterClass() throws Exception {
        // Because of the way the OSGI DataSource is created in ReferenceableDataSourceSpyProvider (required by DefaultOSGIService->KillbillActivator),
        // a new instance is generated for each test class. We need to explicitly close it to avoid thread leaks (e.g. Hikari housekeeper).
        if (osgiDataSource instanceof ReferenceableDataSourceSpy) {
            final ReferenceableDataSourceSpy referenceableDataSourceSpy = (ReferenceableDataSourceSpy) this.osgiDataSource;
            if (referenceableDataSourceSpy.getDataSource() instanceof HikariDataSource) {
                ((HikariDataSource) (referenceableDataSourceSpy.getDataSource())).close();
            }
        }
    }

    protected void checkNoMoreInvoiceToGenerate(final Account account) {
        checkNoMoreInvoiceToGenerate(account.getId());
    }

    protected void checkNoMoreInvoiceToGenerate(final UUID accountId) {
        checkNoMoreInvoiceToGenerate(accountId, callContext);
    }

    protected void checkNoMoreInvoiceToGenerate(final UUID accountId, final CallContext callContext) {
        busHandler.pushExpectedEvent(NextEvent.NULL_INVOICE);
        try {
            invoiceUserApi.triggerInvoiceGeneration(accountId, clock.getUTCToday(), Collections.emptyList(), callContext);
            fail("Should not have generated an extra invoice");
        } catch (final InvoiceApiException e) {
            assertListenerStatus();
            assertEquals(e.getCode(), ErrorCode.INVOICE_NOTHING_TO_DO.getCode());
        }
    }

    protected void verifyTestResult(final UUID accountId, final UUID subscriptionId,
                                    final DateTime startDate, @Nullable final DateTime endDate,
                                    final BigDecimal amount, final DateTime chargeThroughDate,
                                    final int totalInvoiceItemCount) throws EntitlementApiException {

        final Entitlement entitlement = entitlementApi.getEntitlementForId(subscriptionId, false, callContext);

        final SubscriptionBase subscription = ((DefaultEntitlement) entitlement).getSubscriptionBase();
        final DateTime ctd = subscription.getChargedThroughDate();
        assertNotNull(ctd);
        log.info("Checking CTD: " + ctd.toString() + "; clock is " + clock.getUTCNow().toString());
        // Either the ctd is today (start of the trial) or the clock is strictly before the CTD
        assertTrue(clock.getUTCToday().compareTo(new LocalDate(ctd)) == 0 || clock.getUTCNow().isBefore(ctd));
        assertTrue(ctd.toDateTime(testTimeZone).toLocalDate().compareTo(new LocalDate(chargeThroughDate.getYear(), chargeThroughDate.getMonthOfYear(), chargeThroughDate.getDayOfMonth())) == 0);
    }

    protected void checkODState(final String expected, final UUID accountId) {
        try {
            // This will test the overdue notification queue: when we move the clock, the overdue system
            // should get notified to refresh its state.
            // Calling explicitly refresh here (overdueApi.refreshOverdueStateFor(account)) would not fully
            // test overdue.
            // Since we're relying on the notification queue, we may need to wait a bit (hence await()).
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    final BlockingState blockingStateForService = blockingApi.getBlockingStateForService(accountId, BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContext);
                    final String stateName = blockingStateForService != null ? blockingStateForService.getStateName() : OverdueWrapper.CLEAR_STATE_NAME;
                    return expected.equals(stateName);
                }
            });
        } catch (final Exception e) {
            final BlockingState blockingStateForService = blockingApi.getBlockingStateForService(accountId, BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContext);
            final String stateName = blockingStateForService != null ? blockingStateForService.getStateName() : OverdueWrapper.CLEAR_STATE_NAME;
            Assert.assertEquals(stateName, expected, "Got exception: " + e.toString());
        }
    }

    protected DefaultSubscriptionBase subscriptionDataFromSubscription(final SubscriptionBase sub) {
        return (DefaultSubscriptionBase) sub;
    }

    protected DefaultCallContext setupTenant() throws TenantApiException {
        final UUID uuid = UUID.randomUUID();
        final String externalKey = uuid.toString();
        final String apiKey = externalKey + "-Key";
        final String apiSecret = externalKey + "-$3cr3t";
        final DateTime init = new DateTime(DateTimeZone.UTC);
        final Tenant tenant = tenantUserApi.createTenant(new DefaultTenant(uuid, init, init, externalKey, apiKey, apiSecret), callContext);

        return new DefaultCallContext(null,
                                      tenant.getId(),
                                      "tester",
                                      CallOrigin.EXTERNAL,
                                      UserType.TEST,
                                      "good reason",
                                      "trust me",
                                      uuid,
                                      clock);
    }

    protected Account setupAccount(final CallContext testCallContext) throws Exception {
        final AccountData accountData = getAccountData(0);
        final Account account = accountUserApi.createAccount(accountData, testCallContext);
        assertNotNull(account);

        final PaymentMethodPlugin info = createPaymentMethodPlugin();
        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, info, PLUGIN_PROPERTIES, testCallContext);

        return account;
    }

    protected Account createAccount(final AccountData accountData) throws Exception {
        final Account account = accountUserApi.createAccount(accountData, callContext);
        assertNotNull(account);

        refreshCallContext(account.getId());

        return accountUserApi.getAccountById(account.getId(), callContext);
    }

    protected Account createAccountWithNonOsgiPaymentMethod(final AccountData accountData) throws Exception {
        final Account account = createAccount(accountData);

        final PaymentMethodPlugin info = createPaymentMethodPlugin();

        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, info, PLUGIN_PROPERTIES, callContext);
        return accountUserApi.getAccountById(account.getId(), callContext);
    }

    protected PaymentMethodPlugin createPaymentMethodPlugin() {
        return new TestPaymentMethodPlugin();
    }

    protected AccountData getAccountData(@Nullable final Integer billingDay) {
        return getAccountData(billingDay, DateTimeZone.UTC);
    }

    protected AccountData getAccountData(@Nullable final Integer billingDay, final DateTimeZone tz) {
        final MockAccountBuilder builder = new MockAccountBuilder()
                .name(UUID.randomUUID().toString().substring(1, 8))
                .firstNameLength(6)
                .email(UUID.randomUUID().toString().substring(1, 8))
                .phone(UUID.randomUUID().toString().substring(1, 8))
                .migrated(false)
                .externalKey(UUID.randomUUID().toString().substring(1, 8))
                .currency(Currency.USD)
                .referenceTime(clock.getUTCNow())
                .timeZone(tz);
        if (billingDay != null) {
            builder.billingCycleDayLocal(billingDay);
        }
        return builder.build();
    }
    
    protected AccountData getAccountData(@Nullable final Integer billingDay, final DateTimeZone tz, final DateTime referenceTime) {
        final MockAccountBuilder builder = new MockAccountBuilder()
                .name(UUID.randomUUID().toString().substring(1, 8))
                .firstNameLength(6)
                .email(UUID.randomUUID().toString().substring(1, 8))
                .phone(UUID.randomUUID().toString().substring(1, 8))
                .migrated(false)
                .externalKey(UUID.randomUUID().toString().substring(1, 8))
                .currency(Currency.USD)
                .referenceTime(referenceTime)
                .timeZone(tz);
        if (billingDay != null) {
            builder.billingCycleDayLocal(billingDay);
        }
        return builder.build();
    }    

    protected AccountData getChildAccountData(final int billingDay, final UUID parentAccountId, final boolean isPaymentDelegatedToParent) {
        return new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                       .firstNameLength(6)
                                       .email(UUID.randomUUID().toString().substring(1, 8))
                                       .phone(UUID.randomUUID().toString().substring(1, 8))
                                       .migrated(false)
                                       .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                       .billingCycleDayLocal(billingDay)
                                       .currency(Currency.USD)
                                       .paymentMethodId(UUID.randomUUID())
                                       .referenceTime(clock.getUTCNow())
                                       .timeZone(DateTimeZone.UTC)
                                       .parentAccountId(parentAccountId)
                                       .isPaymentDelegatedToParent(isPaymentDelegatedToParent)
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

    protected Payment createPaymentAndCheckForCompletion(final Account account,
                                                         final Invoice invoice,
                                                         final BigDecimal amount,
                                                         final Currency currency,
                                                         final NextEvent... events) {
        try {
            return createPaymentAndCheckForCompletion(account,
                                                      invoice,
                                                      amount,
                                                      currency,
                                                      UUID.randomUUID().toString(),
                                                      UUID.randomUUID().toString(),
                                                      events);
        } catch (final PaymentApiException e) {
            fail(e.toString());
            return null;
        }
    }

    protected Payment createPaymentAndCheckForCompletion(final Account account,
                                                         final Invoice invoice,
                                                         final BigDecimal amount,
                                                         final Currency currency,
                                                         final String paymentExternalKey,
                                                         final String transactionExternalKey,
                                                         final NextEvent... events) throws PaymentApiException {
        return doCallAndCheckForCompletionWithException(new FunctionWithException<Void, Payment, PaymentApiException>() {
            @Override
            public Payment apply(@Nullable final Void input) throws PaymentApiException {
                final InvoicePayment invoicePayment = invoicePaymentApi.createPurchaseForInvoicePayment(account,
                                                                                                        invoice.getId(),
                                                                                                        account.getPaymentMethodId(),
                                                                                                        null,
                                                                                                        amount,
                                                                                                        currency,
                                                                                                        null,
                                                                                                        paymentExternalKey,
                                                                                                        transactionExternalKey,
                                                                                                        Collections.emptyList(),
                                                                                                        PAYMENT_OPTIONS,
                                                                                                        callContext);
                return paymentApi.getPayment(invoicePayment.getPaymentId(), false, true, Collections.emptyList(), callContext);
            }
        }, events);
    }

    protected Payment createPaymentAndCheckForCompletion(final Account account, final Invoice invoice, final NextEvent... events) {
        return createPaymentAndCheckForCompletion(account, invoice, invoice.getBalance(), invoice.getCurrency(), events);
    }

    protected Payment createExternalPaymentAndCheckForCompletion(final Account account, final Invoice invoice, final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Payment>() {
            @Override
            public Payment apply(@Nullable final Void input) {
                try {
                    final InvoicePayment invoicePayment = invoicePaymentApi.createPurchaseForInvoicePayment(account,
                                                                                                            invoice.getId(),
                                                                                                            account.getPaymentMethodId(),
                                                                                                            null,
                                                                                                            invoice.getBalance(),
                                                                                                            invoice.getCurrency(),
                                                                                                            null,
                                                                                                            UUID.randomUUID().toString(),
                                                                                                            UUID.randomUUID().toString(),
                                                                                                            Collections.emptyList(),
                                                                                                            EXTERNAL_PAYMENT_OPTIONS,
                                                                                                            callContext);
                    return paymentApi.getPayment(invoicePayment.getPaymentId(), false, false, Collections.emptyList(), callContext);
                } catch (final PaymentApiException e) {
                    fail(e.toString());
                    return null;
                }
            }
        }, events);
    }

    protected Payment refundPaymentAndCheckForCompletion(final Account account, final Payment payment, final NextEvent... events) {
        return refundPaymentAndCheckForCompletion(account, payment, payment.getPurchasedAmount(), payment.getCurrency(), events);
    }

    protected Payment refundPaymentAndCheckForCompletion(final Account account, final Payment payment, final BigDecimal amount, final Currency currency, final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Payment>() {
            @Override
            public Payment apply(@Nullable final Void input) {
                try {
                    return paymentApi.createRefundWithPaymentControl(account, payment.getId(), amount, currency, null, UUID.randomUUID().toString(),
                                                                     PLUGIN_PROPERTIES, PAYMENT_OPTIONS, callContext);
                } catch (final PaymentApiException e) {
                    fail(e.toString());
                    return null;
                }
            }
        }, events);
    }

    protected Payment refundPaymentWithInvoiceItemAdjAndCheckForCompletion(final Account account, final Payment payment, final Map<UUID, BigDecimal> iias, final NextEvent... events) {
        return refundPaymentWithInvoiceItemAdjAndCheckForCompletion(account, payment, payment.getPurchasedAmount(), payment.getCurrency(), iias, events);
    }

    protected Payment refundPaymentWithInvoiceItemAdjAndCheckForCompletion(final Account account, final Payment payment, final BigDecimal amount, final Currency currency, final Map<UUID, BigDecimal> iias, final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Payment>() {
            @Override
            public Payment apply(@Nullable final Void input) {
                try {
                    final InvoicePayment invoicePayment = invoicePaymentApi.createRefundForInvoicePayment(true, iias, account, payment.getId(), amount, currency, null, UUID.randomUUID().toString(),
                                                                                                          Collections.emptyList(), PAYMENT_OPTIONS, callContext);
                    return paymentApi.getPayment(invoicePayment.getPaymentId(), false, false, Collections.emptyList(), callContext);
                } catch (final PaymentApiException e) {
                    fail(e.toString());
                    return null;
                }
            }
        }, events);
    }

    protected Payment createChargeBackAndCheckForCompletion(final Account account, final Payment payment, final NextEvent... events) {
        return createChargeBackAndCheckForCompletion(account, payment, payment.getPurchasedAmount(), payment.getCurrency(), events);
    }

    protected Payment createChargeBackAndCheckForCompletion(final Account account, final Payment payment, final BigDecimal amount, final Currency currency, final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Payment>() {
            @Override
            public Payment apply(@Nullable final Void input) {
                try {
                    return paymentApi.createChargebackWithPaymentControl(account, payment.getId(), amount, currency, null, UUID.randomUUID().toString(),
                                                                         PAYMENT_OPTIONS, callContext);
                } catch (final PaymentApiException e) {
                    fail(e.toString());
                    return null;
                }
            }
        }, events);
    }

    protected Payment createChargeBackReversalAndCheckForCompletion(final Account account, final Payment payment, final NextEvent... events) {
        final List<PaymentTransaction> reversedPaymentTransactions = new ArrayList<>(payment.getTransactions());
        Collections.reverse(reversedPaymentTransactions);
        final PaymentTransaction chargeback = reversedPaymentTransactions.stream()
                .filter(input -> TransactionType.CHARGEBACK.equals(input.getTransactionType()) &&
                                 TransactionStatus.SUCCESS.equals(input.getTransactionStatus()))
                .findFirst().get();

        return createChargeBackReversalAndCheckForCompletion(account, payment, chargeback.getExternalKey(), events);
    }

    protected Payment createChargeBackReversalAndCheckForCompletion(final Account account, final Payment payment, final String chargebackTransactionExternalKey, final NextEvent... events) {
        return doCallAndCheckForCompletion(new Function<Void, Payment>() {
            @Override
            public Payment apply(@Nullable final Void input) {
                try {
                    return paymentApi.createChargebackReversalWithPaymentControl(account, payment.getId(), null, chargebackTransactionExternalKey, PAYMENT_OPTIONS, callContext);
                } catch (final PaymentApiException e) {
                    fail(e.toString());
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement createBaseEntitlementWithPriceOverrideAndCheckForCompletion(final UUID accountId,
                                                                                             final String bundleExternalKey,
                                                                                             final String productName,
                                                                                             final ProductCategory productCategory,
                                                                                             final BillingPeriod billingPeriod,
                                                                                             final List<PlanPhasePriceOverride> overrides,
                                                                                             final NextEvent... events) {
        return createBaseEntitlementWithPriceOverrideAndCheckForCompletion(accountId,
                                                                           bundleExternalKey,
                                                                           productName,
                                                                           productCategory,
                                                                           billingPeriod,
                                                                           overrides,
                                                                           null,
                                                                           PriceListSet.DEFAULT_PRICELIST_NAME,
                                                                           events);
    }

    protected DefaultEntitlement createBaseEntitlementWithPriceOverrideAndCheckForCompletion(final UUID accountId,
                                                                                             final String bundleExternalKey,
                                                                                             final String productName,
                                                                                             final ProductCategory productCategory,
                                                                                             final BillingPeriod billingPeriod,
                                                                                             final List<PlanPhasePriceOverride> overrides,
                                                                                             final LocalDate billingEffectiveDate,
                                                                                             final String priceList,
                                                                                             final NextEvent... events) {
        if (productCategory == ProductCategory.ADD_ON) {
            throw new RuntimeException("Unxepected Call for creating ADD_ON");
        }

        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, billingPeriod, priceList, null);
                    final UUID entitlementId = entitlementApi.createBaseEntitlement(accountId, new DefaultEntitlementSpecifier(spec, null, null, null, overrides), bundleExternalKey, null, billingEffectiveDate, false, true, Collections.emptyList(), callContext);
                    assertNotNull(entitlementId);
                    return entitlementApi.getEntitlementForId(entitlementId, false, callContext);
                } catch (final EntitlementApiException e) {
                    fail("Unable to create entitlement", e);
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement createBaseEntitlementAndCheckForCompletion(final UUID accountId,
                                                                            final String bundleExternalKey,
                                                                            final String productName,
                                                                            final ProductCategory productCategory,
                                                                            final BillingPeriod billingPeriod,
                                                                            final NextEvent... events) {
        return createBaseEntitlementWithPriceOverrideAndCheckForCompletion(accountId, bundleExternalKey, productName, productCategory, billingPeriod, null, events);
    }

    protected DefaultEntitlement addAOEntitlementAndCheckForCompletion(final UUID bundleId,
                                                                       final String productName,
                                                                       final ProductCategory productCategory,
                                                                       final BillingPeriod billingPeriod,
                                                                       final NextEvent... events) {
        return addAOEntitlementAndCheckForCompletion(bundleId, productName, productCategory, billingPeriod, null, events);
    }

    protected DefaultEntitlement addAOEntitlementAndCheckForCompletion(final UUID bundleId,
                                                                       final String productName,
                                                                       final ProductCategory productCategory,
                                                                       final BillingPeriod billingPeriod,
                                                                       final LocalDate effectiveDate,
                                                                       final NextEvent... events) {
        if (productCategory != ProductCategory.ADD_ON) {
            throw new RuntimeException("Unexpected Call for creating a productCategory " + productCategory);
        }

        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, billingPeriod, PriceListSet.DEFAULT_PRICELIST_NAME, null);
                    final UUID entitlementId = entitlementApi.addEntitlement(bundleId, new DefaultEntitlementSpecifier(spec), effectiveDate, effectiveDate, false, Collections.emptyList(), callContext);
                    assertNotNull(entitlementId);
                    return entitlementApi.getEntitlementForId(entitlementId, false, callContext);
                } catch (final EntitlementApiException e) {
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
                    Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
                    final PlanPhaseSpecifier spec = new PlanPhaseSpecifier(productName, billingPeriod, priceList);
                    if (billingPolicy == null) {
                        refreshedEntitlement = refreshedEntitlement.changePlan(new DefaultEntitlementSpecifier(spec), Collections.emptyList(), callContext);
                    } else {
                        refreshedEntitlement = refreshedEntitlement.changePlanOverrideBillingPolicy(new DefaultEntitlementSpecifier(spec), null, billingPolicy, Collections.emptyList(), callContext);
                    }
                    return refreshedEntitlement;
                } catch (final EntitlementApiException e) {
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
                                                                        final NextEvent... events) {
        return cancelEntitlementAndCheckForCompletion(entitlement, null, events);
    }

    protected DefaultEntitlement cancelEntitlementAndCheckForCompletion(final Entitlement entitlement,
                                                                        final LocalDate requestedDate,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
                    refreshedEntitlement = refreshedEntitlement.cancelEntitlementWithDate(requestedDate, false, Collections.emptyList(), callContext);
                    return refreshedEntitlement;
                } catch (final EntitlementApiException e) {
                    fail(e.getMessage());
                    return null;
                }
            }
        }, events);
    }

    protected DefaultEntitlement cancelEntitlementAndCheckForCompletion(final Entitlement entitlement,
                                                                        final EntitlementActionPolicy entitlementActionPolicy,
                                                                        final BillingActionPolicy billingActionPolicy,
                                                                        final NextEvent... events) {
        return (DefaultEntitlement) doCallAndCheckForCompletion(new Function<Void, Entitlement>() {
            @Override
            public Entitlement apply(@Nullable final Void dontcare) {
                try {
                    // Need to fetch again to get latest CTD updated from the system
                    Entitlement refreshedEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), false, callContext);
                    refreshedEntitlement = refreshedEntitlement.cancelEntitlementWithPolicyOverrideBillingPolicy(entitlementActionPolicy, billingActionPolicy, Collections.emptyList(), callContext);
                    return refreshedEntitlement;
                } catch (final EntitlementApiException e) {
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
                    for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
                        invoiceUserApi.insertInvoiceItemAdjustment(account.getId(),
                                                                   invoice.getId(),
                                                                   invoiceItem.getId(),
                                                                   invoice.getInvoiceDate(),
                                                                   null,
                                                                   null,
                                                                   null,
                                                                   callContext);
                    }

                } catch (final InvoiceApiException e) {
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
                                                               invoice.getInvoiceDate(), null, null, null, callContext);
                } catch (final InvoiceApiException e) {
                    fail(e.toString());
                }
                return null;
            }
        }, events);
    }

    protected void add_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        add_account_Tag(id, ControlTagType.AUTO_PAY_OFF, type);
    }

    protected void add_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        add_account_Tag(id, ControlTagType.AUTO_INVOICING_OFF, type);
    }

    protected void add_AUTO_INVOICING_DRAFT_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        add_account_Tag(id, ControlTagType.AUTO_INVOICING_DRAFT, type);
    }

    protected void add_AUTO_INVOICING_REUSE_DRAFT_Tag(final UUID id, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        add_account_Tag(id, ControlTagType.AUTO_INVOICING_REUSE_DRAFT, type);
    }

    private void add_account_Tag(final UUID id, final ControlTagType controlTagType, final ObjectType type) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        tagUserApi.addTag(id, type, controlTagType.getId(), callContext);
        assertListenerStatus();
        tagUserApi.getTagsForObject(id, type, false, callContext);
    }

    protected void remove_AUTO_PAY_OFF_Tag(final UUID id, final ObjectType type, final NextEvent... additionalEvents) throws TagDefinitionApiException, TagApiException {
        remove_account_Tag(id, ControlTagType.AUTO_PAY_OFF, type, additionalEvents);
    }

    protected void remove_AUTO_INVOICING_OFF_Tag(final UUID id, final ObjectType type, final NextEvent... additionalEvents) throws TagDefinitionApiException, TagApiException {
        remove_account_Tag(id, ControlTagType.AUTO_INVOICING_OFF, type, additionalEvents);
    }

    protected void remove_AUTO_INVOICING_DRAFT_Tag(final UUID id, final ObjectType type, final NextEvent... additionalEvents) throws TagDefinitionApiException, TagApiException {
        remove_account_Tag(id, ControlTagType.AUTO_INVOICING_DRAFT, type, additionalEvents);
    }

    private void remove_account_Tag(final UUID id, final ControlTagType controlTagType, final ObjectType type, final NextEvent... additionalEvents) throws TagDefinitionApiException, TagApiException {
        busHandler.pushExpectedEvent(NextEvent.TAG);
        busHandler.pushExpectedEvents(additionalEvents);
        tagUserApi.removeTag(id, type, controlTagType.getId(), callContext);
        assertListenerStatus();
    }

    private <T> T doCallAndCheckForCompletion(final Function<Void, T> f, final NextEvent... events) {
        try {
            return doCallAndCheckForCompletionWithException(new FunctionWithException<Void, T, RuntimeException>() {
                                                                @Override
                                                                public T apply(final Void input) throws RuntimeException {
                                                                    return f.apply(input);
                                                                }
                                                            },
                                                            events);
        } catch (final RuntimeException e) {
            fail(e.getMessage());
            return null;
        }

    }

    private <T, E extends Throwable> T doCallAndCheckForCompletionWithException(final FunctionWithException<Void, T, E> f, final NextEvent... events) throws E {
        final Joiner joiner = Joiner.on(", ");
        log.debug("            ************    STARTING BUS HANDLER CHECK : {} ********************", joiner.join(List.of(events)));

        busHandler.pushExpectedEvents(events);

        final T result = f.apply(null);
        assertListenerStatus();

        log.debug("            ************    DONE WITH BUS HANDLER CHECK    ********************");
        return result;
    }

    public interface FunctionWithException<F, T, E extends Throwable> {

        T apply(F input) throws E;
    }

    protected void recordUsageData(final UUID subscriptionId,
                                   final String trackingId,
                                   final String unitType,
                                   final DateTime startDate,
                                   final BigDecimal amount,
                                   final CallContext context) throws UsageApiException {
        final List<UsageRecord> usageRecords = new ArrayList<>();
        usageRecords.add(new UsageRecord(startDate, amount));
        final List<UnitUsageRecord> unitUsageRecords = new ArrayList<>();
        unitUsageRecords.add(new UnitUsageRecord(unitType, usageRecords));
        final SubscriptionUsageRecord record = new SubscriptionUsageRecord(subscriptionId, trackingId, unitUsageRecords);
        usageUserApi.recordRolledUpUsage(record, context);
    }

    // Provide a backward compatible test method to record usage points using LocalDate
    // and transforming such date using account#referenceTime
    protected void recordUsageData(final UUID subscriptionId,
            final String trackingId,
            final String unitType,
            final LocalDate startDate,
            final BigDecimal amount,
            final CallContext context) throws UsageApiException {
    	final List<UsageRecord> usageRecords = new ArrayList<>();
    	usageRecords.add(new UsageRecord(internalCallContext.toUTCDateTime(startDate), amount));
    	final List<UnitUsageRecord> unitUsageRecords = new ArrayList<>();
    	unitUsageRecords.add(new UnitUsageRecord(unitType, usageRecords));
    	final SubscriptionUsageRecord record = new SubscriptionUsageRecord(subscriptionId, trackingId, unitUsageRecords);
    	usageUserApi.recordRolledUpUsage(record, context);
    }    


    protected void recordUsageData(final SubscriptionUsageRecord usageRecord, final CallContext context) throws UsageApiException {
        usageUserApi.recordRolledUpUsage(usageRecord, context);
    }

    protected void removeUsageData(final UUID subscriptionId, final String unitType, final LocalDate recordedDate) {
        dbi.withHandle(new HandleCallback<Void>() {
            @Override
            public Void withHandle(final Handle handle) throws Exception {
                handle.execute("delete from rolled_up_usage where subscription_id = ? and unit_type = ? and record_date = ?",
                               subscriptionId, unitType, recordedDate);
                return null;
            }
        });
    }

    protected boolean areAllNotificationsProcessed(final Long tenantRecordId) {
        int nbNotifications = 0;
        for (final NotificationQueue notificationQueue : notificationQueueService.getNotificationQueues()) {
            final Iterator<NotificationEventWithMetadata<NotificationEvent>> iterator = notificationQueue.getFutureOrInProcessingNotificationForSearchKey2(null, tenantRecordId).iterator();
            try {
                while (iterator.hasNext()) {
                    final NotificationEventWithMetadata<NotificationEvent> notificationEvent = iterator.next();
                    if (!notificationEvent.getEffectiveDate().isAfter(clock.getUTCNow())) {
                        nbNotifications += 1;
                    }
                }
            } finally {
                // Go through all results to close the connection
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }
        }
        if (nbNotifications != 0) {
            log.info("Remaining {} notifications to process", nbNotifications);
        }
        return nbNotifications == 0;
    }

    protected void checkAllNotificationProcessed(final Long tenantRecordId) {
        // Verify notification(s) moved to the retry queue
        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return areAllNotificationsProcessed(tenantRecordId);
            }
        });
    }

    protected static class TestDryRunArguments implements DryRunArguments {

        private final DryRunType dryRunType;
        private final EntitlementSpecifier spec;
        private final SubscriptionEventType action;
        private final UUID subscriptionId;
        private final UUID bundleId;
        private final LocalDate effectiveDate;
        private final BillingActionPolicy billingPolicy;

        public TestDryRunArguments(final DryRunType dryRunType) {
            this.dryRunType = dryRunType;
            this.spec = null;
            this.action = null;
            this.subscriptionId = null;
            this.bundleId = null;
            this.effectiveDate = null;
            this.billingPolicy = null;
        }

        public TestDryRunArguments(final DryRunType dryRunType,
                                   final String productName,
                                   final ProductCategory category,
                                   final BillingPeriod billingPeriod,
                                   final String priceList,
                                   final PhaseType phaseType,
                                   final SubscriptionEventType action,
                                   final UUID subscriptionId,
                                   final UUID bundleId,
                                   final LocalDate effectiveDate,
                                   final BillingActionPolicy billingPolicy) {
            this(dryRunType, productName, category, billingPeriod, priceList, phaseType, action, subscriptionId, bundleId, effectiveDate, billingPolicy, null);
        }

        public TestDryRunArguments(final DryRunType dryRunType,
                                   final String productName,
                                   final ProductCategory category,
                                   final BillingPeriod billingPeriod,
                                   final String priceList,
                                   final PhaseType phaseType,
                                   final SubscriptionEventType action,
                                   final UUID subscriptionId,
                                   final UUID bundleId,
                                   final LocalDate effectiveDate,
                                   final BillingActionPolicy billingPolicy,
                                   @Nullable final List<PlanPhasePriceOverride> overrides) {
            this.dryRunType = dryRunType;
            this.spec = new DefaultEntitlementSpecifier(new PlanPhaseSpecifier(productName, billingPeriod, priceList, phaseType), null, null, null, overrides);
            this.action = action;
            this.subscriptionId = subscriptionId;
            this.bundleId = bundleId;
            this.effectiveDate = effectiveDate;
            this.billingPolicy = billingPolicy;
        }

        @Override
        public DryRunType getDryRunType() {
            return dryRunType;
        }

        @Override
        public EntitlementSpecifier getEntitlementSpecifier() {
            return spec;
        }

        @Override
        public SubscriptionEventType getAction() {
            return action;
        }

        @Override
        public UUID getSubscriptionId() {
            return subscriptionId;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        @Override
        public UUID getBundleId() {
            return bundleId;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return billingPolicy;
        }
    }

    private class TestPaymentMethodPlugin extends TestPaymentMethodPluginBase {

        @Override
        public List<PluginProperty> getProperties() {
            final PluginProperty prop = new PluginProperty("whatever", "cool", Boolean.TRUE);
            final List<PluginProperty> res = new ArrayList<PluginProperty>();
            res.add(prop);
            return res;
        }
    }

    public static class ConfigurableInvoiceConfig implements InvoiceConfig {

        private final InvoiceConfig defaultInvoiceConfig;
        private boolean isInvoicingSystemEnabled;
        private boolean shouldParkAccountsWithUnknownUsage;
        private boolean isZeroAmountUsageDisabled;
        private UsageDetailMode detailMode;
        private InArrearMode inArrearMode;
        private Period maxInvoiceLimit;
        private int maxRawUsagePreviousPeriod;

        private AccountTzOffset accountTzOffset;

        public ConfigurableInvoiceConfig(final InvoiceConfig defaultInvoiceConfig) {
            this.defaultInvoiceConfig = defaultInvoiceConfig;
            reset();
        }

        @Override
        public int getNumberOfMonthsInFuture() {
            return defaultInvoiceConfig.getNumberOfMonthsInFuture();
        }

        @Override
        public int getNumberOfMonthsInFuture(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getNumberOfMonthsInFuture();
        }

        @Override
        public boolean isSanitySafetyBoundEnabled() {
            return defaultInvoiceConfig.isSanitySafetyBoundEnabled();
        }

        @Override
        public boolean isSanitySafetyBoundEnabled(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.isSanitySafetyBoundEnabled();
        }

        @Override
        public boolean isUsageZeroAmountDisabled() {
            return isZeroAmountUsageDisabled;
        }

        @Override
        public boolean isUsageZeroAmountDisabled(final InternalTenantContext tenantContext) {
            return isUsageZeroAmountDisabled();
        }

        @Override
        public boolean isUsageMissingLenient() {
            return defaultInvoiceConfig.isUsageMissingLenient();
        }

        @Override
        public boolean isUsageMissingLenient(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.isUsageMissingLenient();
        }

        @Override
        public int getMaxDailyNumberOfItemsSafetyBound() {
            return defaultInvoiceConfig.getMaxDailyNumberOfItemsSafetyBound();
        }

        @Override
        public int getMaxDailyNumberOfItemsSafetyBound(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getMaxDailyNumberOfItemsSafetyBound();
        }

        @Override
        public TimeSpan getDryRunNotificationSchedule() {
            return defaultInvoiceConfig.getDryRunNotificationSchedule();
        }

        @Override
        public TimeSpan getDryRunNotificationSchedule(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getDryRunNotificationSchedule();
        }

        @Override
        public Period getMaxInvoiceLimit() {
            return maxInvoiceLimit;
        }

        @Override
        public Period getMaxInvoiceLimit(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getMaxInvoiceLimit(tenantContext);
        }

        @Override
        public int getProrationFixedDays() {
            return defaultInvoiceConfig.getProrationFixedDays();
        }

        @Override
        public int getProrationFixedDays(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getProrationFixedDays(tenantContext);
        }

        public void setMaxInvoiceLimit(final Period value) {
            this.maxInvoiceLimit = value;
        }

        @Override
        public int getMaxRawUsagePreviousPeriod() {
            return maxRawUsagePreviousPeriod;
        }

        @Override
        public int getMaxRawUsagePreviousPeriod(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getMaxRawUsagePreviousPeriod();
        }

        public void setMaxRawUsagePreviousPeriod(int maxRawUsagePreviousPeriod) {
            this.maxRawUsagePreviousPeriod = maxRawUsagePreviousPeriod;
        }

        @Override
        public int getMaxGlobalLockRetries() {
            return defaultInvoiceConfig.getMaxGlobalLockRetries();
        }

        @Override
        public List<String> getInvoicePluginNames() {
            return defaultInvoiceConfig.getInvoicePluginNames();
        }

        @Override
        public List<String> getInvoicePluginNames(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getInvoicePluginNames();
        }

        @Override
        public boolean isEmailNotificationsEnabled() {
            return defaultInvoiceConfig.isEmailNotificationsEnabled();
        }

        @Override
        public boolean isInvoicingSystemEnabled() {
            return isInvoicingSystemEnabled;
        }

        @Override
        public String getParentAutoCommitUtcTime() {
            return defaultInvoiceConfig.getParentAutoCommitUtcTime();
        }

        @Override
        public String getParentAutoCommitUtcTime(final InternalTenantContext tenantContext) {
            return defaultInvoiceConfig.getParentAutoCommitUtcTime();
        }

        @Override
        public boolean isInvoicingSystemEnabled(final InternalTenantContext tenantContext) {
            return isInvoicingSystemEnabled();
        }

        @Override
        public UsageDetailMode getItemResultBehaviorMode() {
            return detailMode;
        }

        @Override
        public UsageDetailMode getItemResultBehaviorMode(final InternalTenantContext tenantContext) {
            return getItemResultBehaviorMode();
        }

        @Override
        public AccountTzOffset getAccountTzOffsetMode() {
            return accountTzOffset;
        }

        @Override
        public AccountTzOffset getAccountTzOffsetMode(final InternalTenantContext tenantContext) {
            return getAccountTzOffsetMode();
        }

        @Override
        public InArrearMode getInArrearMode() {
            return inArrearMode;
        }

        @Override
        public InArrearMode getInArrearMode(final InternalTenantContext tenantContext) {
            return getInArrearMode();
        }

        public void setInArrearMode(final InArrearMode inArrearMode) {
            this.inArrearMode = inArrearMode;
        }

        public void setItemResultBehaviorMode(final UsageDetailMode detailMode) {
            this.detailMode = detailMode;
        }

        public void setInvoicingSystemEnabled(final boolean invoicingSystemEnabled) {
            isInvoicingSystemEnabled = invoicingSystemEnabled;
        }

        @Override
        public boolean shouldParkAccountsWithUnknownUsage() {
            return shouldParkAccountsWithUnknownUsage;
        }

        @Override
        public boolean shouldParkAccountsWithUnknownUsage(final InternalTenantContext tenantContext) {
            return shouldParkAccountsWithUnknownUsage();
        }

        @Override
        public List<TimeSpan> getRescheduleIntervalOnLock() {
            return defaultInvoiceConfig.getRescheduleIntervalOnLock();
        }

        @Override
        public List<TimeSpan> getRescheduleIntervalOnLock(final InternalTenantContext tenantContext) {
            return getRescheduleIntervalOnLock();
        }

        public void setShouldParkAccountsWithUnknownUsage(final boolean shouldParkAccountsWithUnknownUsage) {
            this.shouldParkAccountsWithUnknownUsage = shouldParkAccountsWithUnknownUsage;
        }

        public void setZeroAmountUsageDisabled(final boolean isZeroAmountUsageDisabled) {
            this.isZeroAmountUsageDisabled = isZeroAmountUsageDisabled;
        }

        public void setAccountTzOffset(final AccountTzOffset accountTzOffset) {
            this.accountTzOffset = accountTzOffset;
        }

        public void reset() {
            isInvoicingSystemEnabled = defaultInvoiceConfig.isInvoicingSystemEnabled();
            shouldParkAccountsWithUnknownUsage = defaultInvoiceConfig.shouldParkAccountsWithUnknownUsage();
            isZeroAmountUsageDisabled = defaultInvoiceConfig.isUsageZeroAmountDisabled();
            detailMode = defaultInvoiceConfig.getItemResultBehaviorMode();
            inArrearMode = defaultInvoiceConfig.getInArrearMode();
            maxInvoiceLimit = defaultInvoiceConfig.getMaxInvoiceLimit();
            maxRawUsagePreviousPeriod = defaultInvoiceConfig.getMaxRawUsagePreviousPeriod();
            accountTzOffset = defaultInvoiceConfig.getAccountTzOffsetMode();
        }
    }
}
