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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.PaymentTestSuiteWithEmbeddedDB;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.audit.ChangeType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPaymentMethodProcessorWithDB extends PaymentTestSuiteWithEmbeddedDB {

    private static final List<PluginProperty> PLUGIN_PROPERTIES = Collections.emptyList();

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

        List<AuditLogWithHistory> auditLogsWithHistory = paymentDao.getPaymentMethodAuditLogsWithHistoryForId(paymentMethodModelDao.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsWithHistory.size(), 1);

        PaymentMethodModelDao history1 = (PaymentMethodModelDao) auditLogsWithHistory.get(0).getEntity();
        Assert.assertEquals(auditLogsWithHistory.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(history1.getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history1.getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history1.getExternalKey(), paymentMethodModelDao.getExternalKey());
        Assert.assertTrue(history1.isActive());

        paymentMethodProcessor.deletedPaymentMethod(account, paymentMethodId, true, true, Collections.emptyList(), callContext, internalCallContext);

        auditLogsWithHistory = paymentDao.getPaymentMethodAuditLogsWithHistoryForId(paymentMethodModelDao.getId(), AuditLevel.FULL, internalCallContext);
        Assert.assertEquals(auditLogsWithHistory.size(), 2);

        history1 = (PaymentMethodModelDao) auditLogsWithHistory.get(0).getEntity();
        PaymentMethodModelDao history2 = (PaymentMethodModelDao) auditLogsWithHistory.get(1).getEntity();
        Assert.assertEquals(auditLogsWithHistory.get(0).getChangeType(), ChangeType.INSERT);
        Assert.assertEquals(history1.getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history1.getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history1.getExternalKey(), paymentMethodModelDao.getExternalKey());
        Assert.assertTrue(history1.isActive());
        Assert.assertEquals(auditLogsWithHistory.get(1).getChangeType(), ChangeType.DELETE);
        Assert.assertEquals(history2.getAccountRecordId(), paymentMethodModelDao.getAccountRecordId());
        Assert.assertEquals(history2.getTenantRecordId(), paymentMethodModelDao.getTenantRecordId());
        Assert.assertEquals(history2.getExternalKey(), paymentMethodModelDao.getExternalKey());
        Assert.assertFalse(history2.isActive());
    }
}
