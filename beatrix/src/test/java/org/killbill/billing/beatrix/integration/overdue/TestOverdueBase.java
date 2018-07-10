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

import org.killbill.billing.account.api.Account;
import org.killbill.billing.beatrix.integration.BeatrixIntegrationModule;
import org.killbill.billing.beatrix.integration.TestIntegrationBase;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.TestPaymentMethodPluginBase;
import org.killbill.xmlloader.XMLLoader;
import org.testng.annotations.BeforeMethod;

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
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        final String configXml = getOverdueConfig();
        final InputStream is = new ByteArrayInputStream(configXml.getBytes());
        final DefaultOverdueConfig config = XMLLoader.getObjectFromStreamNoValidation(is, DefaultOverdueConfig.class);
        overdueConfigCache.loadDefaultOverdueConfig(config);

        productName = "Shotgun";
        term = BillingPeriod.MONTHLY;
        paymentPlugin.clear();
    }

    protected void setupAccount() throws Exception {
        account = createAccountWithNonOsgiPaymentMethod(getAccountData(0));
        assertNotNull(account);

        paymentApi.addPaymentMethod(account, UUID.randomUUID().toString(), BeatrixIntegrationModule.NON_OSGI_PLUGIN_NAME, true, paymentMethodPlugin, PLUGIN_PROPERTIES, callContext);
    }

    protected void checkODState(final String expected) {
        checkODState(expected, account.getId());
    }
}
