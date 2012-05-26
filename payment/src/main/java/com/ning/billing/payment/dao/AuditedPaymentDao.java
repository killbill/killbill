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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.TableName;
import org.skife.jdbi.v2.IDBI;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.DefaultPaymentAttempt;
import com.ning.billing.payment.api.PaymentAttempt;
import com.ning.billing.payment.api.PaymentAttempt.PaymentAttemptStatus;
import com.ning.billing.payment.api.PaymentInfoEvent;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

public class AuditedPaymentDao implements PaymentDao {
    private final PaymentSqlDao paymentSqlDao;
    private final PaymentAttemptSqlDao paymentAttemptSqlDao;

    @Inject
    public AuditedPaymentDao(IDBI dbi) {
        this.paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
        this.paymentAttemptSqlDao = dbi.onDemand(PaymentAttemptSqlDao.class);
    }

    @Override
    public PaymentAttempt getPaymentAttemptForPaymentId(UUID paymentId) {
        return paymentAttemptSqlDao.getPaymentAttemptForPaymentId(paymentId.toString());
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceId(UUID invoiceId) {
        return paymentAttemptSqlDao.getPaymentAttemptsForInvoiceId(invoiceId.toString());
    }

    @Override
    public PaymentAttempt createPaymentAttempt(final PaymentAttempt paymentAttempt, final PaymentAttemptStatus paymentAttemptStatus, final CallContext context) {
        
        final PaymentAttempt newPaymentAttempt = new DefaultPaymentAttempt(paymentAttempt, paymentAttemptStatus);
        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttempt, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttempt inTransaction(PaymentAttemptSqlDao transactional, TransactionStatus status) throws Exception {
                transactional.insertPaymentAttempt(newPaymentAttempt, context);
                PaymentAttempt savedPaymentAttempt = transactional.getPaymentAttemptById(newPaymentAttempt.getId().toString());

                Long recordId = transactional.getRecordId(newPaymentAttempt.getId().toString());
                EntityHistory<PaymentAttempt> history = new EntityHistory<PaymentAttempt>(newPaymentAttempt.getId(), recordId, newPaymentAttempt, ChangeType.INSERT);
                transactional.insertHistoryFromTransaction(history, context);

                Long historyRecordId = transactional.getHistoryRecordId(recordId);
                EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPTS, historyRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);
                return savedPaymentAttempt;
            }
        });
    }

    @Override
    public PaymentAttempt createPaymentAttempt(final Invoice invoice, final PaymentAttemptStatus paymentAttemptStatus, final CallContext context) {

        final PaymentAttempt paymentAttempt = new DefaultPaymentAttempt(UUID.randomUUID(), invoice, paymentAttemptStatus);
        
        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttempt, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttempt inTransaction(PaymentAttemptSqlDao transactional, TransactionStatus status) throws Exception {

                transactional.insertPaymentAttempt(paymentAttempt, context);

                Long recordId = transactional.getRecordId(paymentAttempt.getId().toString());
                EntityHistory<PaymentAttempt> history = new EntityHistory<PaymentAttempt>(paymentAttempt.getId(), recordId, paymentAttempt, ChangeType.INSERT);
                transactional.insertHistoryFromTransaction(history, context);

                Long historyRecordId = transactional.getHistoryRecordId(recordId);
                EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPTS, historyRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);

                return paymentAttempt;
            }
        });
    }
    
    @Override
    public void insertPaymentInfoWithPaymentAttemptUpdate(final PaymentInfoEvent paymentInfo, final UUID paymentAttemptId, final CallContext context) {

        paymentSqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {
            @Override
            public Void inTransaction(PaymentSqlDao transactional, TransactionStatus status) throws Exception {

                transactional.insertPaymentInfo(paymentInfo, context);
                Long recordId = transactional.getRecordId(paymentInfo.getId().toString());
                EntityHistory<PaymentInfoEvent> history = new EntityHistory<PaymentInfoEvent>(paymentInfo.getId(), recordId, paymentInfo, ChangeType.INSERT);
                transactional.insertHistoryFromTransaction(history, context);

                Long historyRecordId = transactional.getHistoryRecordId(recordId);
                EntityAudit audit = new EntityAudit(TableName.PAYMENTS, historyRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);


                if (paymentInfo.getId() != null && paymentAttemptId != null) {
                    PaymentAttemptSqlDao transAttemptSqlDao = transactional.become(PaymentAttemptSqlDao.class);

                    transAttemptSqlDao.updatePaymentAttemptWithPaymentId(paymentAttemptId.toString(), paymentInfo.getId().toString(), context);
                    PaymentAttempt paymentAttempt = transAttemptSqlDao.getPaymentAttemptById(paymentAttemptId.toString());
                    recordId = transAttemptSqlDao.getRecordId(paymentAttemptId.toString());
                    EntityHistory<PaymentAttempt> historyAttempt = new EntityHistory<PaymentAttempt>(paymentAttemptId, recordId, paymentAttempt, ChangeType.UPDATE);
                    transAttemptSqlDao.insertHistoryFromTransaction(historyAttempt, context);

                    historyRecordId = transAttemptSqlDao.getHistoryRecordId(recordId);
                    audit = new EntityAudit(TableName.PAYMENT_ATTEMPTS, historyRecordId, ChangeType.UPDATE);
                    transAttemptSqlDao.insertAuditFromTransaction(audit, context);
                }
                return null;
            }
        });
    }


    @Override
    public List<PaymentInfoEvent> getPaymentInfoList(List<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentInfoEvent>of();
        } else {
            return paymentSqlDao.getPaymentInfoList(toUUIDList(invoiceIds));
        }
    }

    @Override
    public PaymentInfoEvent getLastPaymentInfo(List<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return null;
        } else {
            return paymentSqlDao.getLastPaymentInfo(toUUIDList(invoiceIds));
        }
    }

    @Override
    public List<PaymentAttempt> getPaymentAttemptsForInvoiceIds(List<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.size() == 0) {
            return ImmutableList.<PaymentAttempt>of();
        } else {
            return paymentAttemptSqlDao.getPaymentAttemptsForInvoiceIds(toUUIDList(invoiceIds));
        }
    }

    @Override
    public PaymentAttempt getPaymentAttemptById(UUID paymentAttemptId) {
        return paymentAttemptSqlDao.getPaymentAttemptById(paymentAttemptId.toString());
    }

    @Override
    public PaymentInfoEvent getPaymentInfoForPaymentAttemptId(UUID paymentAttemptIdStr) {
        return paymentSqlDao.getPaymentInfoForPaymentAttemptId(paymentAttemptIdStr.toString());
    }
    
    private static List<String> toUUIDList(List<UUID> input) {
        return new ArrayList<String>(Collections2.transform(input, new Function<UUID, String>() {
            @Override
            public String apply(UUID uuid) {
                return uuid.toString();
            }
        }));
    }
}
