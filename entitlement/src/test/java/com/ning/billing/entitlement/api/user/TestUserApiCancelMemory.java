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
import com.ning.billing.entitlement.api.billing.EntitlementBillingApiException;
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;
import org.testng.annotations.Test;

public class TestUserApiCancelMemory extends TestUserApiCancel {


    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.PRODUCTION, new MockEngineModuleMemory());
    }

    @Override
    @Test(enabled=false, groups={"fast"})
    public void testCancelSubscriptionIMM() {
        super.testCancelSubscriptionIMM();
    }

    @Override
    @Test(enabled=false, groups={"fast"})
    public void testCancelSubscriptionEOTWithChargeThroughDate() throws EntitlementBillingApiException {
        super.testCancelSubscriptionEOTWithChargeThroughDate();
    }

    @Override
    @Test(enabled=false, groups={"fast"})
    public void testCancelSubscriptionEOTWithNoChargeThroughDate() {
        super.testCancelSubscriptionEOTWithNoChargeThroughDate();
    }

    @Override
    @Test(enabled=false, groups={"fast"})
    public void testUncancel() throws EntitlementBillingApiException {
        super.testUncancel();
    }
}
