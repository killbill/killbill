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

package com.ning.billing.subscription.glue;

import org.mockito.Mockito;
import org.skife.config.ConfigSource;

import com.ning.billing.account.api.AccountUserApi;
import com.ning.billing.api.TestApiListener;
import com.ning.billing.api.TestListenerStatus;
import com.ning.billing.catalog.glue.CatalogModule;
import com.ning.billing.subscription.DefaultSubscriptionTestInitializer;
import com.ning.billing.subscription.SubscriptionTestInitializer;
import com.ning.billing.subscription.SubscriptionTestListenerStatus;
import com.ning.billing.subscription.api.user.TestSubscriptionHelper;
import com.ning.billing.util.glue.CacheModule;
import com.ning.billing.util.glue.CallContextModule;

public class TestDefaultSubscriptionModule extends DefaultSubscriptionModule {

    public TestDefaultSubscriptionModule(final ConfigSource configSource) {
        super(configSource);
    }


    @Override
    protected void configure() {
        super.configure();
        install(new CatalogModule(configSource));
        install(new CallContextModule());
        install(new CacheModule(configSource));

        bind(AccountUserApi.class).toInstance(Mockito.mock(AccountUserApi.class));

        bind(TestSubscriptionHelper.class).asEagerSingleton();
        bind(TestListenerStatus.class).to(SubscriptionTestListenerStatus.class).asEagerSingleton();
        bind(TestApiListener.class).asEagerSingleton();
        bind(SubscriptionTestInitializer.class).to(DefaultSubscriptionTestInitializer.class).asEagerSingleton();
    }
}
