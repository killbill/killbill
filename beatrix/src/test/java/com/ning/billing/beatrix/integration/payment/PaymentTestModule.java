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

package com.ning.billing.beatrix.integration.payment;

import com.ning.billing.account.dao.MockAccountDao;
import com.ning.billing.invoice.dao.MockInvoiceDao;
import org.apache.commons.collections.MapUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Provider;
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.junction.api.BillingApi;
import com.ning.billing.mock.BrainDeadProxyFactory;
import com.ning.billing.payment.dao.MockPaymentDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.glue.PaymentModule;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.bus.Bus;
import com.ning.billing.util.bus.InMemoryBus;
import com.ning.billing.util.notificationq.MockNotificationQueueService;
import com.ning.billing.util.notificationq.NotificationQueueService;

public class PaymentTestModule extends PaymentModule {
	public static class MockProvider implements Provider<BillingApi> {
		@Override
		public BillingApi get() {
			return BrainDeadProxyFactory.createBrainDeadProxyFor(BillingApi.class);
		}

	}

    public PaymentTestModule() {
        super(MapUtils.toProperties(ImmutableMap.of("killbill.payment.provider.default", "my-mock",
                "killbill.payment.engine.events.off", "false")));
    }

    @Override
    protected void installPaymentDao() {
        bind(PaymentDao.class).to(MockPaymentDao.class).asEagerSingleton();
    }

    @Override
    protected void installPaymentProviderPlugins(PaymentConfig config) {
        install(new MockPaymentProviderPluginModule("my-mock"));
    }

    @Override
    protected void configure() {
        super.configure();
        bind(Bus.class).to(InMemoryBus.class).asEagerSingleton();
        bind(MockAccountDao.class).asEagerSingleton();
        bind(AccountDao.class).to(MockAccountDao.class);
        bind(MockInvoiceDao.class).asEagerSingleton();
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
        bind(NotificationQueueService.class).to(MockNotificationQueueService.class).asEagerSingleton();

    }
}
