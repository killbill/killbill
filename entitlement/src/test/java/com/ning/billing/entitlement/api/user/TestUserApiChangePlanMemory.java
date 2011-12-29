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
import com.ning.billing.entitlement.glue.MockEngineModuleMemory;
import org.testng.annotations.Test;

public class TestUserApiChangePlanMemory extends TestUserApiChangePlan {

    @Override
    protected Injector getInjector() {
        return Guice.createInjector(Stage.PRODUCTION, new MockEngineModuleMemory());
    }


    @Override
    @Test(enabled=true, groups={"fast"})
    public void testChangePlanBundleAlignEOTWithNoChargeThroughDate() {
         super.testChangePlanBundleAlignEOTWithNoChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"fast"})
    public void testChangePlanBundleAlignEOTWithChargeThroughDate() {
        super.testChangePlanBundleAlignEOTWithChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"fast"})
    public void testChangePlanBundleAlignIMM() {
        super.testChangePlanBundleAlignIMM();
    }

    @Override
    @Test(enabled=true, groups={"fast"})
    public void testMultipleChangeLastIMM() {
        super.testMultipleChangeLastIMM();
    }

    @Override
    @Test(enabled=true, groups={"fast"})
    public void testMultipleChangeLastEOT() {
        super.testMultipleChangeLastEOT();
    }

    // Set to false until we implement rescue example.
    @Override
    @Test(enabled=false, groups={"fast"})
    public void testChangePlanChangePlanAlignEOTWithChargeThroughDate() {
        super.testChangePlanChangePlanAlignEOTWithChargeThroughDate();
    }

    @Override
    @Test(enabled=true, groups={"fast"})
    public void testCorrectPhaseAlignmentOnChange() {
        super.testCorrectPhaseAlignmentOnChange();
    }
}
