/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration.overdue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.beatrix.integration.BeatrixIntegrationModule;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.overdue.OverdueService;
import org.killbill.billing.overdue.api.DefaultOverdueInternalApi;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.TestPaymentMethodPluginBase;
import org.killbill.xmlloader.XMLLoader;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertNotNull;

public abstract class TestOverdueBase extends TestIntegrationBase {

    protected Account account;
    protected SubscriptionBundle bundle;
    protected String productName;
    protected BillingPeriod term;

    public abstract String getOverdueConfig();

    final PaymentMethodPlugin paymentMethodPlugin = new TestPaymentMethodPluginBase();

    @Override
    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        final String configXml = getOverdueConfig();
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);
        overdueListener.setOverdueConfig(config);
        ((DefaultOverdueInternalApi) overdueUserApi).setOverdueConfig(config);

        account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, paymentMethodPlugin, PLUGIN_PROPERTIES, callContext);
        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        paymentPlugin.clear();
    }

    protected void checkODState(final String expected) {
        try {
            // This will test the overdue notification queue: when we move the clock, the overdue system
            // should get notified to refresh its state.
            // Calling explicitly refresh here (overdueApi.refreshOverdueStateFor(account)) would not fully
            // test overdue.
            // Since we're relying on the notification queue, we may need to wait a bit (hence await()).
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return expected.equals(blockingApi.getBlockingStateForService(account.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContext).getStateName());
                }
            });
        } catch (final Exception e) {
            Assert.assertEquals(blockingApi.getBlockingStateForService(account.getId(), BlockingStateType.ACCOUNT, OverdueService.OVERDUE_SERVICE_NAME, internalCallContext).getStateName(), expected, "Got exception: " + e.toString());
        }
    }
}
