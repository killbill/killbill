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

package com.ning.billing.entitlement.api;

import java.util.List;

import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestDefaultSubscriptionApi extends EntitlementTestSuiteWithEmbeddedDB {


    @Test(groups = "slow")
    public void testWithMultipleBundle() throws AccountApiException, SubscriptionApiException, EntitlementApiException {
        final String externalKey = "fooXXX";

        final LocalDate initialDate = new LocalDate(2013, 8, 7);
        clock.setDay(initialDate);

        final Account account = accountApi.createAccount(getAccountData(7), callContext);

        final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, externalKey, initialDate, callContext);
        assertListenerStatus();
        assertEquals(entitlement.getAccountId(), account.getId());
        assertEquals(entitlement.getExternalKey(), externalKey);

        assertEquals(entitlement.getEffectiveStartDate(), initialDate);
        assertNull(entitlement.getEffectiveEndDate());

        final List<SubscriptionBundle> bundles = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles.size(), 1);

        // Cancel entitlement
        clock.addDays(3);
        testListener.pushExpectedEvents(NextEvent.CANCEL, NextEvent.BLOCK);
        entitlement.cancelEntitlementWithDate(new LocalDate(clock.getUTCNow(), account.getTimeZone()), true, callContext);
        assertListenerStatus();

        clock.addDays(1);
        // Re-create a new bundle with same externalKey
        final PlanPhaseSpecifier spec2 = new PlanPhaseSpecifier("Pistol", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

        // Create entitlement and check each field
        testListener.pushExpectedEvent(NextEvent.CREATE);
        final Entitlement entitlement2 = entitlementApi.createBaseEntitlement(account.getId(), spec2, externalKey, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertListenerStatus();
        assertEquals(entitlement2.getAccountId(), account.getId());
        assertEquals(entitlement2.getExternalKey(), externalKey);

        final List<SubscriptionBundle> bundles2 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles2.size(), 2);

        SubscriptionBundle firstbundle = bundles2.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        SubscriptionBundle secondbundle = bundles2.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertNull(secondbundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(secondbundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        final List<SubscriptionBundle> bundles2Again = subscriptionApi.getSubscriptionBundlesForAccountIdAndExternalKey(account.getId(), externalKey, callContext);
        assertEquals(bundles2Again.size(), 2);

        clock.addDays(3);

        final Account account2 = accountApi.createAccount(getAccountData(7), callContext);

        testListener.pushExpectedEvents(NextEvent.TRANSFER, NextEvent.CANCEL, NextEvent.BLOCK);
        entitlementApi.transferEntitlements(account.getId(), account2.getId(), externalKey, new LocalDate(clock.getUTCNow(), account.getTimeZone()), callContext);
        assertListenerStatus();

        final List<SubscriptionBundle> bundles3 = subscriptionApi.getSubscriptionBundlesForExternalKey(externalKey, callContext);
        assertEquals(bundles3.size(), 3);

        firstbundle = bundles3.get(0);
        assertEquals(firstbundle.getSubscriptions().size(), 1);
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 7));
        assertEquals(firstbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 10));
        assertEquals(firstbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 10));

        secondbundle = bundles3.get(1);
        assertEquals(secondbundle.getSubscriptions().size(), 1);
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 11));
        assertEquals(secondbundle.getSubscriptions().get(0).getEffectiveEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getSubscriptions().get(0).getBillingEndDate(), new LocalDate(2013, 8, 14));
        assertEquals(secondbundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);

        SubscriptionBundle thirdBundle = bundles3.get(2);
        assertEquals(thirdBundle.getSubscriptions().size(), 1);
        assertEquals(thirdBundle.getSubscriptions().get(0).getEffectiveStartDate(), new LocalDate(2013, 8, 14));
        assertEquals(thirdBundle.getSubscriptions().get(0).getBillingStartDate(), new LocalDate(2013, 8, 14));
        assertNull(thirdBundle.getSubscriptions().get(0).getEffectiveEndDate());
        assertNull(thirdBundle.getSubscriptions().get(0).getBillingEndDate());
        assertEquals(thirdBundle.getOriginalCreatedDate().compareTo(firstbundle.getCreatedDate()), 0);
    }
}
