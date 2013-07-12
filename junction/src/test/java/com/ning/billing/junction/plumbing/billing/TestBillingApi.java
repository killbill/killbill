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

package com.ning.billing.junction.plumbing.billing;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.MockCatalog;
import com.ning.billing.catalog.api.BillingAlignment;
import com.ning.billing.catalog.api.CatalogApiException;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.InternationalPrice;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.subscription.api.SubscriptionTransitionType;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionState;
import com.ning.billing.subscription.api.user.SubscriptionBundle;
import com.ning.billing.junction.JunctionTestSuiteNoDB;
import com.ning.billing.junction.api.BlockingState;
import com.ning.billing.junction.api.Type;
import com.ning.billing.junction.dao.MockBlockingStateDao;
import com.ning.billing.mock.MockEffectiveSubscriptionEvent;
import com.ning.billing.mock.MockSubscription;
import com.ning.billing.util.api.TagApiException;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.events.EffectiveSubscriptionInternalEvent;
import com.ning.billing.util.svcapi.junction.BillingEvent;
import com.ning.billing.util.svcapi.junction.BillingEventSet;
import com.ning.billing.util.svcapi.junction.BillingModeType;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;
import com.ning.billing.util.tag.ControlTagType;
import com.ning.billing.util.tag.dao.MockTagDao;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestBillingApi extends JunctionTestSuiteNoDB {

    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private static final UUID eventId = new UUID(0L, 0L);
    private static final UUID subId = new UUID(1L, 0L);
    private static final UUID bunId = new UUID(2L, 0L);

    private List<EffectiveSubscriptionInternalEvent> effectiveSubscriptionTransitions;
    private Subscription subscription;
    private MockCatalog catalog;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception  {
        super.beforeMethod();
        final SubscriptionBundle bundle = Mockito.mock(SubscriptionBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bunId);
        final List<SubscriptionBundle> bundles = ImmutableList.<SubscriptionBundle>of(bundle);

        effectiveSubscriptionTransitions = new LinkedList<EffectiveSubscriptionInternalEvent>();

        final DateTime subscriptionStartDate = clock.getUTCNow().minusDays(3);
        subscription = new MockSubscription(subId, bunId, null, subscriptionStartDate, effectiveSubscriptionTransitions);
        final List<Subscription> subscriptions = ImmutableList.<Subscription>of(subscription);

        Mockito.when(entitlementInternalApi.getBundlesForAccount(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundles);
        Mockito.when(entitlementInternalApi.getSubscriptionsForBundle(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscriptions);
        Mockito.when(entitlementInternalApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(entitlementInternalApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundle);
        Mockito.when(entitlementInternalApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(entitlementInternalApi.getBillingTransitions(Mockito.<Subscription>any(), Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);
        Mockito.when(entitlementInternalApi.getAllTransitions(Mockito.<Subscription>any(), Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);

        catalog = ((MockCatalog) catalogService.getCurrentCatalog());
        // TODO The MockCatalog module returns two different things for full vs current catalog
        Mockito.when(catalogService.getFullCatalog()).thenReturn(catalog);
        // Set a default alignment
        catalog.setBillingAlignment(BillingAlignment.ACCOUNT);

        // Cleanup mock daos
        ((MockBlockingStateDao) blockingStateDao).clear();
        ((MockTagDao) tagDao).clear();
    }

    @Test(groups = "fast")
    public void testBillingEventsEmpty() throws AccountApiException {
        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L, 0L), internalCallContext);
        Assert.assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsNoBillingPeriod() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        // The trial has no billing period
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(10);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        checkFirstEvent(events, nextPlan, account.getBillCycleDayLocal(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsSubscriptionAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        catalog.setBillingAlignment(BillingAlignment.SUBSCRIPTION);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is when the subscription started since we skip the trial phase
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsAccountAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is the account BCD (account aligned by default)
        checkFirstEvent(events, nextPlan, 32, subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsBundleAligned() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalog.findPlan("Horn1USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        catalog.setBillingAlignment(BillingAlignment.BUNDLE);
        ((MockSubscription) subscription).setPlan(catalog.findPlan("PickupTrialEvergreen10USD", now));

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);
        // The expected BCD is when the subscription started
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsWithBlock() throws CatalogApiException, AccountApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final List<BlockingState> blockingStates = new ArrayList<BlockingState>();
        blockingStates.add(new DefaultBlockingState(UUID.randomUUID(), bunId, DISABLED_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", true, true, true, now.plusDays(1), null));
        blockingStates.add(new DefaultBlockingState(UUID.randomUUID(), bunId, CLEAR_BUNDLE, Type.SUBSCRIPTION_BUNDLE, "test", false, false, false, now.plusDays(2), null));

        ((MockBlockingStateDao) blockingStateDao).setBlockingStates(bunId, blockingStates);
        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

        Assert.assertEquals(events.size(), 3);
        final Iterator<BillingEvent> it = events.iterator();

        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now, nextPhase, SubscriptionTransitionType.CREATE.toString(), nextPhase.getFixedPrice(), nextPhase.getRecurringPrice());
        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now.plusDays(1), nextPhase, SubscriptionTransitionType.START_BILLING_DISABLED.toString(), null, null);
        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now.plusDays(2), nextPhase, SubscriptionTransitionType.END_BILLING_DISABLED.toString(), nextPhase.getFixedPrice(), nextPhase.getRecurringPrice());
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffAccount() throws CatalogApiException, AccountApiException, TagApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        tagInternalApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), internalCallContext);

        final BillingEventSet events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

        assertEquals(events.isAccountAutoInvoiceOff(), true);
        assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffBundle() throws CatalogApiException, AccountApiException, TagApiException {
        final Plan nextPlan = catalog.findPlan("PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        tagInternalApi.addTag(bunId, ObjectType.BUNDLE, ControlTagType.AUTO_INVOICING_OFF.getId(), internalCallContext);

        final BillingEventSet events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), internalCallContext);

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

        Assert.assertEquals(BCD, event.getBillCycleDayLocal());
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
        Mockito.when(account.getBillCycleDayLocal()).thenReturn(billCycleDay);
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        return account;
    }

    private DateTime createSubscriptionCreationEvent(final Plan nextPlan, final PlanPhase nextPhase) throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime then = now.minusDays(1);
        final PriceList nextPriceList = catalog.findPriceList(PriceListSet.DEFAULT_PRICELIST_NAME, now);

        final EffectiveSubscriptionInternalEvent t = new MockEffectiveSubscriptionEvent(
                eventId, subId, bunId, then, now, null, null, null, null, SubscriptionState.ACTIVE,
                nextPlan.getName(), nextPhase.getName(),
                nextPriceList.getName(), 1L,
                SubscriptionTransitionType.CREATE,  1, null, 1L, 2L, null);

        effectiveSubscriptionTransitions.add(t);
        return now;
    }
}
