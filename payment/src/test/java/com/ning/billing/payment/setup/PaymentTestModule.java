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

package com.ning.billing.payment.setup;

import org.apache.commons.collections.MapUtils;

import com.google.common.collect.ImmutableMap;
import com.ning.billing.account.api.IAccountUserApi;
import com.ning.billing.account.api.MockAccountUserApi;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.eventbus.EventBus;
import com.ning.billing.util.eventbus.MemoryEventBus;

public class PaymentTestModule extends PaymentModule {
    public PaymentTestModule() {
        super(MapUtils.toProperties(ImmutableMap.of("killbill.payment.provider.default", "my-mock")));
    }

    @Override
    protected void installPaymentProviderPlugins(PaymentConfig config) {
        install(new MockPaymentProviderPluginModule("my-mock"));
    }

    @Override
    protected void configure() {
        super.configure();
        bind(EventBus.class).to(MemoryEventBus.class).asEagerSingleton();
        bind(IAccountUserApi.class).to(MockAccountUserApi.class).asEagerSingleton();
    }
}
