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

package org.killbill.billing.payment.core;

import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentMethodProcessorRefreshWithDB extends PaymentTestSuiteWithEmbeddedDB {

    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();
        getPluginApi().resetPaymentMethods(null, null, PLUGIN_PROPERTIES, callContext);
    }

    @Test(groups = "slow")
    public void testRefreshWithNewPaymentMethod() throws Exception {
        final Account account = testHelper.createTestAccount("foo@bar.com", true);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, PLUGIN_PROPERTIES, callContext).size(), 1);
        final UUID existingPMId = account.getPaymentMethodId();

        // Add new payment in plugin directly
        final UUID newPmId = UUID.randomUUID();
        getPluginApi().addPaymentMethod(account.getId(), newPmId, new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, ImmutableList.<PluginProperty>of()), false, PLUGIN_PROPERTIES, callContext);

        // Verify that the refresh does indeed show 2 PMs
        final List<PaymentMethod> methods = paymentMethodProcessor.refreshPaymentMethods(MockPaymentProviderPlugin.PLUGIN_NAME, account, PLUGIN_PROPERTIES, callContext, internalCallContext);
        Assert.assertEquals(methods.size(), 2);
        checkPaymentMethodExistsWithStatus(methods, existingPMId, true);
        checkPaymentMethodExistsWithStatus(methods, newPmId, true);
    }

    @Test(groups = "slow")
    public void testRefreshWithDeletedPaymentMethod() throws Exception {
        final Account account = testHelper.createTestAccount("super@bar.com", true);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, PLUGIN_PROPERTIES, callContext).size(), 1);
        final UUID firstPmId = account.getPaymentMethodId();

        String secondPaymentMethodExternalKey = UUID.randomUUID().toString();
        final UUID secondPmId = paymentApi.addPaymentMethod(account, secondPaymentMethodExternalKey, MockPaymentProviderPlugin.PLUGIN_NAME, true, new DefaultNoOpPaymentMethodPlugin(secondPaymentMethodExternalKey, false, null), PLUGIN_PROPERTIES, callContext);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, PLUGIN_PROPERTIES, callContext).size(), 2);
        Assert.assertEquals(paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext).size(), 2);

        // Remove second PM from plugin
        getPluginApi().deletePaymentMethod(account.getId(), secondPmId, PLUGIN_PROPERTIES, callContext);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, PLUGIN_PROPERTIES, callContext).size(), 1);
        Assert.assertEquals(paymentApi.getAccountPaymentMethods(account.getId(), false, false, PLUGIN_PROPERTIES, callContext).size(), 2);

        // Verify that the refresh sees that PM as being deleted now
        final List<PaymentMethod> methods = paymentMethodProcessor.refreshPaymentMethods(MockPaymentProviderPlugin.PLUGIN_NAME, account, PLUGIN_PROPERTIES, callContext, internalCallContext);
        Assert.assertEquals(methods.size(), 1);
        checkPaymentMethodExistsWithStatus(methods, firstPmId, true);

        final PaymentMethodModelDao deletedPMModel = paymentDao.getPaymentMethodIncludedDeleted(secondPmId, internalCallContext);
        Assert.assertNotNull(deletedPMModel);
        Assert.assertFalse(deletedPMModel.isActive());
    }

    private void checkPaymentMethodExistsWithStatus(final List<PaymentMethod> methods, final UUID expectedPaymentMethodId, final boolean expectedActive) {
        PaymentMethod foundPM = null;
        for (final PaymentMethod cur : methods) {
            if (cur.getId().equals(expectedPaymentMethodId)) {
                foundPM = cur;
                break;
            }
        }
        Assert.assertNotNull(foundPM);
        Assert.assertEquals(foundPM.isActive().booleanValue(), expectedActive);
    }

    private PaymentPluginApi getPluginApi() {
        final PaymentPluginApi pluginApi = registry.getServiceForName(MockPaymentProviderPlugin.PLUGIN_NAME);
        Assert.assertNotNull(pluginApi);
        return pluginApi;
    }
}
