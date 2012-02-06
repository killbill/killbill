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

public class TestUserApiCancelSql extends TestUserApiCancel {


    private final int MAX_STRESS_ITERATIONS = 10;

    @Override
    public Injector getInjector() {
        return Guice.createInjector(Stage.DEVELOPMENT, new MockEngineModuleSql());
    }

    @Test(enabled= false, groups={"stress"})
    public void stressTest() {
        for (int i = 0; i < MAX_STRESS_ITERATIONS; i++) {
            cleanupTest();
            setupTest();
            testCancelSubscriptionIMM();
            cleanupTest();
            setupTest();
            testCancelSubscriptionEOTWithChargeThroughDate();
            cleanupTest();
            setupTest();
            testCancelSubscriptionEOTWithNoChargeThroughDate();
        }
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testCancelSubscriptionIMM() {
        super.testCancelSubscriptionIMM();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testCancelSubscriptionEOTWithChargeThroughDate() {
        super.testCancelSubscriptionEOTWithChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testCancelSubscriptionEOTWithNoChargeThroughDate() {
        super.testCancelSubscriptionEOTWithNoChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"sql"})
    public void testUncancel() {
        super.testUncancel();
    }

}
