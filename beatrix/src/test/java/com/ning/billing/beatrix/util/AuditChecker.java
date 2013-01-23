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

package com.ning.billing.beatrix.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.tweak.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.dao.AccountSqlDao;
import com.ning.billing.entitlement.api.timeline.EntitlementRepairException;
import com.ning.billing.entitlement.engine.dao.BundleSqlDao;
import com.ning.billing.entitlement.engine.dao.EntitlementEventSqlDao;
import com.ning.billing.entitlement.engine.dao.SubscriptionSqlDao;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.dao.InvoiceItemSqlDao;
import com.ning.billing.invoice.dao.InvoiceSqlDao;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.dao.PaymentSqlDao;
import com.ning.billing.util.api.AuditLevel;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.audit.AuditLog;
import com.ning.billing.util.audit.AuditLogsForAccount;
import com.ning.billing.util.audit.AuditLogsForBundles;
import com.ning.billing.util.audit.AuditLogsForInvoices;
import com.ning.billing.util.audit.AuditLogsForPayments;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.callcontext.UserType;
import com.ning.billing.util.dao.NonEntityDao;
import com.ning.billing.util.dao.TableName;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.entity.dao.EntityModelDao;
import com.ning.billing.util.entity.dao.EntitySqlDao;

import com.google.inject.Inject;

public class AuditChecker {

    private static final Logger log = LoggerFactory.getLogger(AuditChecker.class);

    private final AuditUserApi auditUserApi;
    private final IDBI dbi;
    private final InternalCallContextFactory callContextFactory;
    private final NonEntityDao nonEntityDao;


    @Inject
    public AuditChecker(final AuditUserApi auditUserApi, final IDBI dbi, final InternalCallContextFactory callContextFactory, final NonEntityDao nonEntityDao) {
        this.auditUserApi = auditUserApi;
        this.dbi = dbi;
        this.callContextFactory = callContextFactory;
        this.nonEntityDao = nonEntityDao;
    }


    /***********************************************  ACCOUNT ********************************************************/

    public void checkAccountCreated(final Account account, final CallContext context) {
        AuditLogsForAccount result = auditUserApi.getAuditLogsForAccount(account.getId(), AuditLevel.FULL, context);
        checkAuditLog(ChangeType.INSERT, context, result.getAccountAuditLogs().get(0), account.getId(), AccountSqlDao.class, true, true);
        checkAuditLog(ChangeType.UPDATE, context, result.getAccountAuditLogs().get(1), account.getId(), AccountSqlDao.class, true, true);
    }


    public void checkPaymentCreated(final Payment payment, final CallContext context) {
        AuditLogsForPayments result = getAuditLogsForPayment(payment, context);
        Assert.assertEquals(result.getPaymentsAuditLogs().size(), 1);
        final List<AuditLog> paymentLogs = result.getPaymentsAuditLogs().get(payment.getId());
        Assert.assertEquals(paymentLogs.size(), 2);
        checkAuditLog(ChangeType.INSERT, context, paymentLogs.get(0), payment.getId(), PaymentSqlDao.class, true, false);
        checkAuditLog(ChangeType.UPDATE, context, paymentLogs.get(1), payment.getId(), PaymentSqlDao.class, true, false);
    }

    /***********************************************  BUNDLE ********************************************************/


    // Pass the call context used to create the bundle
    public void checkBundleCreated(final UUID bundleId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().keySet().size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getBundlesAuditLogs().get(bundleId).get(0), bundleId, BundleSqlDao.class, false, false);
    }

    // Pass the call context used to update the bundle
    public void checkBundleUpdated(final UUID bundleId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        Assert.assertEquals(auditLogsForBundles.getBundlesAuditLogs().keySet().size(), 1);
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getBundlesAuditLogs().get(bundleId).get(auditLogsForBundles.getBundlesAuditLogs().get(bundleId).size() - 1), bundleId, BundleSqlDao.class, false, false);
    }

    /***********************************************  SUBSCRIPTION ********************************************************/

    // Pass the call context used to create the subscription
    public void checkSubscriptionCreated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().keySet().size(), 1);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(0), subscriptionId, SubscriptionSqlDao.class, false, true);
    }

    // Pass the call context used to update the subscription
    public void checkSubscriptionUpdated(final UUID bundleId, final UUID subscriptionId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);

        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().keySet().size(), 1);
        Assert.assertEquals(auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).size(), 2);
        checkAuditLog(ChangeType.INSERT, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getSubscriptionsAuditLogs().get(subscriptionId).get(1), subscriptionId, SubscriptionSqlDao.class, false, false);
    }

    /***********************************************  SUBSCRIPTION EVENTS ********************************************************/

    // Pass the call context used to create the subscription event
    public void checkSubscriptionEventCreated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        checkAuditLog(ChangeType.INSERT, context, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(0), subscriptionEventId, EntitlementEventSqlDao.class, false, true);
    }

    // Pass the call context used to update the subscription event
    public void checkSubscriptionEventUpdated(final UUID bundleId, final UUID subscriptionEventId, final CallContext context) {
        final AuditLogsForBundles auditLogsForBundles = getAuditLogsForBundle(bundleId, context);
        checkAuditLog(ChangeType.INSERT, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(0));
        checkAuditLog(ChangeType.UPDATE, context, auditLogsForBundles.getSubscriptionEventsAuditLogs().get(subscriptionEventId).get(1), subscriptionEventId, EntitlementEventSqlDao.class, false, true);
    }



    /***********************************************  PAYMENT ********************************************************/

    private AuditLogsForPayments getAuditLogsForPayment(final Payment payment, final CallContext context) {
        AuditLogsForPayments results = auditUserApi.getAuditLogsForPayments(Collections.singletonList(payment), AuditLevel.FULL, context);
        return results;
    }

    /***********************************************  INVOICE ********************************************************/

    public void checkInvoiceCreated(final Invoice invoice, final CallContext context) {
        AuditLogsForInvoices result = getAuditLogForInvoice(invoice, context);
        Assert.assertEquals(result.getInvoiceAuditLogs().keySet().size(), 1);
        final List<AuditLog> invoiceLogs = result.getInvoiceAuditLogs().get(invoice.getId());
        Assert.assertEquals(invoiceLogs.size(), 1);
        checkAuditLog(ChangeType.INSERT, context, invoiceLogs.get(0), invoice.getId(), InvoiceSqlDao.class, false, false);

        Assert.assertEquals(result.getInvoiceItemsAuditLogs().keySet().size(), invoice.getInvoiceItems().size());
        for (InvoiceItem cur : invoice.getInvoiceItems()) {
            final List<AuditLog> auditLogs = result.getInvoiceItemsAuditLogs().get(cur.getId());
            Assert.assertEquals(auditLogs.size(), 1);
            checkAuditLog(ChangeType.INSERT, context, auditLogs.get(0), cur.getId(), InvoiceItemSqlDao.class, false, false);
        }
    }




    private AuditLogsForBundles getAuditLogsForBundle(final UUID bundleId, final CallContext context) {
        try {
            return auditUserApi.getAuditLogsForBundle(bundleId, AuditLevel.FULL, context);
        } catch (EntitlementRepairException e) {
            Assert.fail(e.toString());
            return null;
        }
    }

    private AuditLogsForInvoices getAuditLogForInvoice(final Invoice invoice, final CallContext context) {
        return auditUserApi.getAuditLogsForInvoices(Collections.singletonList(invoice), AuditLevel.FULL, context);
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
            //Assert.assertEquals(auditLog.getCreatedDate().comparesTo(context.getCreatedDate()));
            // We can't take userToken oustide of the 'if' because for instance NextBillingDate invoice will not have it.
            Assert.assertEquals(auditLog.getUserToken(), context.getUserToken().toString());
        }
        final M entityModel = extractEntityModelFromEntityWithTargetRecordId(entityId, auditLog.getId(), sqlDao, context, useHistory);
        Assert.assertEquals(entityModel.getId(), entityId);

    }


    private <T extends EntitySqlDao<M, E>, M extends EntityModelDao<E>, E extends Entity> M extractEntityModelFromEntityWithTargetRecordId(final UUID entityId, final UUID auditLogId, final Class<T> sqlDao, final CallContext context, final boolean useHistory) {


        final M modelDaoThatGivesMeTableName = dbi.onDemand(sqlDao).getById(entityId.toString(), callContextFactory.createInternalCallContext(context));

        Integer targetRecordId = dbi.withHandle(new HandleCallback<Integer>() {
            @Override
            public Integer withHandle(final Handle handle) throws Exception {

                List<Map<String, Object>> res = handle.select("select target_record_id from audit_log where id = '" + auditLogId.toString() + "';");
                return (Integer) res.get(0).get("target_record_id");
            }
        });

        if (useHistory) {
            Long entityRecordId = nonEntityDao.retrieveHistoryTargetRecordId(Long.valueOf(targetRecordId), modelDaoThatGivesMeTableName.getHistoryTableName());
            targetRecordId = new Integer(entityRecordId.intValue());
        }
        return dbi.onDemand(sqlDao).getByRecordId(Long.valueOf(targetRecordId), callContextFactory.createInternalCallContext(context));
    }

}
