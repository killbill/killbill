/*
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

package org.killbill.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.api.TestApiListener.NextEvent;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.PlanPhaseSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import static org.testng.Assert.assertEquals;

public class TestRegessionSubscriptionApi extends EntitlementTestSuiteWithEmbeddedDB {

    @Test(groups = "slow", description = "Verify behavior with or without ENT_STARTED event works as expected")
    public void testRegressionForNew_ENT_STARTED_event() throws Exception {

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        // Start the entitlement yesterday (does not m,ake sense, but we want to check later different of behavior)
        final LocalDate entitlementEffectiveDate = initialDate.minusDays(1);

        final Account account = createAccount(getAccountData(7));
        final PlanPhaseSpecifier planPhaseSpecifier = new PlanPhaseSpecifier("Shotgun",  BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);
        final EntitlementSpecifier spec = new DefaultEntitlementSpecifier(planPhaseSpecifier);
        testListener.pushExpectedEvents(NextEvent.CREATE, NextEvent.BLOCK);
        final UUID entitlementId = entitlementApi.createBaseEntitlement(account.getId(), spec, UUID.randomUUID().toString(), entitlementEffectiveDate, null, false, true, ImmutableList.<PluginProperty>of(), callContext);
        final Entitlement entitlement = entitlementApi.getEntitlementForId(entitlementId, callContext);
        // Because of the BlockingState event ENT_STARTED, the entitlement date should be correctly set
        Assert.assertEquals(entitlement.getEffectiveStartDate(), entitlementEffectiveDate);

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForAccountId(account.getId(), callContext);
        Assert.assertEquals(bundles.size(), 1);
        subscriptionBundleChecker(bundles, initialDate, entitlement, 0);

        // Let's do some surgery and inactivate the ENT_STARTED BlockingState
        final List<BlockingState> blockingStates = blockingStateDao.getBlockingState(entitlement.getId(), BlockingStateType.SUBSCRIPTION, clock.getUTCNow(), internalCallContext);
        assertEquals(blockingStates.size(), 1);
        assertEquals(blockingStates.get(0).getStateName(), DefaultEntitlementApi.ENT_STATE_START);
        blockingStateDao.unactiveBlockingState(blockingStates.get(0).getId(), internalCallContext);

        final Entitlement oldSchoolEntitlement = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
        // Because the ENT_STARTED BlockingState has been invalidated, the startDate should now default to the billingDate
        Assert.assertEquals(oldSchoolEntitlement.getEffectiveStartDate(), initialDate);

        final List<SubscriptionBundle> oldSchoolBundles = subscriptionApi.getSubscriptionBundlesForAccountId(account.getId(), callContext);
        Assert.assertEquals(oldSchoolBundles.size(), 1);
        subscriptionBundleChecker(oldSchoolBundles, initialDate, oldSchoolEntitlement, 0);
    }

    private void subscriptionBundleChecker(final List<SubscriptionBundle> bundles, final LocalDate billingStartDate, final Entitlement entitlement, final int idx) {
        Assert.assertEquals(bundles.get(idx).getId(), entitlement.getBundleId());
        Assert.assertEquals(bundles.get(idx).getSubscriptions().size(), 1);
        Assert.assertEquals(bundles.get(idx).getSubscriptions().get(0).getId(), entitlement.getId());
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().size(), 3);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getEffectiveDate(), entitlement.getEffectiveStartDate());
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(0).getSubscriptionEventType(), SubscriptionEventType.START_ENTITLEMENT);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getEffectiveDate(), billingStartDate);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(1).getSubscriptionEventType(), SubscriptionEventType.START_BILLING);
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getEffectiveDate(), new LocalDate(2013, 9, 6));
        Assert.assertEquals(bundles.get(idx).getTimeline().getSubscriptionEvents().get(2).getSubscriptionEventType(), SubscriptionEventType.PHASE);
    }

}
