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

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.CallOrigin;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.callcontext.DefaultCallContextFactory;
import com.ning.billing.util.clock.DefaultClock;
import org.joda.time.DateTime;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.entitlement.api.TestApiBase;
import com.ning.billing.entitlement.api.ApiTestListener.NextEvent;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;
import com.ning.billing.util.customfield.CustomField;


public class TestUserCustomFieldsSql extends TestApiBase {
    private static final String USER_NAME = "Entitlement Test";
    private final CallContext context = new DefaultCallContextFactory(new DefaultClock()).createCallContext(USER_NAME, CallOrigin.TEST, UserType.TEST);

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled=false, groups={"slow"})
    public void stress() {
        cleanupTest();
        for (int i = 0; i < 20; i++) {
            setupTest();
            testOverwriteCustomFields();
            cleanupTest();

            setupTest();
            testBasicCustomFields();
            cleanupTest();
        }
    }

    @Test(enabled=true, groups={"slow"})
    public void testOverwriteCustomFields() {
        log.info("Starting testCreateWithRequestedDate");
        try {

            DateTime init = clock.getUTCNow();
            DateTime requestedDate = init.minusYears(1);

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.CREATE);
            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
            assertNotNull(subscription);

            assertEquals(subscription.getFieldValue("nonExistent"), null);

            subscription.saveFieldValue("field1", "value1", context);
            assertEquals(subscription.getFieldValue("field1"), "value1");
            List<CustomField> allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription.saveFieldValue("field1", "valueNew1", context);
            assertEquals(subscription.getFieldValue("field1"), "valueNew1");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            assertEquals(subscription.getFieldValue("field1"), "valueNew1");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription.saveFieldValue("field1", "valueSuperNew1", context);
            assertEquals(subscription.getFieldValue("field1"), "valueSuperNew1");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            assertEquals(subscription.getFieldValue("field1"), "valueSuperNew1");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription.saveFieldValue("field1", null, context);
            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            assertEquals(subscription.getFieldValue("field1"), null);
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);
        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception",e);
            Assert.fail(e.getMessage());
        }
    }

    @Test(enabled=true, groups={"slow"})
    public void testBasicCustomFields() {
        log.info("Starting testCreateWithRequestedDate");
        try {

            DateTime init = clock.getUTCNow();
            DateTime requestedDate = init.minusYears(1);

            String productName = "Shotgun";
            BillingPeriod term = BillingPeriod.MONTHLY;
            String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

            testListener.pushExpectedEvent(NextEvent.PHASE);
            testListener.pushExpectedEvent(NextEvent.CREATE);
            SubscriptionData subscription = (SubscriptionData) entitlementApi.createSubscription(bundle.getId(),
                    getProductSpecifier(productName, planSetName, term, null), requestedDate, context);
            assertNotNull(subscription);


            subscription.saveFieldValue("field1", "value1", context);
            assertEquals(subscription.getFieldValue("field1"), "value1");
            List<CustomField> allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 1);

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            assertEquals(subscription.getFieldValue("field1"), "value1");
            assertEquals(allFields.size(), 1);

            subscription.clearFields();

            subscription.saveFieldValue("field2", "value2", context);
            subscription.saveFieldValue("field3", "value3", context);
            assertEquals(subscription.getFieldValue("field2"), "value2");
            assertEquals(subscription.getFieldValue("field3"), "value3");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 2);

            subscription = (SubscriptionData) entitlementApi.getSubscriptionFromId(subscription.getId());
            assertEquals(subscription.getFieldValue("field2"), "value2");
            assertEquals(subscription.getFieldValue("field3"), "value3");
            allFields = subscription.getFieldList();
            assertEquals(allFields.size(), 2);

        } catch (EntitlementUserApiException e) {
            log.error("Unexpected exception",e);
            Assert.fail(e.getMessage());
        }
    }
}
