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

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.EntitlementTestSuiteWithEmbeddedDB;
import com.ning.billing.entitlement.api.Entitlement.EntitlementActionPolicy;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestDefaultEntitlement extends EntitlementTestSuiteWithEmbeddedDB {


    @Test(groups = "slow")
    public void testCancelWithEntitlementDate() {

        try {
            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
            assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

            clock.addDays(5);
            final LocalDate cancelDate = new LocalDate(clock.getUTCNow());
            entitlement.cancelEntitlementWithDate(cancelDate, callContext);
            final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            assertEquals(entitlement2.getState(), EntitlementState.CANCELLED);
            assertEquals(entitlement2.getEffectiveEndDate(), cancelDate);

        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }


    @Test(groups = "slow")
    public void testCancelWithEntitlementDateInFuture() {

        try {
            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);
            assertEquals(entitlement.getState(), EntitlementState.ACTIVE);

            clock.addDays(5);
            final LocalDate cancelDate = new LocalDate(clock.getUTCToday().plusDays(1));
            entitlement.cancelEntitlementWithDate(cancelDate, callContext);
            final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            assertEquals(entitlement2.getState(), EntitlementState.ACTIVE);
            assertNull(entitlement2.getEffectiveEndDate());

            clock.addDays(1);
            final Entitlement entitlement3 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            assertEquals(entitlement3.getState(), EntitlementState.CANCELLED);
            assertEquals(entitlement3.getEffectiveEndDate(), cancelDate);


        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }

    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTAndNOCTD() {

        try {

            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);

            final Entitlement cancelledEntitlement = entitlement.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, callContext);
            assertEquals(cancelledEntitlement.getState(), EntitlementState.CANCELLED);
            assertEquals(cancelledEntitlement.getEffectiveEndDate(), initialDate);

        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }


    @Test(groups = "slow")
    public void testCancelWithEntitlementPolicyEOTAndCTD() {

        try {

            final LocalDate initialDate = new LocalDate(2013, 8, 7);
            clock.setDay(initialDate);

            final Account account = accountApi.createAccount(getAccountData(7), callContext);

            final PlanPhaseSpecifier spec = new PlanPhaseSpecifier("Shotgun", ProductCategory.BASE, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null);

            // Create entitlement and check each field
            final Entitlement entitlement = entitlementApi.createBaseEntitlement(account.getId(), spec, account.getExternalKey(), initialDate, callContext);

            final DateTime ctd = clock.getUTCNow().plusDays(30).plusMonths(1);
            testListener.pushExpectedEvent(NextEvent.PHASE);
            clock.addDays(32);
            // Set manually since no invoice
            subscriptionInternalApi.setChargedThroughDate(entitlement.getId(), ctd, internalCallContext);
            assertTrue(testListener.isCompleted(5000));

            final Entitlement entitlement2 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            final Entitlement entitlement3 = entitlement2.cancelEntitlementWithPolicy(EntitlementActionPolicy.END_OF_TERM, callContext);
            assertEquals(entitlement3.getState(), EntitlementState.ACTIVE);
            assertNull(entitlement3.getEffectiveEndDate());

            clock.addMonths(1);

            final Entitlement entitlement4 = entitlementApi.getEntitlementForId(entitlement.getId(), callContext);
            assertEquals(entitlement4.getState(), EntitlementState.CANCELLED);
            assertEquals(entitlement4.getEffectiveEndDate(), new LocalDate(ctd));

        } catch (EntitlementApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        } catch (AccountApiException e) {
            Assert.fail("Test failed " + e.getMessage());
        }
    }

}
