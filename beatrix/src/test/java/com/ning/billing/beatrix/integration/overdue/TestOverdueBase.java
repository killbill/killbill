/*
 * Copyright 2010-2012 Ning, Inc.
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
package com.ning.billing.beatrix.integration.overdue;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;

import com.ning.billing.account.api.Account;
import com.ning.billing.beatrix.integration.BeatrixIntegrationModule;
import com.ning.billing.beatrix.integration.TestIntegrationBase;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.overdue.config.OverdueConfig;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.util.config.catalog.XMLLoader;

public abstract class TestOverdueBase extends TestIntegrationBase {


    protected Account account;
    protected SubscriptionBundle bundle;
    protected String productName;
    protected BillingPeriod term;


    public abstract String getOverdueConfig();

    final PaymentMethodPlugin paymentMethodPlugin = new PaymentMethodPlugin() {
        @Override
        public boolean isDefaultPaymentMethod() {
            return false;
        }

        @Override
        public String getValueString(final String key) {
            return null;
        }

        @Override
        public List<PaymentMethodKVInfo> getProperties() {
            return null;
        }

        @Override
        public String getExternalPaymentMethodId() {
            return UUID.randomUUID().toString();
        }
    };

    @BeforeMethod(groups = "slow")
    public void setupOverdue() throws Exception {
        final String configXml = getOverdueConfig();
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final OverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, OverdueConfig.class);
        overdueWrapperFactory.setOverdueConfig(config);

        account = createAccountWithPaymentMethod(getAccountData(0));
        assertNotNull(account);

        paymentApi.addPaymentMethod(BeatrixIntegrationModule.PLUGIN_NAME, account, true, paymentMethodPlugin, callContext);

        bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", callContext);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;

        paymentPlugin.clear();
    }


    protected void checkODState(final String expected) {
        try {
            // This will test the overdue notification queue: when we move the clock, the overdue system
            // should get notified to refresh its state.
            // Calling explicitly refresh here (overdueApi.refreshOverdueStateFor(bundle)) would not fully
            // test overdue.
            // Since we're relying on the notification queue, we may need to wait a bit (hence await()).
            await().atMost(10, SECONDS).until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return expected.equals(blockingApi.getBlockingStateFor(bundle, internalCallContext).getStateName());
                }
            });
        } catch (Exception e) {
            Assert.assertEquals(blockingApi.getBlockingStateFor(bundle, internalCallContext).getStateName(), expected, "Got exception: " + e.toString());
        }
    }


}
