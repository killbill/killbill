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

package org.killbill.billing.payment.glue;

import org.skife.config.ConfigSource;

import org.killbill.billing.GuicyKillbillTestNoDBModule;
import org.killbill.billing.mock.glue.MockNonEntityDaoModule;
import org.killbill.billing.payment.dao.MockPaymentDao;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.clock.Clock;

public class TestPaymentModuleNoDB extends TestPaymentModule {

    public TestPaymentModuleNoDB(final ConfigSource configSource, final Clock clock) {
        super(configSource, clock);
    }

    @Override
    protected void installPaymentDao() {
        bind(PaymentDao.class).to(MockPaymentDao.class).asEagerSingleton();
    }

    @Override
    protected void configure() {
        install(new GuicyKillbillTestNoDBModule());
        install(new MockNonEntityDaoModule());
        super.configure();
    }
}
