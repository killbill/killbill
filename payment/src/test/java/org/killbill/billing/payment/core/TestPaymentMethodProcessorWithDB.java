/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestPaymentMethodProcessorWithDB extends PaymentTestSuiteWithEmbeddedDB {

    private static final ImmutableList<PluginProperty> PLUGIN_PROPERTIES = ImmutableList.<PluginProperty>of();

    @Test(groups = "slow", expectedExceptions = PaymentApiException.class, expectedExceptionsMessageRegExp = ".*Payment method .* has a different account id")
    public void testSetDefaultPaymentMethodDifferentAccount() throws Exception {
        final Account account = testHelper.createTestAccount("foo@bar.com", true);
        final Account secondAccount = testHelper.createTestAccount("foo2@bar.com", true);

        // Add new payment method
        final UUID newPaymentMethod = paymentMethodProcessor.createOrGetExternalPaymentMethod("pmExternalKey", secondAccount, PLUGIN_PROPERTIES, callContext, internalCallContext);

        paymentMethodProcessor.setDefaultPaymentMethod(account, newPaymentMethod, PLUGIN_PROPERTIES, callContext, internalCallContext);
    }

    @Test(groups = "slow")
    public void testSetDefaultPaymentMethodSameAccount() throws Exception {
        final Account account = testHelper.createTestAccount("foo@bar.com", true);

        // Add new payment method
        final UUID newPaymentMethod = paymentMethodProcessor.createOrGetExternalPaymentMethod("pmExternalKey", account, PLUGIN_PROPERTIES, callContext, internalCallContext);

        paymentMethodProcessor.setDefaultPaymentMethod(account, newPaymentMethod, PLUGIN_PROPERTIES, callContext, internalCallContext);

        final Account accountById = accountApi.getAccountById(account.getId(), internalCallContext);
        Assert.assertEquals(accountById.getPaymentMethodId(), newPaymentMethod);
    }
}
