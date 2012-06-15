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

package com.ning.billing.payment.glue;

import java.io.IOException;
import java.net.URL;

import org.apache.commons.collections.MapUtils;

import com.google.common.collect.ImmutableMap;
import com.ning.billing.config.PaymentConfig;
import com.ning.billing.mock.glue.MockInvoiceModule;
import com.ning.billing.mock.glue.MockNotificationQueueModule;
import com.ning.billing.mock.glue.TestDbiModule;
import com.ning.billing.payment.dao.MockPaymentDao;
import com.ning.billing.payment.dao.PaymentDao;
import com.ning.billing.payment.provider.MockPaymentProviderPluginModule;
import com.ning.billing.util.globallocker.GlobalLocker;
import com.ning.billing.util.globallocker.MockGlobalLocker;
import com.ning.billing.util.glue.BusModule;
import com.ning.billing.util.glue.BusModule.BusType;
import com.ning.billing.util.glue.TagStoreModule;

import static org.testng.Assert.assertNotNull;

public class PaymentTestModuleWithMocks extends PaymentModule {
    /*
	public static class MockProvider implements Provider<BillingApi> {
		@Override
		public BillingApi get() {
			return BrainDeadProxyFactory.createBrainDeadProxyFor(BillingApi.class);
		}

	}
	*/

    public static final String PLUGIN_TEST_NAME = "my-mock";

    private void loadSystemPropertiesFromClasspath(final String resource) {
        final URL url = PaymentTestModuleWithMocks.class.getResource(resource);
        assertNotNull(url);

        try {
            props.load(url.openStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PaymentTestModuleWithMocks() {
        super(MapUtils.toProperties(ImmutableMap.of("killbill.payment.provider.default", PLUGIN_TEST_NAME,
                                                    "killbill.payment.engine.events.off", "false")));
    }

    @Override
    protected void installPaymentDao() {
        bind(PaymentDao.class).to(MockPaymentDao.class).asEagerSingleton();
    }

    @Override
    protected void installPaymentProviderPlugins(final PaymentConfig config) {
        install(new MockPaymentProviderPluginModule(PLUGIN_TEST_NAME));
    }

    @Override
    protected void configure() {
        loadSystemPropertiesFromClasspath("/payment.properties");
        super.configure();
        install(new BusModule(BusType.MEMORY));
        install(new MockNotificationQueueModule());
        install(new MockInvoiceModule());
        install(new TestDbiModule());
        install(new TagStoreModule());
        bind(GlobalLocker.class).to(MockGlobalLocker.class).asEagerSingleton();
    }
}
