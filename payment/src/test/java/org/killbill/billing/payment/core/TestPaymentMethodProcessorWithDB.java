/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.payment.dao.PaymentMethodSqlDao;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.dao.EntityHistoryModelDao;
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

    @Test(groups = "slow")
    public void testDeletePaymentMethod() throws Exception {
        final Account account = testHelper.createTestAccount("foo@bar.com", true);

        final UUID paymentMethodId = paymentMethodProcessor.createOrGetExternalPaymentMethod("pmExternalKey", account, PLUGIN_PROPERTIES, callContext, internalCallContext);
        final PaymentMethodModelDao paymentMethodModelDao = paymentDao.getPaymentMethod(paymentMethodId, internalCallContext);

        final List<EntityHistoryModelDao<PaymentMethodModelDao, PaymentMethod>> history1 = getPaymentMethodHistory(paymentMethodModelDao.getRecordId());
        Assert.assertEquals(history1.size(), 1);
        Assert.assertEquals(history1.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(history1.get(0).getEntity().getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history1.get(0).getEntity().getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history1.get(0).getEntity().getExternalKey(), paymentMethodModelDao.getExternalKey());
        Assert.assertTrue(history1.get(0).getEntity().isActive());

        paymentMethodProcessor.deletedPaymentMethod(account, paymentMethodId, true, true, ImmutableList.<PluginProperty>of(), callContext, internalCallContext);

        final List<EntityHistoryModelDao<PaymentMethodModelDao, PaymentMethod>> history2 = getPaymentMethodHistory(paymentMethodModelDao.getRecordId());
        Assert.assertEquals(history2.size(), 2);
        Assert.assertEquals(history2.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(history2.get(0).getEntity().getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history2.get(0).getEntity().getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history2.get(0).getEntity().getExternalKey(), paymentMethodModelDao.getExternalKey());
        Assert.assertTrue(history2.get(0).getEntity().isActive());
        // Note: it looks like we don't consider this as a DELETE, probably because we can un-delete such payment methods?
        Assert.assertEquals(history2.get(1).getChangeType(), ChangeType.UPDATE);
        Assert.assertEquals(history2.get(1).getEntity().getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history2.get(1).getEntity().getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history2.get(1).getEntity().getExternalKey(), paymentMethodModelDao.getExternalKey());
        // Note: upon deletion, the recorded state is the same as before the delete
        Assert.assertTrue(history2.get(1).getEntity().isActive());
    }

    private List<EntityHistoryModelDao<PaymentMethodModelDao, PaymentMethod>> getPaymentMethodHistory(final Long paymentMethodRecordId) {
        // See https://github.com/killbill/killbill/issues/335
        final PaymentMethodSqlDao paymentMethodSqlDao = dbi.onDemand(PaymentMethodSqlDao.class);
        return paymentMethodSqlDao.getHistoryForTargetRecordId(paymentMethodRecordId, internalCallContext);
    }
}
