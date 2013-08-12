/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.entitlement.glue;

import com.ning.billing.catalog.MockCatalogModule;
import com.ning.billing.mock.glue.MockAccountModule;
import com.ning.billing.mock.glue.MockSubscriptionModule;
import com.ning.billing.subscription.glue.DefaultSubscriptionModule;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;
import org.skife.config.ConfigSource;

public class TestEntitlementModule extends DefaultEntitlementModule {

    final protected ConfigSource configSource;

    public TestEntitlementModule(final ConfigSource configSource) {
        super(configSource);
        this.configSource = configSource;
    }

    @Override
    protected void configure() {
        super.configure();
        install(new CacheModule(configSource));
        install(new CallContextModule());
        install(new MockAccountModule());
    }
}
