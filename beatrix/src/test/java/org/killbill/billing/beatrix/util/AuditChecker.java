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

package org.killbill.billing.beatrix.util;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.billing.account.api.Account;
import org.killbill.billing.account.dao.AccountSqlDao;
import org.killbill.billing.entitlement.api.SubscriptionApi;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.api.SubscriptionBundle;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.dao.InvoiceItemSqlDao;
import org.killbill.billing.invoice.dao.InvoiceSqlDao;
import org.killbill.billing.payment.api.Payment;
import org.killbill.billing.payment.api.PaymentTransaction;
import org.killbill.billing.payment.dao.PaymentSqlDao;
import org.killbill.billing.payment.dao.TransactionSqlDao;
import org.killbill.billing.subscription.engine.dao.BundleSqlDao;
import org.killbill.billing.subscription.engine.dao.SubscriptionEventSqlDao;
import org.killbill.billing.subscription.engine.dao.SubscriptionSqlDao;
import org.killbill.billing.util.api.AuditLevel;
import org.killbill.billing.util.api.AuditUserApi;
import org.killbill.billing.util.audit.AccountAuditLogs;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.ChangeType;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.dao.NonEntityDao;
import org.killbill.billing.util.entity.Entity;
import org.killbill.billing.util.entity.dao.EntityModelDao;
import org.killbill.billing.util.entity.dao.EntitySqlDao;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.google.inject.Inject;

public class AuditChecker {

    private static final Logger log = LoggerFactory.getLogger(AuditChecker.class);

    private final AuditUserApi auditUserApi;
    private final IDBI dbi;
    private final SubscriptionApi subscriptionApi;
    private final InternalCallContextFactory callContextFactory;
    private final NonEntityDao nonEntityDao;

    @Inject
    public AuditChecker(final AuditUserApi auditUserApi, final IDBI dbi, final SubscriptionApi subscriptionApi, final InternalCallContextFactory callContextFactory, final NonEntityDao nonEntityDao) {
        this.auditUserApi = auditUserApi;
        this.dbi = dbi;
        this.subscriptionApi = subscriptionApi;
        this.callContextFactory = callContextFactory;
        this.nonEntityDao = nonEntityDao;
    }

    /**
     * ********************************************  ACCOUNT *******************************************************
     */

    public void checkAccountCreated(final Account account, final CallContext context) {
        final AccountAuditLogs result = auditUserApi.getAccountAuditLogs(account.getId(), AuditLevel.FULL, context);
        checkAuditLog(ChangeType.INSERT, context, result.getAuditLogsForAccount().get(0), account.getId(), AccountSqlDao.class, true, true);
        checkAuditLog(ChangeType.UPDATE, context, result.getAuditLogsForAccount().get(1), account.getId(), AccountSqlDao.class, true, true);
    }

    /**
     * ********************************************  BUNDLE *******************************************************
     */

    // Pass the call callcontext used to create the bundle
    public void checkBundleCreated(final UUID bundleId, final CallContext context) {
        final List<AuditLog> auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        Assert.assertTrue(auditLogsForBundles.size() >= 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.get(0), bundleId, BundleSqlDao.class, false, false);
    }

    // Pass the call callcontext used to update the bundle
    public void checkBundleUpdated(final UUID bundleId, final CallContext context) {
        final List<AuditLog> auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        Assert.assertTrue(auditLogsForBundles.size() > 1);
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.get(auditLogsForBundles.size() - 1), bundleId, BundleSqlDao.class, false, false);
    }

    /**
     * ********************************************  SUBSCRIPTION *******************************************************
     */

    // Pass the call callcontext used to create the subscription
    public void checkSubscriptionCreated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final List<AuditLog> auditLogsForSubscription = getAuditLogsForSubscription(bundleId, subscriptionId, context);
        Assert.assertTrue(auditLogsForSubscription.size() >= 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForSubscription.get(0), subscriptionId, SubscriptionSqlDao.class, false, true);
    }

    // Pass the call callcontext used to update the subscription
    public void checkSubscriptionUpdated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final List<AuditLog> auditLogsForSubscription = getAuditLogsForSubscription(bundleId, subscriptionId, context);
        Assert.assertEquals(auditLogsForSubscription.size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForSubscription.get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForSubscription.get(1), subscriptionId, SubscriptionSqlDao.class, false, false);
    }

    /**
     * ********************************************  SUBSCRIPTION EVENTS *******************************************************
     */

    // Pass the call callcontext used to create the subscription event
    public void checkSubscriptionEventCreated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final List<AuditLog> auditLogsForSubscriptionEvent = getAuditLogsForSubscriptionEvent(bundleId, subscriptionEventId, context);
        Assert.assertEquals(auditLogsForSubscriptionEvent.size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForSubscriptionEvent.get(0), subscriptionEventId, SubscriptionEventSqlDao.class, false, true);
    }

    // Pass the call callcontext used to update the subscription event
    public void checkSubscriptionEventUpdated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final List<AuditLog> auditLogsForSubscriptionEvent = getAuditLogsForSubscriptionEvent(bundleId, subscriptionEventId, context);
        Assert.assertEquals(auditLogsForSubscriptionEvent.size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForSubscriptionEvent.get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForSubscriptionEvent.get(1), subscriptionEventId, SubscriptionEventSqlDao.class, false, true);
    }

    /**
     * ********************************************  INVOICE *******************************************************
     */

    public void checkInvoiceCreated(final Invoice invoice, final CallContext context) {
        final List<AuditLog> invoiceLogs = getAuditLogForInvoice(invoice, context);
        //Assert.assertEquals(invoiceLogs.size(), 1);
        checkAuditLog(ChangeType.INSERT, context, invoiceLogs.get(0), invoice.getId(), InvoiceSqlDao.class, false, false);

        for (InvoiceItem cur : invoice.getInvoiceItems()) {
            final List<AuditLog> auditLogs = getAuditLogForInvoiceItem(cur, context);
            Assert.assertTrue(auditLogs.size() >= 1);
            checkAuditLog(ChangeType.INSERT, context, auditLogs.get(0), cur.getId(), InvoiceItemSqlDao.class, false, false);
        }
    }

    /**
     * ********************************************  PAYMENT *******************************************************
     */

    public void checkPaymentCreated(final Payment payment, final CallContext context) {
        final List<AuditLog> paymentLogs = getAuditLogForPayment(payment, context);
        Assert.assertEquals(paymentLogs.size(), 2);
        checkAuditLog(ChangeType.INSERT, context, paymentLogs.get(0), payment.getId(), PaymentSqlDao.class, true, false);
        checkAuditLog(ChangeType.UPDATE, context, paymentLogs.get(1), payment.getId(), PaymentSqlDao.class, true, false);

        for (PaymentTransaction cur : payment.getTransactions()) {
            final List<AuditLog> auditLogs = getAuditLogForPaymentTransaction(payment, cur, context);
            Assert.assertEquals(auditLogs.size(), 2);
            checkAuditLog(ChangeType.INSERT, context, auditLogs.get(0), cur.getId(), TransactionSqlDao.class, true, false);
            checkAuditLog(ChangeType.UPDATE, context, auditLogs.get(1), cur.getId(), TransactionSqlDao.class, true, false);
        }
    }

    private List<AuditLog> getAuditLogsForBundle(final UUID bundleId, final TenantContext context) {
        try {
            final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, context);
            return auditUserApi.getAccountAuditLogs(bundle.getAccountId(), AuditLevel.FULL, context).getAuditLogsForBundle(bundle.getId());
        } catch (final SubscriptionApiException e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private List<AuditLog> getAuditLogsForSubscription(final UUID bundleId, final UUID subscriptionId, final TenantContext context) {
        try {
            final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, context);
            return auditUserApi.getAccountAuditLogs(bundle.getAccountId(), AuditLevel.FULL, context).getAuditLogsForSubscription(subscriptionId);
        } catch (final SubscriptionApiException e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private List<AuditLog> getAuditLogsForSubscriptionEvent(final UUID bundleId, final UUID subscriptionEventId, final TenantContext context) {
        try {
            final SubscriptionBundle bundle = subscriptionApi.getSubscriptionBundle(bundleId, context);
            return auditUserApi.getAccountAuditLogs(bundle.getAccountId(), AuditLevel.FULL, context).getAuditLogsForSubscriptionEvent(subscriptionEventId);
        } catch (final SubscriptionApiException e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private List<AuditLog> getAuditLogForInvoice(final Invoice invoice, final TenantContext context) {
        return auditUserApi.getAccountAuditLogs(invoice.getAccountId(), AuditLevel.FULL, context).getAuditLogsForInvoice(invoice.getId());
    }

    private List<AuditLog> getAuditLogForInvoiceItem(final InvoiceItem invoiceItem, final TenantContext context) {
        return auditUserApi.getAccountAuditLogs(invoiceItem.getAccountId(), AuditLevel.FULL, context).getAuditLogsForInvoiceItem(invoiceItem.getId());
    }

    private List<AuditLog> getAuditLogForPayment(final Payment payment, final TenantContext context) {
        return auditUserApi.getAccountAuditLogs(payment.getAccountId(), AuditLevel.FULL, context).getAuditLogsForPayment(payment.getId());
    }

    private List<AuditLog> getAuditLogForPaymentTransaction(final Payment payment, final PaymentTransaction paymentTransaction, final TenantContext context) {
        return auditUserApi.getAccountAuditLogs(payment.getAccountId(), AuditLevel.FULL, context).getAuditLogsForPaymentTransaction(paymentTransaction.getId());
    }

    private void checkAuditLog(final ChangeType insert, final AuditLog auditLog) {
        checkAuditLog(insert, null, auditLog, null, EntitySqlDao.class, false, false);
    }

    private <T extends EntitySqlDao<M, E>, M extends EntityModelDao<E>, E extends Entity> void checkAuditLog(final ChangeType changeType, @Nullable final CallContext context, final AuditLog auditLog, final UUID entityId, Class<T> sqlDao,
                                                                                                             boolean useHistory, boolean checkContext) {
        Assert.assertEquals(auditLog.getChangeType(), changeType);

        if (checkContext) {
            Assert.assertEquals(auditLog.getUserName(), context.getUserName());
            Assert.assertEquals(auditLog.getComment(), context.getComments());
            //Assert.assertEquals(auditLog.getCreatedDate().comparesTo(callcontext.getCreatedDate()));
            // We can't take userToken oustide of the 'if' because for instance NextBillingDate invoice will not have it.
            Assert.assertEquals(auditLog.getUserToken(), context.getUserToken().toString());
        }
        final M entityModel = extractEntityModelFromEntityWithTargetRecordId(entityId, auditLog.getId(), sqlDao, context, useHistory);
        Assert.assertEquals(entityModel.getId(), entityId);

    }

    private <T extends EntitySqlDao<M, E>, M extends EntityModelDao<E>, E extends Entity> M extractEntityModelFromEntityWithTargetRecordId(final UUID entityId, final UUID auditLogId, final Class<T> sqlDao, final CallContext context, final boolean useHistory) {

        final M modelDaoThatGivesMeTableName = dbi.onDemand(sqlDao).getById(entityId.toString(), callContextFactory.createInternalCallContextWithoutAccountRecordId(context));

        Integer targetRecordId = dbi.withHandle(new HandleCallback<Integer>() {
            @Override
            public Integer withHandle(final Handle handle) throws Exception {

                List<Map<String, Object>> res = handle.select("select target_record_id from audit_log where id = '" + auditLogId.toString() + "';");
                return Integer.valueOf(res.get(0).get("target_record_id").toString());
            }
        });

        if (useHistory) {
            Long entityRecordId = nonEntityDao.retrieveHistoryTargetRecordId(Long.valueOf(targetRecordId), modelDaoThatGivesMeTableName.getHistoryTableName());
            targetRecordId = new Integer(entityRecordId.intValue());
        }
        return dbi.onDemand(sqlDao).getByRecordId(Long.valueOf(targetRecordId), callContextFactory.createInternalCallContextWithoutAccountRecordId(context));
    }

}
