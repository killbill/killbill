/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

package org.killbill.billing.invoice;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.GuicyKillbillTestSuite;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.account.api.AccountInternalApi;
import org.killbill.billing.account.api.AccountUserApi;
import org.killbill.billing.account.api.ImmutableAccountInternalApi;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PlanPhasePriceOverride;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.api.InvoiceNotifier;
import org.killbill.billing.invoice.api.InvoicePayment;
import org.killbill.billing.invoice.dao.InvoiceDao;
import org.killbill.billing.invoice.dao.InvoiceItemModelDao;
import org.killbill.billing.invoice.dao.InvoiceItemSqlDao;
import org.killbill.billing.invoice.dao.InvoiceModelDao;
import org.killbill.billing.invoice.dao.InvoiceModelDaoHelper;
import org.killbill.billing.invoice.dao.InvoicePaymentModelDao;
import org.killbill.billing.invoice.dao.InvoicePaymentSqlDao;
import org.killbill.billing.invoice.dao.InvoiceSqlDao;
import org.killbill.billing.invoice.generator.InvoiceGenerator;
import org.killbill.billing.invoice.notification.NullInvoiceNotifier;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.lifecycle.api.BusService;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class TestInvoiceHelper {

    public static final Currency accountCurrency = Currency.USD;

    public static final BigDecimal ZERO = new BigDecimal("0.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal ONE_HALF = new BigDecimal("0.5").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal ONE = new BigDecimal("1.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal ONE_AND_A_HALF = new BigDecimal("1.5").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal TWO = new BigDecimal("2.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THREE = new BigDecimal("3.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal FOUR = new BigDecimal("4.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal FIVE = new BigDecimal("5.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal SIX = new BigDecimal("6.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal SEVEN = new BigDecimal("7.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal EIGHT = new BigDecimal("8.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal TEN = new BigDecimal("10.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal ELEVEN = new BigDecimal("11.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal TWELVE = new BigDecimal("12.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THIRTEEN = new BigDecimal("13.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal FOURTEEN = new BigDecimal("14.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal FIFTEEN = new BigDecimal("15.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal NINETEEN = new BigDecimal("19.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal TWENTY = new BigDecimal("20.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal TWENTY_FOUR = new BigDecimal("24.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal TWENTY_FIVE = new BigDecimal("25.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal TWENTY_EIGHT = new BigDecimal("28.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal TWENTY_NINE = new BigDecimal("29.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THIRTY = new BigDecimal("30.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THIRTY_ONE = new BigDecimal("31.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THIRTY_THREE = new BigDecimal("33.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal FORTY = new BigDecimal("40.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal FORTY_SEVEN = new BigDecimal("47.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal SIXTY_SIX = new BigDecimal("66.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal SEVENTY_FIVE = new BigDecimal("75.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal EIGHTY_NINE = new BigDecimal("89.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal NINETY = new BigDecimal("90.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal NINETY_ONE = new BigDecimal("91.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal NINETY_TWO = new BigDecimal("92.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal ONE_HUNDRED = new BigDecimal("100.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal THREE_HUNDRED_AND_FOURTY_NINE = new BigDecimal("349.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THREE_HUNDRED_AND_FIFTY_FOUR = new BigDecimal("354.0").setScale(KillBillMoney.MAX_SCALE);

    public static final BigDecimal THREE_HUNDRED_AND_SIXTY_FIVE = new BigDecimal("365.0").setScale(KillBillMoney.MAX_SCALE);
    public static final BigDecimal THREE_HUNDRED_AND_SIXTY_SIX = new BigDecimal("366.0").setScale(KillBillMoney.MAX_SCALE);

    private final InvoiceGenerator generator;
    private final BillingInternalApi billingApi;
    private final AccountInternalApi accountApi;
    private final ImmutableAccountInternalApi immutableAccountApi;
    private final InvoicePluginDispatcher invoicePluginDispatcher;
    private final AccountUserApi accountUserApi;
    private final SubscriptionBaseInternalApi subscriptionApi;
    private final BusService busService;
    private final InvoiceDao invoiceDao;
    private final GlobalLocker locker;
    private final Clock clock;
    private final NonEntityDao nonEntityDao;
    private final ParkedAccountsManager parkedAccountsManager;
    private final MutableInternalCallContext internalCallContext;
    private final InternalCallContextFactory internalCallContextFactory;
    private final InvoiceConfig invoiceConfig;
    private final NotificationQueueService notificationQueueService;
    // Low level SqlDao used by the tests to directly insert rows
    private final InvoicePaymentSqlDao invoicePaymentSqlDao;
    private final InvoiceItemSqlDao invoiceItemSqlDao;
    private final InvoiceSqlDao invoiceSqlDao;


    @Inject
    public TestInvoiceHelper(final InvoiceGenerator generator, final IDBI dbi,
                             final BillingInternalApi billingApi, final AccountInternalApi accountApi, final ImmutableAccountInternalApi immutableAccountApi, final InvoicePluginDispatcher invoicePluginDispatcher, final AccountUserApi accountUserApi, final SubscriptionBaseInternalApi subscriptionApi, final BusService busService,
                             final InvoiceDao invoiceDao, final GlobalLocker locker, final Clock clock, final NonEntityDao nonEntityDao, final NotificationQueueService notificationQueueService, final MutableInternalCallContext internalCallContext, final InvoiceConfig invoiceConfig,
                             final ParkedAccountsManager parkedAccountsManager, final InternalCallContextFactory internalCallContextFactory) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.accountApi = accountApi;
        this.immutableAccountApi = immutableAccountApi;
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.accountUserApi = accountUserApi;
        this.subscriptionApi = subscriptionApi;
        this.busService = busService;
        this.invoiceDao = invoiceDao;
        this.locker = locker;
        this.clock = clock;
        this.nonEntityDao = nonEntityDao;
        this.notificationQueueService = notificationQueueService;
        this.parkedAccountsManager = parkedAccountsManager;
        this.internalCallContext = internalCallContext;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.invoiceConfig = invoiceConfig;
    }

    public UUID generateRegularInvoice(final Account account, final LocalDate targetDate, final CallContext callContext) throws Exception {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(new UUID(0L, 0L));
        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = new DateTime().minusDays(1);
        final Currency currency = Currency.USD;
        final BigDecimal fixedPrice = null;
        events.add(createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                          fixedPrice, BigDecimal.ONE, currency, BillingPeriod.MONTHLY, 1,
                                          BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final InvoiceNotifier invoiceNotifier = new NullInvoiceNotifier();
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi,
                                                                   invoiceDao, internalCallContextFactory, invoiceNotifier, invoicePluginDispatcher, locker, busService.getBus(),
                                                                   notificationQueueService, invoiceConfig, clock, parkedAccountsManager);

        Invoice invoice = dispatcher.processAccountFromNotificationOrBusEvent(account.getId(), targetDate, new DryRunFutureDateArguments(), internalCallContext);
        Assert.assertNotNull(invoice);

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(context);
        Assert.assertEquals(invoices.size(), 0);

        invoice = dispatcher.processAccountFromNotificationOrBusEvent(account.getId(), targetDate, null, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(context);
        Assert.assertEquals(invoices.size(), 1);

        return invoice.getId();
    }

    public SubscriptionBase createSubscription() throws SubscriptionBaseApiException {
        final UUID uuid = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(uuid);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);
        Mockito.when(subscriptionApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        return subscription;
    }

    public Account createAccount(final CallContext callContext) throws AccountApiException {
        final Account accountData = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                                            .firstNameLength(6)
                                                            .email(UUID.randomUUID().toString().substring(1, 8))
                                                            .phone(UUID.randomUUID().toString().substring(1, 8))
                                                            .migrated(false)
                                                            .isNotifiedForInvoices(true)
                                                            .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                                            .billingCycleDayLocal(31)
                                                            .currency(accountCurrency)
                                                            .paymentMethodId(UUID.randomUUID())
                                                            .timeZone(DateTimeZone.UTC)
                                                            .createdDate(clock.getUTCNow())
                                                            .build();

        final Account account;
        if (isFastTest()) {
            account = GuicyKillbillTestSuiteNoDB.createMockAccount(accountData, accountUserApi, accountApi, immutableAccountApi, nonEntityDao, clock, internalCallContextFactory, callContext, internalCallContext);
        } else {
            account = accountUserApi.createAccount(accountData, callContext);
        }

        GuicyKillbillTestSuite.refreshCallContext(account.getId(), clock, internalCallContextFactory, callContext, internalCallContext);

        return account;
    }

    public void createInvoiceItem(final InvoiceItem invoiceItem, final InternalCallContext internalCallContext) throws EntityPersistenceException {
        invoiceItemSqlDao.create(new InvoiceItemModelDao(invoiceItem), internalCallContext);
    }

    public InvoiceItemModelDao getInvoiceItemById(final UUID invoiceItemId, final InternalCallContext internalCallContext) {
        return invoiceItemSqlDao.getById(invoiceItemId.toString(), internalCallContext);
    }

    public List<InvoiceItemModelDao> getInvoiceItemBySubscriptionId(final UUID subscriptionId, final InternalCallContext internalCallContext) {
        return invoiceItemSqlDao.getInvoiceItemsBySubscription(subscriptionId.toString(), internalCallContext);
    }

    public List<InvoiceItemModelDao> getInvoiceItemByAccountId(final InternalCallContext internalCallContext) {
        return invoiceItemSqlDao.getByAccountRecordId(internalCallContext);
    }

    public List<InvoiceItemModelDao> getInvoiceItemByInvoiceId(final UUID invoiceId, final InternalCallContext internalCallContext) {
        return invoiceItemSqlDao.getInvoiceItemsByInvoice(invoiceId.toString(), internalCallContext);
    }

    public void createInvoice(final Invoice invoice, final InternalCallContext internalCallContext) throws EntityPersistenceException {
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        final List<InvoiceItemModelDao> invoiceItemModelDaos = ImmutableList.<InvoiceItemModelDao>copyOf(Collections2.transform(invoice.getInvoiceItems(),
                                                                                                                                new Function<InvoiceItem, InvoiceItemModelDao>() {
                                                                                                                                    @Override
                                                                                                                                    public InvoiceItemModelDao apply(final InvoiceItem input) {
                                                                                                                                        return new InvoiceItemModelDao(input);
                                                                                                                                    }
                                                                                                                                }));
        invoiceSqlDao.create(invoiceModelDao, internalCallContext);

        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            createInvoiceItem(invoiceItem, internalCallContext);
        }
    }

    public void createPayment(final InvoicePayment invoicePayment, final InternalCallContext internalCallContext) {
        try {
            invoicePaymentSqlDao.create(new InvoicePaymentModelDao(invoicePayment), internalCallContext);
        } catch (final EntityPersistenceException e) {
            Assert.fail(e.getMessage());
        }
    }

    public void verifyInvoice(final UUID invoiceId, final double balance, final double cbaAmount, final InternalTenantContext context) throws InvoiceApiException {
        final InvoiceModelDao invoice = invoiceDao.getById(invoiceId, context);
        Assert.assertEquals(InvoiceModelDaoHelper.getRawBalanceForRegularInvoice(invoice).doubleValue(), balance);
        Assert.assertEquals(InvoiceModelDaoHelper.getCBAAmount(invoice).doubleValue(), cbaAmount);
    }

    public void checkInvoicesEqual(final InvoiceModelDao retrievedInvoice, final Invoice invoice) {
        Assert.assertEquals(retrievedInvoice.getId(), invoice.getId());
        Assert.assertEquals(retrievedInvoice.getAccountId(), invoice.getAccountId());
        Assert.assertEquals(retrievedInvoice.getCurrency(), invoice.getCurrency());
        Assert.assertEquals(retrievedInvoice.getInvoiceDate(), invoice.getInvoiceDate());
        Assert.assertEquals(retrievedInvoice.getTargetDate(), invoice.getTargetDate());
        Assert.assertEquals(retrievedInvoice.getInvoiceItems().size(), invoice.getInvoiceItems().size());
        Assert.assertEquals(retrievedInvoice.getInvoicePayments().size(), invoice.getPayments().size());
    }

    public LocalDate buildDate(final int year, final int month, final int day) {
        return new LocalDate(year, month, day);
    }

    public BillingEvent createMockBillingEvent(@Nullable final Account account, final SubscriptionBase subscription,
                                               final DateTime effectiveDate,
                                               final Plan plan, final PlanPhase planPhase,
                                               @Nullable final BigDecimal fixedPrice, @Nullable final BigDecimal recurringPrice,
                                               final Currency currency, final BillingPeriod billingPeriod,
                                               final int billCycleDayLocal,
                                               final BillingMode billingMode, final String description,
                                               final long totalOrdering,
                                               final SubscriptionBaseTransitionType type) {

        final Account mockAccount = Mockito.mock(Account.class);
        Mockito.when(mockAccount.getTimeZone()).thenReturn(DateTimeZone.UTC);
        return new BillingEvent() {
            @Override
            public int getBillCycleDayLocal() {
                return billCycleDayLocal;
            }

            @Override
            public SubscriptionBase getSubscription() {
                return subscription;
            }

            @Override
            public DateTime getEffectiveDate() {
                return effectiveDate;
            }

            @Override
            public PlanPhase getPlanPhase() {
                return planPhase;
            }

            @Override
            public Plan getPlan() {
                return plan;
            }

            @Override
            public BillingPeriod getBillingPeriod() {
                return billingPeriod;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public BigDecimal getFixedPrice() {
                return fixedPrice;
            }

            @Override
            public BigDecimal getRecurringPrice(DateTime effectiveDate) {
                return recurringPrice;
            }

            @Override
            public Currency getCurrency() {
                return currency;
            }

            @Override
            public SubscriptionBaseTransitionType getTransitionType() {
                return type;
            }

            @Override
            public Long getTotalOrdering() {
                return totalOrdering;
            }

            @Override
            public List<Usage> getUsages() {
                return Collections.emptyList();
            }

            @Override
            public int compareTo(final BillingEvent e1) {
                if (!getSubscription().getId().equals(e1.getSubscription().getId())) { // First order by subscription
                    return getSubscription().getId().compareTo(e1.getSubscription().getId());
                } else { // subscriptions are the same
                    if (!getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                        return getEffectiveDate().compareTo(e1.getEffectiveDate());
                    } else { // dates and subscriptions are the same
                        return getTotalOrdering().compareTo(e1.getTotalOrdering());
                    }
                }
            }
        };
    }

    public static class DryRunFutureDateArguments implements DryRunArguments {

        public DryRunFutureDateArguments() {
        }

        @Override
        public DryRunType getDryRunType() {
            return DryRunType.TARGET_DATE;
        }

        @Override
        public PlanPhaseSpecifier getPlanPhaseSpecifier() {
            return null;
        }

        @Override
        public SubscriptionEventType getAction() {
            return null;
        }

        @Override
        public UUID getSubscriptionId() {
            return null;
        }

        @Override
        public LocalDate getEffectiveDate() {
            return null;
        }

        @Override
        public UUID getBundleId() {
            return null;
        }

        @Override
        public BillingActionPolicy getBillingActionPolicy() {
            return null;
        }

        @Override
        public List<PlanPhasePriceOverride> getPlanPhasePriceOverrides() {
            return null;
        }
    }

    // Unfortunately, this helper is shared across fast and slow tests
    private boolean isFastTest() {
        return Mockito.mockingDetails(accountApi).isMock();
    }
}
