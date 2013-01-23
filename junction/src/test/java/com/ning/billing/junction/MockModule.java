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

package com.ning.billing.junction;

import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.junction.glue.DefaultJunctionModule;
import com.ning.billing.mock.glue.MockClockModule;
import com.ning.billing.mock.glue.MockDbHelperModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import com.ning.billing.util.glue.NonEntityDaoModule;

public class MockModule extends DefaultJunctionModule {
    @Override
    protected void configure() {
        super.configure();

        install(new MockClockModule());
        install(new MockDbHelperModule());
        install(new CallContextModule());
        install(new CatalogModule());
        install(new CacheModule());
        install(new NonEntityDaoModule());
    }

    @Override
    public void installBillingApi() {
        // no billing Api
    }

    @Override
    public void installAccountUserApi() {
    }
}
