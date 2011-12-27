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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.ning.billing.entitlement.glue.MockEngineModuleSql;
import org.testng.annotations.Test;

public class TestUserApiChangePlanSql extends TestUserApiChangePlan {

    private final int MAX_STRESS_ITERATIONS = 10;

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled= true, groups={"stress"})
    public void stressTest() {
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
    @Test(enabled=true, groups={"sql"})
    public void testChangePlanBundleAlignEOTWithNoChargeThroughDate() {
        super.testChangePlanBundleAlignEOTWithNoChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testChangePlanBundleAlignEOTWithChargeThroughDate() {
        super.testChangePlanBundleAlignEOTWithChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testChangePlanBundleAlignIMM() {
        super.testChangePlanBundleAlignIMM();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testMultipleChangeLastIMM() {
        super.testMultipleChangeLastIMM();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testMultipleChangeLastEOT() {
        super.testMultipleChangeLastEOT();
    }

    // rescue not implemented yet
    @Override
    @Test(enabled=false, groups={"sql"})
    public void testChangePlanChangePlanAlignEOTWithChargeThroughDate() {
        super.testChangePlanChangePlanAlignEOTWithChargeThroughDate();
    }

}
