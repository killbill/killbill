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

package com.ning.billing.entitlement.api.user;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.inject.Injector;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;

public abstract class TestUserApiRecreate extends TestApiBase {

    private static Logger log = LoggerFactory.getLogger(TestUserApiRecreate.class);


    protected void testRecreateWithBPCanceledThroughSubscription() {
        log.info("Starting testRecreateWithBPCanceled");
        try {
            testCreateAndRecreate(false);
        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception",e);
            Assert.fail(e.getMessage());
        }
    }

    protected void testCreateWithBPCanceledFromUserApi() {
        log.info("Starting testCreateWithBPCanceled");
        try {
            testCreateAndRecreate(true);
        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception",e);
            Assert.fail(e.getMessage());
        }
    }


    private SubscriptionData testCreateAndRecreate(boolean fromUserAPi) throws EntitlementUserApiException {

        DateTime init = clock.getUTCNow();
        DateTime requestedDate = init.minusYears(1);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CREATE);
        SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                getProductSpecifier(productName, planSetName, term, null), requestedDate);
        assertNotNull(subscription);
        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), requestedDate);
        assertEquals(productName, subscription.getCurrentPlan().getProduct().getName());

        assertTrue(testListener.isCompleted(5000));

        // CREATE (AGAIN) WITH NEW PRODUCT
        productName = "Pistol";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        try {

            if (fromUserAPi) {
                subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                        getProductSpecifier(productName, planSetName, term, null), requestedDate);
            } else {
                subscription.recreate(getProductSpecifier(productName, planSetName, term, null), requestedDate);
            }
            Assert.fail("Expected Create API to fail since BP already exists");
        } catch (EntitlementUserApiException e) {
            assertTrue(true);
        }

        // NOW CANCEL ADN THIS SHOULD WORK
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        subscription.cancel(null, false);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.RE_CREATE);

        // Avoid ordering issue for events at excat same date; this is actually a real good test, we
        // we test it at Beatrix level. At this level that would work for sql tests but not for in memory.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }

        if (fromUserAPi) {
            subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), requestedDate);
        } else {
            subscription.recreate(getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow());
        }
        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), requestedDate);
        assertEquals(productName, subscription.getCurrentPlan().getProduct().getName());

        return subscription;
    }
}
