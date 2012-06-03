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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.inject.Inject;
import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import com.ning.billing.util.ChangeType;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.TableName;

public class AuditedPaymentDao implements PaymentDao {

    private final PaymentSqlDao paymentSqlDao;
    private final PaymentAttemptSqlDao paymentAttemptSqlDao;
    private final PaymentMethodSqlDao paymentMethodSqlDao;    
    //private final TimedoutPaymentRetryServiceScheduler timedoutSchduler;
    
    @Inject
    public AuditedPaymentDao(IDBI dbi, PluginFailureRetryServiceScheduler timedoutSchduler) {
       this.paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
       this.paymentAttemptSqlDao = dbi.onDemand(PaymentAttemptSqlDao.class);
       this.paymentMethodSqlDao = dbi.onDemand(PaymentMethodSqlDao.class);
      // this.timedoutSchduler = timedoutSchduler;
    }
    


    @Override
    public PaymentAttemptModelDao insertNewAttemptForPayment(final UUID paymentId,
            final PaymentAttemptModelDao attempt, final boolean scheduleTimeoutRetry, final CallContext context) {

        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttemptModelDao, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(PaymentAttemptSqlDao transactional, TransactionStatus status)
            throws Exception {
                PaymentAttemptModelDao savedAttempt = insertPaymentAttemptFromTransaction(attempt, context, transactional);
                PaymentSqlDao transPaymentSqlDao = transactional.become(PaymentSqlDao.class);
                updatePaymentAmountFromTransaction(paymentId, savedAttempt.getRequestedAmount(), context, transPaymentSqlDao);
                return savedAttempt;
            }
        });
    }
    
    
    @Override
    public PaymentModelDao insertPaymentWithAttempt(final PaymentModelDao payment, final PaymentAttemptModelDao attempt, final boolean scheduleTimeoutRetry, final CallContext context) {
        
        return paymentSqlDao.inTransaction(new Transaction<PaymentModelDao, PaymentSqlDao>() {

            @Override
            public PaymentModelDao inTransaction(PaymentSqlDao transactional,
                    TransactionStatus status) throws Exception {
                PaymentModelDao result =  insertPaymentFromTransaction(payment, context, transactional);
                final PaymentAttemptSqlDao transactionalAttempt = transactional.become(PaymentAttemptSqlDao.class);
                insertPaymentAttemptFromTransaction(attempt, context, transactionalAttempt);
                return result;
            }
        });
    }

    /*
    private int getNbTimedoutAttemptsFromTransaction(final UUID paymentId, final PaymentAttemptSqlDao transactional) {
        List<PaymentAttemptModelDao> attempts = transactional.getPaymentAttempts(paymentId.toString());
        return Collections2.filter(attempts, new Predicate<PaymentAttemptModelDao>() {
            @Override
            public boolean apply(PaymentAttemptModelDao input) {
                return input.getPaymentStatus() == PaymentStatus.TIMEDOUT;
            }
        }).size();
    }

    
    private void scheduleTimeoutRetryFromTransaction(final UUID paymentId, final PaymentAttemptSqlDao transactional, final boolean scheduleTimeoutRetry) {

        if (scheduleTimeoutRetry) { 
            int retryAttempt = getNbTimedoutAttemptsFromTransaction(paymentId, transactional) + 1;
            timedoutSchduler.scheduleRetryFromTransaction(paymentId, retryAttempt, transactional);
        }
    }
*/
    
    
    private PaymentModelDao insertPaymentFromTransaction(final PaymentModelDao payment, final CallContext context, final PaymentSqlDao transactional) {
        transactional.insertPayment(payment, context);
        PaymentModelDao savedPayment = transactional.getPayment(payment.getId().toString());
        Long recordId = transactional.getRecordId(savedPayment.getId().toString());
        EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.INSERT);
        transactional.insertHistoryFromTransaction(history, context);
        
        Long historyRecordId = transactional.getHistoryRecordId(recordId);
        EntityAudit audit = new EntityAudit(TableName.PAYMENTS, historyRecordId, ChangeType.INSERT);
        transactional.insertAuditFromTransaction(audit, context);
        return savedPayment;
    }
    
    private PaymentAttemptModelDao insertPaymentAttemptFromTransaction(final PaymentAttemptModelDao attempt, final CallContext context, final PaymentAttemptSqlDao transactional) {
        transactional.insertPaymentAttempt(attempt, context);
        PaymentAttemptModelDao savedAttempt = transactional.getPaymentAttempt(attempt.getId().toString());
        Long recordId = transactional.getRecordId(savedAttempt.getId().toString());
        EntityHistory<PaymentAttemptModelDao> history = new EntityHistory<PaymentAttemptModelDao>(savedAttempt.getId(), recordId, savedAttempt, ChangeType.INSERT);
        transactional.insertHistoryFromTransaction(history, context);
        Long historyRecordId = transactional.getHistoryRecordId(recordId);
        EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPTS, historyRecordId, ChangeType.INSERT);
        transactional.insertAuditFromTransaction(audit, context);
        return savedAttempt;
    }
    
    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId) {
        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttemptModelDao, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(PaymentAttemptSqlDao transactional, TransactionStatus status)
                    throws Exception {
                return transactional.getPaymentAttempt(attemptId.toString());
            }
        });
    }
    
    

    @Override
    public void updateStatusForPayment(final UUID paymentId,
            final PaymentStatus paymentStatus, final CallContext context) {
        paymentSqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {

            @Override
            public Void inTransaction(PaymentSqlDao transactional,
                    TransactionStatus status) throws Exception {
                updatePaymentStatusFromTransaction(paymentId, paymentStatus, context, transactional);
                return null;
            }
        });
    }

    @Override
    public void updateStatusForPaymentWithAttempt(final UUID paymentId,
            final PaymentStatus paymentStatus, final String paymentError, final UUID attemptId,
            final CallContext context) {
        paymentSqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {

            @Override
            public Void inTransaction(PaymentSqlDao transactional,
                    TransactionStatus status) throws Exception {
                updatePaymentStatusFromTransaction(paymentId, paymentStatus, context, transactional);
                PaymentAttemptSqlDao transPaymentAttemptSqlDao = transactional.become(PaymentAttemptSqlDao.class);
                updatePaymentAttemptStatusFromTransaction(attemptId, paymentStatus, paymentError, context, transPaymentAttemptSqlDao);
                return null;
            }
        });
    }

    private void updatePaymentAmountFromTransaction(final UUID paymentId, final BigDecimal amount, final CallContext context, final PaymentSqlDao transactional) {
        transactional.updatePaymentAmount(paymentId.toString(), amount, context);
        PaymentModelDao savedPayment = transactional.getPayment(paymentId.toString());
        Long recordId = transactional.getRecordId(savedPayment.getId().toString());
        EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        Long historyRecordId = transactional.getHistoryRecordId(recordId);
        EntityAudit audit = new EntityAudit(TableName.PAYMENTS, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }

    private void updatePaymentStatusFromTransaction(final UUID paymentId, final PaymentStatus paymentStatus, final CallContext context, final PaymentSqlDao transactional) {
        transactional.updatePaymentStatus(paymentId.toString(), paymentStatus.toString(), context);
        PaymentModelDao savedPayment = transactional.getPayment(paymentId.toString());
        Long recordId = transactional.getRecordId(savedPayment.getId().toString());
        EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        Long historyRecordId = transactional.getHistoryRecordId(recordId);
        EntityAudit audit = new EntityAudit(TableName.PAYMENTS, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }
    
    private void updatePaymentAttemptStatusFromTransaction(final UUID attemptId, final PaymentStatus processingStatus, final String paymentError, final CallContext context, final PaymentAttemptSqlDao transactional) {
        transactional.updatePaymentAttemptStatus(attemptId.toString(), processingStatus.toString(), paymentError);
        PaymentAttemptModelDao savedAttempt = transactional.getPaymentAttempt(attemptId.toString());
        Long recordId = transactional.getRecordId(savedAttempt.getId().toString());
        EntityHistory<PaymentAttemptModelDao> history = new EntityHistory<PaymentAttemptModelDao>(savedAttempt.getId(), recordId, savedAttempt, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        Long historyRecordId = transactional.getHistoryRecordId(recordId);
        EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPTS, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }
    
    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final CallContext context) {
        return paymentMethodSqlDao.inTransaction(new Transaction<PaymentMethodModelDao, PaymentMethodSqlDao>() {

            @Override
            public PaymentMethodModelDao inTransaction(PaymentMethodSqlDao transactional, TransactionStatus status)
                    throws Exception {
                transactional.insertPaymentMethod(paymentMethod, context);
                PaymentMethodModelDao savedPaymentMethod = transactional.getPaymentMethod(paymentMethod.getId().toString());
                Long recordId = transactional.getRecordId(savedPaymentMethod.getId().toString());
                EntityHistory<PaymentMethodModelDao> history = new EntityHistory<PaymentMethodModelDao>(savedPaymentMethod.getId(), recordId, savedPaymentMethod, ChangeType.INSERT);
                transactional.insertHistoryFromTransaction(history, context);
                Long historyRecordId = transactional.getHistoryRecordId(recordId);
                EntityAudit audit = new EntityAudit(TableName.PAYMENT_METHODS, historyRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);
                return savedPaymentMethod;
            }
        });
    }
    
    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId) {
        return paymentMethodSqlDao.inTransaction(new Transaction<PaymentMethodModelDao, PaymentMethodSqlDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(PaymentMethodSqlDao transactional, TransactionStatus status)
                    throws Exception {
                return transactional.getPaymentMethod(paymentMethodId.toString());
            }
        });
    }
    
    @Override    
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId) {
        return paymentMethodSqlDao.inTransaction(new Transaction<List<PaymentMethodModelDao>, PaymentMethodSqlDao>() {
            @Override
            public List<PaymentMethodModelDao> inTransaction(PaymentMethodSqlDao transactional, TransactionStatus status)
                    throws Exception {
                return transactional.getPaymentMethods(accountId.toString());
            }
        });
                 
    }

    @Override
    public void deletedPaymentMethod(UUID paymentMethodId) {
        paymentMethodSqlDao.markPaymentMethodAsDeleted(paymentMethodId.toString());
    }



    @Override
    public List<PaymentModelDao> getPaymentsForInvoice(UUID invoiceId) {
        return paymentSqlDao.getPaymentsForInvoice(invoiceId.toString());
    }

    @Override
    public PaymentModelDao getPayment(UUID paymentId) {
        return paymentSqlDao.getPayment(paymentId.toString());
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(UUID accountId) {
        return paymentSqlDao.getPaymentsForAccount(accountId.toString());
    }



    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(UUID paymentId) {
        return paymentAttemptSqlDao.getPaymentAttempts(paymentId.toString());
    }



}
