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

package org.killbill.billing.payment.core;

import java.util.List;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentMethodKVInfo;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.payment.provider.DefaultNoOpPaymentMethodPlugin;
import org.killbill.billing.payment.provider.MockPaymentProviderPlugin;

import com.google.common.collect.ImmutableList;

public class TestPaymentMethodProcessorRefreshWithDB extends PaymentTestSuiteWithEmbeddedDB {


    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        getPluginApi().resetPaymentMethods(null, null);
    }

    @Test(groups = "slow")
    public void testRefreshWithNewPaymentMethod() throws Exception {

        final Account account = testHelper.createTestAccount("foo@bar.com", true);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, callContext).size(), 1);
        final UUID existingPMId = account.getPaymentMethodId();

        // Add new payment in plugin directly
        final UUID newPmId = UUID.randomUUID();
        getPluginApi().addPaymentMethod(account.getId(), newPmId, new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, ImmutableList.<PaymentMethodKVInfo>of()), false, callContext);

        // Verify that the refresh does indeed show 2 PMs
        final List<PaymentMethod> methods = paymentMethodProcessor.refreshPaymentMethods(MockPaymentProviderPlugin.PLUGIN_NAME, account, internalCallContext);
        Assert.assertEquals(methods.size(), 2);
        checkPaymentMethodExistsWithStatus(methods, existingPMId, true);
        checkPaymentMethodExistsWithStatus(methods, newPmId, true);
    }


    @Test(groups = "slow")
    public void testRefreshWithDeletedPaymentMethod() throws Exception {

        final Account account = testHelper.createTestAccount("super@bar.com", true);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, callContext).size(), 1);
        final UUID firstPmId = account.getPaymentMethodId();

        final UUID secondPmId = paymentApi.addPaymentMethod(MockPaymentProviderPlugin.PLUGIN_NAME, account, true, new DefaultNoOpPaymentMethodPlugin(UUID.randomUUID().toString(), false, null), callContext);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, callContext).size(), 2);
        Assert.assertEquals(paymentApi.getPaymentMethods(account, false, callContext).size(), 2);

        // Remove second PM from plugin
        getPluginApi().deletePaymentMethod(account.getId(), secondPmId, callContext);
        Assert.assertEquals(getPluginApi().getPaymentMethods(account.getId(), true, callContext).size(), 1);
        Assert.assertEquals(paymentApi.getPaymentMethods(account, false, callContext).size(), 2);

        // Verify that the refresh sees that PM as being deleted now
        final List<PaymentMethod> methods = paymentMethodProcessor.refreshPaymentMethods(MockPaymentProviderPlugin.PLUGIN_NAME, account, internalCallContext);
        Assert.assertEquals(methods.size(), 1);
        checkPaymentMethodExistsWithStatus(methods, firstPmId, true);

        PaymentMethodModelDao deletedPMModel =  paymentDao.getPaymentMethodIncludedDeleted(secondPmId, internalCallContext);
        Assert.assertNotNull(deletedPMModel);
        Assert.assertFalse(deletedPMModel.isActive());
    }


    private void checkPaymentMethodExistsWithStatus(final List<PaymentMethod> methods, UUID expectedPaymentMethodId, boolean expectedActive) {
        PaymentMethod foundPM = null;
        for (PaymentMethod cur : methods) {
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
