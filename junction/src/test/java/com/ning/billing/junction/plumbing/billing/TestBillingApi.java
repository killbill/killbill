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

package com.ning.billing.junction.plumbing.billing;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.MockCatalogService;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.CatalogService;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.SubscriptionTransitionType;
import com.ning.billing.entitlement.api.user.EntitlementUserApiException;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.Subscription.SubscriptionState;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.junction.JunctionTestSuite;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.lifecycle.KillbillService.ServiceException;
import com.ning.billing.mock.MockEffectiveSubscriptionEvent;
import com.ning.billing.mock.MockSubscription;
import com.ning.billing.mock.api.MockBillCycleDay;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.clock.Clock;
import com.ning.billing.util.clock.ClockMock;
import com.ning.billing.util.dao.ObjectType;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.svcapi.entitlement.EntitlementInternalApi;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingInternalApi;
import com.ning.billing.util.svcapi.junction.BillingModeType;
import com.ning.billing.util.svcapi.junction.BlockingInternalApi;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import com.ning.billing.util.svcapi.tag.TagInternalApi;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.Tag;

public class TestBillingApi extends JunctionTestSuite {

    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private static final UUID eventId = new UUID(0L, 0L);
    private static final UUID subId = new UUID(1L, 0L);
    private static final UUID bunId = new UUID(2L, 0L);

    private CatalogService catalogService;

    private List<EffectiveSubscriptionInternalEvent> effectiveSubscriptionTransitions;
    private EntitlementInternalApi entitlementApi;

    private final BlockingCalculator blockCalculator = new BlockingCalculator(null) {
        @Override
        public void insertBlockingEvents(final SortedSet<BillingEvent> billingEvents, final InternalTenantContext context) {
        }
    };

    private Clock clock;

    private AccountInternalApi accountApi;
    private BillCycleDayCalculator bcdCalculator;
    private InternalCallContextFactory factory;
    private BillingInternalApi api;
    private Subscription subscription;
    private TagInternalApi tagApi;

    @BeforeSuite(groups = "fast")
    public void setup() throws ServiceException {
        catalogService = new MockCatalogService(new MockCatalog());
        clock = new ClockMock();
    }

    @BeforeMethod(groups = "fast")
    public void setupEveryTime() throws EntitlementUserApiException {
        accountApi = Mockito.mock(AccountInternalApi.class);

        final List<SubscriptionBundle> bundles = new ArrayList<SubscriptionBundle>();
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bunId);

        bundles.add(bundle);

        effectiveSubscriptionTransitions = new LinkedList<EffectiveSubscriptionInternalEvent>();
        final List<Subscription> subscriptions = new LinkedList<Subscription>();

        final DateTime subscriptionStartDate = clock.getUTCNow().minusDays(3);
        subscription = new MockSubscription(subId, bunId, null, subscriptionStartDate, effectiveSubscriptionTransitions);

        subscriptions.add(subscription);

        entitlementApi = Mockito.mock(EntitlementInternalApi.class);
        Mockito.when(entitlementApi.getBundlesForAccount(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundles);
        Mockito.when(entitlementApi.getSubscriptionsForBundle(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscriptions);
        Mockito.when(entitlementApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(entitlementApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundle);
        Mockito.when(entitlementApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(entitlementApi.getBillingTransitions(Mockito.<Subscription>any(), Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);
        Mockito.when(entitlementApi.getAllTransitions(Mockito.<Subscription>any(), Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);
        tagApi = mock(TagInternalApi.class);

        bcdCalculator = new BillCycleDayCalculator(catalogService, entitlementApi);

        api = new DefaultInternalBillingApi(accountApi, bcdCalculator, entitlementApi, blockCalculator, catalogService, tagApi);

        // Set a default alignment
        ((MockCatalog) catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.ACCOUNT);
    }

    @Test(groups = "fast")
    public void testBillingEventsEmpty() throws AccountApiException {
        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L, 0L), internalCallContext);
        Assert.assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsNoBillingPeriod() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        // The trial has no billing period
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(10);

        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        checkFirstEvent(events, nextPlan, account.getBillCycleDay().getDayOfMonthUTC(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsSubscriptionAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        ((MockCatalog) catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.SUBSCRIPTION);

        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is when the subscription started since we skip the trial phase
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsAccountAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is the account BCD (account aligned by default)
        checkFirstEvent(events, nextPlan, 32, subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsBundleAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("Horn1USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        ((MockCatalog) catalogService.getFullCatalog()).setBillingAlignment(BillingAlignment.BUNDLE);
        ((MockSubscription) subscription).setPlan(catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", now));

        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is when the subscription started
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsWithBlock() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();
        blockingStates.add(new DefaultBlockingState(bunId, DISABLED_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1)));
        blockingStates.add(new DefaultBlockingState(bunId, CLEAR_BUNDLE, Blockable.Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2)));

        final BlockingCalculator blockingCal = new BlockingCalculator(new BlockingInternalApi() {
            @Override
            public <T extends Blockable> void setBlockingState(final BlockingState state, final InternalCallContext context) {
            }

            @Override
            public BlockingState getBlockingStateFor(final UUID overdueableId, final InternalTenantContext context) {
                return null;
            }

            @Override
            public BlockingState getBlockingStateFor(final Blockable overdueable, final InternalTenantContext context) {
                return null;
            }

            @Override
            public List<BlockingState> getBlockingHistory(final UUID overdueableId, final InternalTenantContext context) {
                if (overdueableId == bunId) {
                    return blockingStates;
                }
                return new ArrayList<BlockingState>();
            }

            @Override
            public List<BlockingState> getBlockingHistory(final Blockable overdueable, final InternalTenantContext context) {
                return new ArrayList<BlockingState>();
            }
        });

        final BillingInternalApi api = new DefaultInternalBillingApi(accountApi, bcdCalculator, entitlementApi, blockingCal, catalogService, tagApi);
        final SortedSet<BillingEvent> events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

        Assert.assertEquals(events.size(), 3);
        final Iterator<BillingEvent> it = events.iterator();

        checkEvent(it.next(), nextPlan, account.getBillCycleDay().getDayOfMonthUTC(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString(), nextPhase.getFixedPrice(), nextPhase.getRecurringPrice());
        checkEvent(it.next(), nextPlan, account.getBillCycleDay().getDayOfMonthUTC(), subId, now.plusDays(1), nextPhase, SubscriptionTransitionType.START_BILLING_DISABLED.toString(), null, null);
        checkEvent(it.next(), nextPlan, account.getBillCycleDay().getDayOfMonthUTC(), subId, now.plusDays(2), nextPhase, SubscriptionTransitionType.END_BILLING_DISABLED.toString(), nextPhase.getFixedPrice(), nextPhase.getRecurringPrice());
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffAccount() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final Map<String, Tag> tags = new HashMap<String, Tag>();
        final Tag aioTag = mock(Tag.class);
        when(aioTag.getTagDefinitionId()).thenReturn(ControlTagType.AUTO_INVOICING_OFF.getId());
        tags.put(ControlTagType.AUTO_INVOICING_OFF.name(), aioTag);
        when(tagApi.getTags(account.getId(), ObjectType.ACCOUNT, internalCallContext)).thenReturn(tags);
        assertEquals(tagApi.getTags(account.getId(), ObjectType.ACCOUNT, internalCallContext), tags);

        final BillingEventSet events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

        assertEquals(events.isAccountAutoInvoiceOff(), true);
        assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffBundle() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalogService.getFullCatalog().findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final Map<String, Tag> tags = new HashMap<String, Tag>();
        final Tag aioTag = mock(Tag.class);
        when(aioTag.getTagDefinitionId()).thenReturn(ControlTagType.AUTO_INVOICING_OFF.getId());
        tags.put(ControlTagType.AUTO_INVOICING_OFF.name(), aioTag);
        when(tagApi.getTags(bunId, ObjectType.BUNDLE, internalCallContext)).thenReturn(tags);

        final BillingEventSet events = api.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

        assertEquals(events.getSubscriptionIdsWithAutoInvoiceOff().size(), 1);
        assertEquals(events.getSubscriptionIdsWithAutoInvoiceOff().get(0), subId);
        assertEquals(events.size(), 0);
    }

    private void checkFirstEvent(final SortedSet<BillingEvent> events, final Plan nextPlan,
                                 final int BCD, final UUID id, final DateTime time, final PlanPhase nextPhase, final String desc) throws CatalogApiException {
        Assert.assertEquals(events.size(), 1);
        checkEvent(events.first(), nextPlan, BCD, id, time, nextPhase, desc, nextPhase.getFixedPrice(), nextPhase.getRecurringPrice());
    }

    private void checkEvent(final BillingEvent event, final Plan nextPlan, final int BCD, final UUID id, final DateTime time,
                            final PlanPhase nextPhase, final String desc, final InternationalPrice fixedPrice, final InternationalPrice recurringPrice) throws CatalogApiException {
        if (fixedPrice != null) {
            Assert.assertEquals(fixedPrice.getPrice(Currency.USD), event.getFixedPrice());
        } else {
            assertNull(event.getFixedPrice());
        }

        if (recurringPrice != null) {
            Assert.assertEquals(recurringPrice.getPrice(Currency.USD), event.getRecurringPrice());
        } else {
            assertNull(event.getRecurringPrice());
        }

        Assert.assertEquals(BCD, event.getBillCycleDay().getDayOfMonthUTC());
        Assert.assertEquals(id, event.getSubscription().getId());
        Assert.assertEquals(time.getDayOfMonth(), event.getEffectiveDate().getDayOfMonth());
        Assert.assertEquals(nextPhase, event.getPlanPhase());
        Assert.assertEquals(nextPlan, event.getPlan());
        if (!SubscriptionTransitionType.START_BILLING_DISABLED.equals(event.getTransitionType())) {
            Assert.assertEquals(nextPhase.getBillingPeriod(), event.getBillingPeriod());
        }
        Assert.assertEquals(BillingModeType.IN_ADVANCE, event.getBillingMode());
        Assert.assertEquals(desc, event.getTransitionType().toString());
    }

    private Account createAccount(final int billCycleDay) throws AccountApiException {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getBillCycleDay()).thenReturn(new MockBillCycleDay(billCycleDay));
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        return account;
    }

    private DateTime createSubscriptionCreationEvent(final Plan nextPlan, final PlanPhase nextPhase) throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime then = now.minusDays(1);
        final PriceList nextPriceList = catalogService.getFullCatalog().findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);

        final EffectiveSubscriptionInternalEvent t = new MockEffectiveSubscriptionEvent(
                eventId, subId, bunId, then, now, null, null, null, null, SubscriptionState.ACTIVE,
                nextPlan.getName(), nextPhase.getName(),
                nextPriceList.getName(), 1L, null,
                SubscriptionTransitionType.CREATE, 0, null);

        effectiveSubscriptionTransitions.add(t);
        return now;
    }
}
