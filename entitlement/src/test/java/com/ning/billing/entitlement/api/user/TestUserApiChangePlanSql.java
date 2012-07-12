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

import org.testng.annotations.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;

public class TestUserApiChangePlanSql extends TestUserApiChangePlan {
    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled = false, groups = {"slow", "stress"})
    public void stressTest() throws Exception {
        final int MAX_STRESS_ITERATIONS = 10;
        for (int i = 0; i < MAX_STRESS_ITERATIONS; i++) {
            cleanupTest();
            setupTest();
            testChangePlanBundleAlignEOTWithNoChargeThroughDate();
            cleanupTest();
            setupTest();
            testChangePlanBundleAlignEOTWithChargeThroughDate();
            cleanupTest();
            setupTest();
            testChangePlanBundleAlignIMM();
            cleanupTest();
            setupTest();
            testMultipleChangeLastIMM();
            cleanupTest();
            setupTest();
            testMultipleChangeLastEOT();
        }
    }

    @Override
    @Test(groups = "slow")
    public void testCorrectPhaseAlignmentOnChange() {
        super.testCorrectPhaseAlignmentOnChange();
    }

    @Override
    @Test(groups = "slow")
    public void testChangePlanBundleAlignEOTWithNoChargeThroughDate() {
        super.testChangePlanBundleAlignEOTWithNoChargeThroughDate();
    }

    @Override
    @Test(groups = "slow")
    public void testChangePlanBundleAlignEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        super.testChangePlanBundleAlignEOTWithChargeThroughDate();
    }

    @Override
    @Test(groups = "slow")
    public void testChangePlanBundleAlignIMM() {
        super.testChangePlanBundleAlignIMM();
    }

    @Override
    @Test(groups = "slow")
    public void testMultipleChangeLastIMM() throws EntitlementBillingApiException {
        super.testMultipleChangeLastIMM();
    }

    @Override
    @Test(groups = "slow")
    public void testMultipleChangeLastEOT() throws EntitlementBillingApiException {
        super.testMultipleChangeLastEOT();
    }

    // rescue not implemented yet
    @Override
    @Test(enabled = false, groups = {"slow"})
    public void testChangePlanChangePlanAlignEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        super.testChangePlanChangePlanAlignEOTWithChargeThroughDate();
    }
}
