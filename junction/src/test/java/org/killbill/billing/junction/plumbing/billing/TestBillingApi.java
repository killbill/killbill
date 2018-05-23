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

package org.killbill.billing.junction.plumbing.billing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.killbill.billing.ObjectType;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.InternationalPrice;
import org.killbill.billing.catalog.api.Plan;
import org.killbill.billing.catalog.api.PlanPhase;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.entitlement.dao.MockBlockingStateDao;
import org.killbill.billing.events.EffectiveSubscriptionInternalEvent;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.junction.DefaultBlockingState;
import org.killbill.billing.junction.JunctionTestSuiteNoDB;
import org.killbill.billing.mock.MockEffectiveSubscriptionEvent;
import org.killbill.billing.mock.MockSubscription;
import org.killbill.billing.subscription.api.SubscriptionBase;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;
import org.killbill.billing.subscription.api.user.SubscriptionBaseBundle;
import org.killbill.billing.util.api.TagApiException;
import org.killbill.billing.util.tag.ControlTagType;
import org.killbill.billing.util.tag.dao.MockTagDao;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestBillingApi extends JunctionTestSuiteNoDB {

    private static final String DISABLED_BUNDLE = "disabled-bundle";
    private static final String CLEAR_BUNDLE = "clear-bundle";

    private static final UUID eventId = new UUID(0L, 0L);
    private static final UUID subId = new UUID(1L, 0L);
    private static final UUID bunId = new UUID(2L, 0L);
    private static final String bunKey = bunId.toString();

    private List<EffectiveSubscriptionInternalEvent> effectiveSubscriptionTransitions;
    private SubscriptionBase subscription;
    private MockCatalog catalog;

    @BeforeMethod(groups = "fast")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final SubscriptionBaseBundle bundle = Mockito.mock(SubscriptionBaseBundle.class);
        Mockito.when(bundle.getId()).thenReturn(bunId);
        final List<SubscriptionBaseBundle> bundles = ImmutableList.<SubscriptionBaseBundle>of(bundle);

        effectiveSubscriptionTransitions = new LinkedList<EffectiveSubscriptionInternalEvent>();

        final DateTime subscriptionStartDate = clock.getUTCNow().minusDays(3);
        subscription = new MockSubscription(subId, bunId, null, subscriptionStartDate, subscriptionStartDate);
        final List<SubscriptionBase> subscriptions = ImmutableList.<SubscriptionBase>of(subscription);

        Mockito.when(subscriptionInternalApi.getBundlesForAccount(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundles);
        Mockito.when(subscriptionInternalApi.getSubscriptionsForBundle(Mockito.<UUID>any(), Mockito.<DryRunArguments>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscriptions);
        Mockito.when(subscriptionInternalApi.getSubscriptionsForAccount(Mockito.<Catalog>any(), Mockito.<InternalTenantContext>any())).thenReturn(ImmutableMap.<UUID, List<SubscriptionBase>>builder()
                                                                                                                                                          .put(bunId, subscriptions)
                                                                                                                                                          .build());
        Mockito.when(subscriptionInternalApi.getSubscriptionFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(subscriptionInternalApi.getBundleFromId(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(bundle);
        Mockito.when(subscriptionInternalApi.getBaseSubscription(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(subscription);
        Mockito.when(subscriptionInternalApi.getBillingTransitions(Mockito.<SubscriptionBase>any(),  Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);
        Mockito.when(subscriptionInternalApi.getAllTransitions(Mockito.<SubscriptionBase>any(), Mockito.<InternalTenantContext>any())).thenReturn(effectiveSubscriptionTransitions);

        catalog = ((MockCatalog) catalogService.getCurrentCatalog(true, true, internalCallContext));
        Mockito.when(catalogService.getFullCatalog(true, true, internalCallContext)).thenReturn(catalog);

        Mockito.when(catalogInternalApi.getFullCatalog(true, true, internalCallContext)).thenReturn(catalog);
        Mockito.when(catalogInternalApi.getCurrentCatalog(true, true, internalCallContext)).thenReturn(catalog);
        // Set a default alignment
        catalog.setBillingAlignment(BillingAlignment.ACCOUNT);

        // Cleanup mock daos
        ((MockBlockingStateDao) blockingStateDao).clear();
        ((MockTagDao) tagDao).clear();
    }

    @Test(groups = "fast")
    public void testBillingEventsEmpty() throws AccountApiException, CatalogApiException, SubscriptionBaseApiException {
        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(new UUID(0L, 0L), null, internalCallContext);
        Assert.assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsNoBillingPeriod() throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        // The trial has no billing period
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(10);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);
        checkFirstEvent(events, nextPlan, account.getBillCycleDayLocal(), subId, now, nextPhase, SubscriptionBaseTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsSubscriptionAligned() throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        catalog.setBillingAlignment(BillingAlignment.SUBSCRIPTION);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);
        // The expected BCD is when the subscription started since we skip the trial phase
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionBaseTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsAccountAligned() throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);
        // The expected BCD is the account BCD (account aligned by default)
        checkFirstEvent(events, nextPlan, 32, subId, now, nextPhase, SubscriptionBaseTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsBundleAligned() throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("7-Horn1USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[0];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(1);

        catalog.setBillingAlignment(BillingAlignment.BUNDLE);
        ((MockSubscription) subscription).setPlan(catalog.findPlan("3-PickupTrialEvergreen10USD", now));

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);
        // The expected BCD is when the subscription started
        checkFirstEvent(events, nextPlan, subscription.getStartDate().getDayOfMonth(), subId, now, nextPhase, SubscriptionBaseTransitionType.CREATE.toString());
    }

    @Test(groups = "fast")
    public void testBillingEventsWithBlock() throws CatalogApiException, AccountApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        final DateTime now = createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        final BlockingState blockingState1 = new DefaultBlockingState(bunId, BlockingStateType.SUBSCRIPTION_BUNDLE, DISABLED_BUNDLE, "test", true, true, true, now.plusDays(1));
        final BlockingState blockingState2 = new DefaultBlockingState(bunId, BlockingStateType.SUBSCRIPTION_BUNDLE, CLEAR_BUNDLE, "test", false, false, false, now.plusDays(2));
        blockingStateDao.setBlockingStatesAndPostBlockingTransitionEvent(ImmutableMap.<BlockingState, Optional<UUID>>of(blockingState1, Optional.<UUID>absent(), blockingState2, Optional.<UUID>absent()), internalCallContext);

        final SortedSet<BillingEvent> events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);

        Assert.assertEquals(events.size(), 3);
        final Iterator<BillingEvent> it = events.iterator();

        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now, nextPhase, SubscriptionBaseTransitionType.CREATE.toString(), nextPhase.getFixed().getPrice(), nextPhase.getRecurring().getRecurringPrice());
        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now.plusDays(1), nextPhase, SubscriptionBaseTransitionType.START_BILLING_DISABLED.toString(), null, null);
        checkEvent(it.next(), nextPlan, account.getBillCycleDayLocal(), subId, now.plusDays(2), nextPhase, SubscriptionBaseTransitionType.END_BILLING_DISABLED.toString(), nextPhase.getFixed().getPrice(), nextPhase.getRecurring().getRecurringPrice());
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffAccount() throws CatalogApiException, AccountApiException, TagApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        tagInternalApi.addTag(account.getId(), ObjectType.ACCOUNT, ControlTagType.AUTO_INVOICING_OFF.getId(), internalCallContext);

        final BillingEventSet events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);

        assertEquals(events.isAccountAutoInvoiceOff(), true);
        assertEquals(events.size(), 0);
    }

    @Test(groups = "fast")
    public void testBillingEventsAutoInvoicingOffBundle() throws CatalogApiException, AccountApiException, TagApiException, SubscriptionBaseApiException {
        final Plan nextPlan = catalog.findPlan("3-PickupTrialEvergreen10USD", clock.getUTCNow());
        final PlanPhase nextPhase = nextPlan.getAllPhases()[1];
        createSubscriptionCreationEvent(nextPlan, nextPhase);

        final Account account = createAccount(32);

        tagInternalApi.addTag(bunId, ObjectType.BUNDLE, ControlTagType.AUTO_INVOICING_OFF.getId(), internalCallContext);

        final BillingEventSet events = billingInternalApi.getBillingEventsForAccountAndUpdateAccountBCD(account.getId(), null, internalCallContext);

        assertEquals(events.getSubscriptionIdsWithAutoInvoiceOff().size(), 1);
        assertEquals(events.getSubscriptionIdsWithAutoInvoiceOff().get(0), subId);
        assertEquals(events.size(), 0);
    }

    private void checkFirstEvent(final SortedSet<BillingEvent> events, final Plan nextPlan,
                                 final int BCD, final UUID id, final DateTime time, final PlanPhase nextPhase, final String desc) throws CatalogApiException {
        Assert.assertEquals(events.size(), 1);
        checkEvent(events.first(), nextPlan, BCD, id, time, nextPhase, desc, nextPhase.getFixed().getPrice(), nextPhase.getRecurring().getRecurringPrice());
    }

    private void checkEvent(final BillingEvent event, final Plan nextPlan, final int BCD, final UUID id, final DateTime time,
                            final PlanPhase nextPhase, final String desc, final InternationalPrice fixedPrice, final InternationalPrice recurringPrice) throws CatalogApiException {
        if (fixedPrice != null) {
            Assert.assertEquals(fixedPrice.getPrice(Currency.USD), event.getFixedPrice());
        } else {
            assertNull(event.getFixedPrice());
        }

        if (recurringPrice != null) {
            Assert.assertEquals(recurringPrice.getPrice(Currency.USD), event.getRecurringPrice(null));
        } else {
            assertNull(event.getRecurringPrice(null));
        }

        Assert.assertEquals(BCD, event.getBillCycleDayLocal());
        Assert.assertEquals(id, event.getSubscription().getId());
        Assert.assertEquals(time.getDayOfMonth(), event.getEffectiveDate().getDayOfMonth());
        Assert.assertEquals(nextPhase, event.getPlanPhase());
        Assert.assertEquals(nextPlan, event.getPlan());
        if (!SubscriptionBaseTransitionType.START_BILLING_DISABLED.equals(event.getTransitionType())) {
            Assert.assertEquals(nextPhase.getRecurring().getBillingPeriod(), event.getBillingPeriod());
        }
        Assert.assertEquals(desc, event.getTransitionType().toString());
    }

    private Account createAccount(final int billCycleDay) throws AccountApiException {
        final Account account = Mockito.mock(Account.class);
        Mockito.when(account.getBillCycleDayLocal()).thenReturn(billCycleDay);
        Mockito.when(account.getCurrency()).thenReturn(Currency.USD);
        Mockito.when(account.getId()).thenReturn(UUID.randomUUID());
        Mockito.when(account.getTimeZone()).thenReturn(DateTimeZone.UTC);
        Mockito.when(accountInternalApi.getAccountById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(accountInternalApi.getImmutableAccountDataById(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(account);
        Mockito.when(accountInternalApi.getBCD(Mockito.<UUID>any(), Mockito.<InternalTenantContext>any())).thenReturn(billCycleDay);
        return account;
    }

    private DateTime createSubscriptionCreationEvent(final Plan nextPlan, final PlanPhase nextPhase) throws CatalogApiException {
        final DateTime now = clock.getUTCNow();
        final DateTime then = now.minusDays(1);
        final PriceList nextPriceList = catalog.findPriceListForPlan(nextPlan.getName(), now, now);

        final EffectiveSubscriptionInternalEvent t = new MockEffectiveSubscriptionEvent(
                eventId, subId, bunId, bunKey, then, now, null, null, null, null, null, EntitlementState.ACTIVE,
                nextPlan.getName(), nextPhase.getName(),
                nextPriceList.getName(), null, 1L,
                SubscriptionBaseTransitionType.CREATE, 1, null, 1L, 2L, null);

        effectiveSubscriptionTransitions.add(t);
        return now;
    }
}
