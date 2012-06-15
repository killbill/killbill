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

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.TestApiBase;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public abstract class TestUserApiRecreate extends TestApiBase {

    private static final Logger log = LoggerFactory.getLogger(TestUserApiRecreate.class);


    protected void testRecreateWithBPCanceledThroughSubscription() {
        log.info("Starting testRecreateWithBPCanceledThroughSubscription");
        try {
            testCreateAndRecreate(false);
            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception", e);
            Assert.fail(e.getMessage());
        }
    }

    protected void testCreateWithBPCanceledFromUserApi() {
        log.info("Starting testCreateWithBPCanceledFromUserApi");
        try {
            testCreateAndRecreate(true);
            assertListenerStatus();
        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception", e);
            Assert.fail(e.getMessage());
        }
    }


    private SubscriptionData testCreateAndRecreate(final boolean fromUserAPi) throws EntitlementUserApiException {

        final DateTime init = clock.getUTCNow();
        final DateTime requestedDate = init.minusYears(1);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.CREATE);
        SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                             getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
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
                                                                                    getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
            } else {
                subscription.recreate(getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
            }
            Assert.fail("Expected Create API to fail since BP already exists");
        } catch (EntitlementUserApiException e) {
            assertTrue(true);
        }

        // NOW CANCEL ADN THIS SHOULD WORK
        testListener.pushExpectedEvent(NextEvent.CANCEL);
        subscription.cancel(null, false, context);

        testListener.pushExpectedEvent(NextEvent.PHASE);
        testListener.pushExpectedEvent(NextEvent.RE_CREATE);

        // Avoid ordering issue for events at exact same date; this is actually a real good test, we
        // we test it at Beatrix level. At this level that would work for sql tests but not for in memory.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }

        if (fromUserAPi) {
            subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                                                                                getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
        } else {
            subscription.recreate(getProductSpecifier(productName, planSetName, term, null), clock.getUTCNow(), context);
        }
        assertEquals(subscription.getActiveVersion(), SubscriptionEvents.INITIAL_VERSION);
        assertEquals(subscription.getBundleId(), bundle.getId());
        assertEquals(subscription.getStartDate(), requestedDate);
        assertEquals(productName, subscription.getCurrentPlan().getProduct().getName());

        return subscription;
    }
}
