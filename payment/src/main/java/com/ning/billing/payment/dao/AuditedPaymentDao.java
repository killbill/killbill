/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing.payment.dao;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.audit.dao.AuditSqlDao;
import com.ning.billing.util.callcontext.CallContext;
import org.apache.commons.lang.Validate;
import org.skife.jdbi.v2.IDBI;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentInfo;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

public class AuditedPaymentDao implements PaymentDao {
    private final PaymentSqlDao sqlDao;

    @Inject
    public AuditedPaymentDao(IDBI dbi) {
        this.sqlDao = dbi.onDemand(PaymentSqlDao.class);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(String paymentId) {
        return sqlDao.getPaymentAttemptForPaymentId(paymentId);
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(String invoiceId) {
        return sqlDao.getPaymentAttemptsForInvoiceId(invoiceId);
    }

    @Override
    public PaymentAttempt createPaymentAttempt(final PaymentAttempt paymentAttempt, final CallContext context) {
        return sqlDao.inTransaction(new Transaction<PaymentAttempt, PaymentSqlDao>() {
            @Override
            public PaymentAttempt inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.insertPaymentAttempt(paymentAttempt, context);
                PaymentAttempt savedPaymentAttempt = transactional.getPaymentAttemptById(paymentAttempt.getPaymentAttemptId().toString());
                UUID historyRecordId = UUID.randomUUID();
                transactional.insertPaymentAttemptHistory(historyRecordId.toString(), paymentAttempt, context);
                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction("payment_attempt", historyRecordId.toString(),
                                                       ChangeType.INSERT.toString(), context);
                return savedPaymentAttempt;
            }
        });
    }

    @Override
    public PaymentAttempt createPaymentAttempt(final Invoice invoice, final CallContext context) {
        return sqlDao.inTransaction(new Transaction<PaymentAttempt, PaymentSqlDao>() {
            @Override
            public PaymentAttempt inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {
                final PaymentAttempt paymentAttempt = new PaymentAttempt(UUID.randomUUID(), invoice);
                transactional.insertPaymentAttempt(paymentAttempt, context);
                UUID historyRecordId = UUID.randomUUID();
                transactional.insertPaymentAttemptHistory(historyRecordId.toString(), paymentAttempt, context);
                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction("payment_attempt", historyRecordId.toString(),
                                                       ChangeType.INSERT.toString(), context);

                return paymentAttempt;
            }
        });
    }

    @Override
    public void savePaymentInfo(final PaymentInfo info, final CallContext context) {
        sqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {
            @Override
            public Void inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.insertPaymentInfo(info, context);
                UUID historyRecordId = UUID.randomUUID();
                transactional.insertPaymentInfoHistory(historyRecordId.toString(), info, context);
                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction("payment", historyRecordId.toString(),
                                                       ChangeType.INSERT.toString(), context);

                return null;
            }
        });
    }

    @Override
    public void updatePaymentAttemptWithPaymentId(final UUID paymentAttemptId, final String paymentId, final CallContext context) {
        sqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {
            @Override
            public Void inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.updatePaymentAttemptWithPaymentId(paymentAttemptId.toString(), paymentId, context);
                PaymentAttempt paymentAttempt = transactional.getPaymentAttemptById(paymentAttemptId.toString());
                UUID historyRecordId = UUID.randomUUID();
                transactional.insertPaymentAttemptHistory(historyRecordId.toString(), paymentAttempt, context);
                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction("payment_attempt", historyRecordId.toString(),
                                                       ChangeType.UPDATE.toString(), context);

                return null;
            }
        });
    }

    @Override
    public void updatePaymentInfo(final String type, final String paymentId, final String cardType,
                                  final String cardCountry, final CallContext context) {
        sqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {
            @Override
            public Void inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.updatePaymentInfo(type, paymentId, cardType, cardCountry, context);
                PaymentInfo paymentInfo = transactional.getPaymentInfo(paymentId.toString());
                UUID historyRecordId = UUID.randomUUID();
                transactional.insertPaymentInfoHistory(historyRecordId.toString(), paymentInfo, context);
                AuditSqlDao auditSqlDao = transactional.become(AuditSqlDao.class);
                auditSqlDao.insertAuditFromTransaction("payments", historyRecordId.toString(),
                                                       ChangeType.UPDATE.toString(), context);

                return null;
            }
        });
    }

    @Override
    public List<PaymentInfo> getPaymentInfo(List<String> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentInfo>of();
        } else {
            return sqlDao.getPaymentInfos(invoiceIds);
        }
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<String> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentAttempt>of();
        } else {
            return sqlDao.getPaymentAttemptsForInvoiceIds(invoiceIds);
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId) {
        return sqlDao.getPaymentAttemptById(paymentAttemptId.toString());
    }

    @Override
    public PaymentInfo getPaymentInfoForPaymentAttemptId(String paymentAttemptIdStr) {
        return sqlDao.getPaymentInfoForPaymentAttemptId(paymentAttemptIdStr);
    }

}
