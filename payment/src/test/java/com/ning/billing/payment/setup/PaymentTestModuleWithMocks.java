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
import com.ning.billing.account.dao.AccountDao;
import com.ning.billing.account.dao.MockAccountDao;
import com.ning.billing.invoice.dao.InvoiceDao;
import com.ning.billing.invoice.dao.MockInvoiceDao;
import com.ning.billing.payment.dao.MockPaymentDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.eventbus.Bus;
import com.ning.billing.util.eventbus.MemoryEventBus;

public class PaymentTestModuleWithMocks extends PaymentModule {
    public PaymentTestModuleWithMocks() {
        super(MapUtils.toProperties(ImmutableMap.of("killbill.payment.provider.default", "my-mock")));
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
        bind(Bus.class).to(MemoryEventBus.class).asEagerSingleton();
        bind(MockAccountDao.class).asEagerSingleton();
        bind(AccountDao.class).to(MockAccountDao.class);
        bind(MockInvoiceDao.class).asEagerSingleton();
        bind(InvoiceDao.class).to(MockInvoiceDao.class);
    }
}
