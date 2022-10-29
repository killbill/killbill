/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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
import org.killbill.billing.callcontext.MutableCallContext;
import org.killbill.billing.callcontext.MutableInternalCallContext;
import org.killbill.billing.catalog.MockPlan;
import org.killbill.billing.catalog.MockPlanPhase;
import org.killbill.billing.catalog.api.BillingActionPolicy;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingMode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.Usage;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.SubscriptionEventType;
import org.killbill.billing.entity.EntityPersistenceException;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.invoice.api.DryRunType;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
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
import org.killbill.billing.invoice.optimizer.InvoiceOptimizer;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.BillingInternalApi;
import org.killbill.billing.junction.plumbing.billing.DefaultBillingEvent;
import org.killbill.billing.mock.MockAccountBuilder;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseInternalApi;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.currency.KillBillMoney;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.optimizer.BusOptimizer;
import org.killbill.clock.Clock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.notificationq.api.NotificationQueueService;
import org.mockito.Mockito;
import org.skife.jdbi.v2.IDBI;
import org.testng.Assert;

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
    private final BusOptimizer eventBus;
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
    private final InvoiceOptimizer invoiceOptimizer;

    @Inject
    public TestInvoiceHelper(final InvoiceGenerator generator, final IDBI dbi,
                             final BillingInternalApi billingApi, final AccountInternalApi accountApi, final ImmutableAccountInternalApi immutableAccountApi, final InvoicePluginDispatcher invoicePluginDispatcher, final AccountUserApi accountUserApi, final SubscriptionBaseInternalApi subscriptionApi, final BusOptimizer eventBus,
                             final InvoiceDao invoiceDao, final GlobalLocker locker, final Clock clock, final NonEntityDao nonEntityDao, final NotificationQueueService notificationQueueService, final MutableInternalCallContext internalCallContext, final InvoiceConfig invoiceConfig,
                             final ParkedAccountsManager parkedAccountsManager, final InvoiceOptimizer invoiceOptimizer, final InternalCallContextFactory internalCallContextFactory) {
        this.generator = generator;
        this.billingApi = billingApi;
        this.accountApi = accountApi;
        this.immutableAccountApi = immutableAccountApi;
        this.invoicePluginDispatcher = invoicePluginDispatcher;
        this.accountUserApi = accountUserApi;
        this.subscriptionApi = subscriptionApi;
        this.eventBus = eventBus;
        this.invoiceDao = invoiceDao;
        this.locker = locker;
        this.clock = clock;
        this.nonEntityDao = nonEntityDao;
        this.notificationQueueService = notificationQueueService;
        this.parkedAccountsManager = parkedAccountsManager;
        this.invoiceOptimizer = invoiceOptimizer;
        this.internalCallContext = internalCallContext;
        this.internalCallContextFactory = internalCallContextFactory;
        this.invoiceSqlDao = dbi.onDemand(InvoiceSqlDao.class);
        this.invoiceItemSqlDao = dbi.onDemand(InvoiceItemSqlDao.class);
        this.invoicePaymentSqlDao = dbi.onDemand(InvoicePaymentSqlDao.class);
        this.invoiceConfig = invoiceConfig;
    }

    public UUID generateRegularInvoice(final Account account, final LocalDate targetDate, final CallContext callContext) throws Exception {
        final BigDecimal fixedPrice = null;
        final BigDecimal recurringPrice = BigDecimal.ONE;
        return generateRegularInvoice(account, fixedPrice, recurringPrice, targetDate, callContext);
    }

    public UUID generateRegularInvoice(final Account account, final BigDecimal fixedPrice, final BigDecimal recurringPrice, final LocalDate targetDate, final CallContext callContext) throws Exception {
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(subscription.getBundleId()).thenReturn(new UUID(0L, 0L));
        final BillingEventSet events = new MockBillingEventSet();
        final Plan plan = MockPlan.createBicycleNoTrialEvergreen1USD();
        final PlanPhase planPhase = MockPlanPhase.create1USDMonthlyEvergreen();
        final DateTime effectiveDate = new DateTime().minusDays(1);
        final Currency currency = Currency.USD;
        events.add(createMockBillingEvent(account, subscription, effectiveDate, plan, planPhase,
                                          fixedPrice, recurringPrice, currency, BillingPeriod.MONTHLY, 1,
                                          BillingMode.IN_ADVANCE, "", 1L, SubscriptionBaseTransitionType.CREATE));

        Mockito.when(billingApi.getBillingEventsForAccountAndUpdateAccountBCD(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<LocalDate>any(), Mockito.<InternalCallContext>any())).thenReturn(events);

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(account.getId(), callContext);

        Invoice invoice = generateInvoice(account.getId(), targetDate, new DryRunFutureDateArguments(), context);
        Assert.assertNotNull(invoice);

        List<InvoiceModelDao> invoices = invoiceDao.getInvoicesByAccount(false, true, context);
        Assert.assertEquals(invoices.size(), 0);

        invoice = generateInvoice(account.getId(), targetDate, null, context);
        Assert.assertNotNull(invoice);

        invoices = invoiceDao.getInvoicesByAccount(false, true, context);
        Assert.assertEquals(invoices.size(), 1);

        return invoice.getId();
    }

    public Invoice generateInvoice(final UUID accountId, @Nullable final LocalDate targetDate, @Nullable final DryRunArguments dryRunArguments, final InternalCallContext internalCallContext) throws InvoiceApiException {
        final InvoiceDispatcher dispatcher = new InvoiceDispatcher(generator, accountApi, billingApi, subscriptionApi,
                                                                   invoiceDao, internalCallContextFactory, invoicePluginDispatcher, locker, eventBus,
                                                                   notificationQueueService, invoiceConfig, clock, invoiceOptimizer, parkedAccountsManager);

        final List<Invoice> result = dispatcher.processAccountFromNotificationOrBusEvent(accountId, targetDate, dryRunArguments, false, internalCallContext);
        Assert.assertEquals(result.size(), 1);
        return result.get(0);
    }

    public SubscriptionBase createSubscription() throws SubscriptionBaseApiException {
        final UUID uuid = UUID.randomUUID();
        final UUID bundleId = UUID.randomUUID();
        final SubscriptionBase subscription = Mockito.mock(SubscriptionBase.class);
        Mockito.when(subscription.getId()).thenReturn(uuid);
        Mockito.when(subscription.getBundleId()).thenReturn(bundleId);
        Mockito.when(subscriptionApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.eq(false), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        return subscription;
    }

    public Account createAccount(final CallContext callContext) throws AccountApiException {
        final Account accountData = new MockAccountBuilder().name(UUID.randomUUID().toString().substring(1, 8))
                                                            .firstNameLength(6)
                                                            .email(UUID.randomUUID().toString().substring(1, 8))
                                                            .phone(UUID.randomUUID().toString().substring(1, 8))
                                                            .migrated(false)
                                                            .externalKey(UUID.randomUUID().toString().substring(1, 8))
                                                            .billingCycleDayLocal(31)
                                                            .currency(accountCurrency)
                                                            .paymentMethodId(UUID.randomUUID())
                                                            .timeZone(DateTimeZone.UTC)
                                                            .createdDate(clock.getUTCNow())

                                                            .build();
        final MutableCallContext mutableCallContext = new MutableCallContext(internalCallContext);

        final Account account;
        if (isFastTest()) {
            account = GuicyKillbillTestSuiteNoDB.createMockAccount(accountData, accountUserApi, accountApi, immutableAccountApi, nonEntityDao, clock, internalCallContextFactory, mutableCallContext, internalCallContext);
        } else {
            account = accountUserApi.createAccount(accountData, callContext);
        }

        GuicyKillbillTestSuite.refreshCallContext(account.getId(), clock, internalCallContextFactory, mutableCallContext, internalCallContext);

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
        return invoiceItemSqlDao.getInvoiceItemsForInvoices(List.of(invoiceId), internalCallContext);
    }

    public void createInvoice(final Invoice invoice, final InternalCallContext internalCallContext) throws EntityPersistenceException {
        final InvoiceModelDao invoiceModelDao = new InvoiceModelDao(invoice);
        invoiceSqlDao.create(invoiceModelDao, internalCallContext);

        for (final InvoiceItem invoiceItem : invoice.getInvoiceItems()) {
            createInvoiceItem(invoiceItem, internalCallContext);
        }
    }

    public void createPayment(final InvoicePayment invoicePayment, final InternalCallContext internalCallContext) {
        invoicePaymentSqlDao.create(new InvoicePaymentModelDao(invoicePayment), internalCallContext);
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
            public UUID getSubscriptionId() {
                return subscription.getId();
            }

            @Override
            public UUID getBundleId() {
                return subscription.getBundleId();
            }

            @Override
            public int getBillCycleDayLocal() {
                return billCycleDayLocal;
            }

            @Override
            public BillingAlignment getBillingAlignment() {
                return null;
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
            public BigDecimal getRecurringPrice() {
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
            public DateTime getCatalogEffectiveDate() {
                return null;
            }

            @Override
            public int compareTo(final BillingEvent e1) {
                if (!getSubscriptionId().equals(e1.getSubscriptionId())) { // First order by subscription
                    return getSubscriptionId().compareTo(e1.getSubscriptionId());
                } else { // subscriptions are the same
                    if (!getEffectiveDate().equals(e1.getEffectiveDate())) { // Secondly order by date
                        return getEffectiveDate().compareTo(e1.getEffectiveDate());
                    } else { // dates and subscriptions are the same
                        return getTotalOrdering().compareTo(e1.getTotalOrdering());
                    }
                }
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                final DefaultBillingEvent that = (DefaultBillingEvent) o;

                if (getSubscriptionId() != null ? !getSubscriptionId().equals(that.getSubscriptionId()) : that.getSubscriptionId() != null) {
                    return false;
                }
                if (getBundleId() != null ? !getBundleId().equals(that.getBundleId()) : that.getBundleId() != null) {
                    return false;
                }
                if (billCycleDayLocal != that.getBillCycleDayLocal()) {
                    return false;
                }
                if (billingPeriod != that.getBillingPeriod()) {
                    return false;
                }
                if (currency != that.getCurrency()) {
                    return false;
                }
                if (fixedPrice != null ? !fixedPrice.equals(that.getFixedPrice()) : that.getFixedPrice() != null) {
                    return false;
                }
                if (description != null ? !description.equals(that.getDescription()) : that.getDescription() != null) {
                    return false;
                }
                if (effectiveDate != null ? !effectiveDate.equals(that.getEffectiveDate()) : that.getEffectiveDate() != null) {
                    return false;
                }
                if (plan != null ? !plan.equals(that.getPlan()) : that.getPlan() != null) {
                    return false;
                }
                if (planPhase != null ? !planPhase.equals(that.getPlanPhase()) : that.getPlanPhase() != null) {
                    return false;
                }
                if (getTotalOrdering() != null ? !getTotalOrdering().equals(that.getTotalOrdering()) : that.getTotalOrdering() != null) {
                    return false;
                }
                if (type != that.getTransitionType()) {
                    return false;
                }

                return true;
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
        public EntitlementSpecifier getEntitlementSpecifier() {
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
    }

    // Unfortunately, this helper is shared across fast and slow tests
    private boolean isFastTest() {
        return Mockito.mockingDetails(accountApi).isMock();
    }
}
