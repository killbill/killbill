/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.server.log;

import org.killbill.billing.GuicyKillbillTestModule;
import org.killbill.billing.GuicyKillbillTestSuiteNoDB;
import org.testng.annotations.BeforeClass;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class ServerTestSuiteNoDB extends GuicyKillbillTestSuiteNoDB {

    @BeforeClass(groups = "fast")
    protected void beforeClass() throws Exception {
        if (hasFailed()) {
            return;
        }

        final Injector injector = Guice.createInjector(new TestServerModuleNoDB(configSource));
        injector.injectMembers(this);
    }
}
