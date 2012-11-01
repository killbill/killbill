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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.skife.jdbi.v2.IDBI;
import org.skife.jdbi.v2.Transaction;
import org.skife.jdbi.v2.TransactionStatus;

import com.ning.billing.payment.api.PaymentStatus;
import com.ning.billing.payment.dao.RefundModelDao.RefundStatus;
import com.ning.billing.payment.retry.PluginFailureRetryService.PluginFailureRetryServiceScheduler;
import com.ning.billing.util.audit.ChangeType;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalTenantContext;
import com.ning.billing.util.dao.EntityAudit;
import com.ning.billing.util.dao.EntityHistory;
import com.ning.billing.util.dao.TableName;

import com.google.inject.Inject;

public class AuditedPaymentDao implements PaymentDao {

    private final PaymentSqlDao paymentSqlDao;
    private final PaymentAttemptSqlDao paymentAttemptSqlDao;
    private final PaymentMethodSqlDao paymentMethodSqlDao;
    private final RefundSqlDao refundSqlDao;

    @Inject
    public AuditedPaymentDao(final IDBI dbi, final PluginFailureRetryServiceScheduler timedoutSchduler) {
        this.paymentSqlDao = dbi.onDemand(PaymentSqlDao.class);
        this.paymentAttemptSqlDao = dbi.onDemand(PaymentAttemptSqlDao.class);
        this.paymentMethodSqlDao = dbi.onDemand(PaymentMethodSqlDao.class);
        this.refundSqlDao = dbi.onDemand(RefundSqlDao.class);
    }

    @Override
    public PaymentAttemptModelDao insertNewAttemptForPayment(final UUID paymentId, final PaymentAttemptModelDao attempt, final InternalCallContext context) {

        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttemptModelDao, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(final PaymentAttemptSqlDao transactional, final TransactionStatus status)
                    throws Exception {
                final PaymentAttemptModelDao savedAttempt = insertPaymentAttemptFromTransaction(attempt, context, transactional);
                final PaymentSqlDao transPaymentSqlDao = transactional.become(PaymentSqlDao.class);
                updatePaymentAmountFromTransaction(paymentId, savedAttempt.getRequestedAmount(), context, transPaymentSqlDao);
                return savedAttempt;
            }
        });
    }

    @Override
    public PaymentModelDao insertPaymentWithAttempt(final PaymentModelDao payment, final PaymentAttemptModelDao attempt, final InternalCallContext context) {

        return paymentSqlDao.inTransaction(new Transaction<PaymentModelDao, PaymentSqlDao>() {

            @Override
            public PaymentModelDao inTransaction(final PaymentSqlDao transactional,
                                                 final TransactionStatus status) throws Exception {
                final PaymentModelDao result = insertPaymentFromTransaction(payment, context, transactional);
                final PaymentAttemptSqlDao transactionalAttempt = transactional.become(PaymentAttemptSqlDao.class);
                insertPaymentAttemptFromTransaction(attempt, context, transactionalAttempt);
                return result;
            }
        });
    }

    private PaymentModelDao insertPaymentFromTransaction(final PaymentModelDao payment, final InternalCallContext context, final PaymentSqlDao transactional) {
        transactional.insertPayment(payment, context);
        final PaymentModelDao savedPayment = transactional.getPayment(payment.getId().toString(), context);
        final Long recordId = transactional.getRecordId(savedPayment.getId().toString(), context);
        final EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.INSERT);
        transactional.insertHistoryFromTransaction(history, context);

        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_HISTORY, historyRecordId, ChangeType.INSERT);
        transactional.insertAuditFromTransaction(audit, context);
        return savedPayment;
    }

    private PaymentAttemptModelDao insertPaymentAttemptFromTransaction(final PaymentAttemptModelDao attempt, final InternalCallContext context, final PaymentAttemptSqlDao transactional) {
        transactional.insertPaymentAttempt(attempt, context);
        final PaymentAttemptModelDao savedAttempt = transactional.getPaymentAttempt(attempt.getId().toString(), context);
        final Long recordId = transactional.getRecordId(savedAttempt.getId().toString(), context);
        final EntityHistory<PaymentAttemptModelDao> history = new EntityHistory<PaymentAttemptModelDao>(savedAttempt.getId(), recordId, savedAttempt, ChangeType.INSERT);
        transactional.insertHistoryFromTransaction(history, context);
        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPT_HISTORY, historyRecordId, ChangeType.INSERT);
        transactional.insertAuditFromTransaction(audit, context);
        return savedAttempt;
    }

    @Override
    public PaymentAttemptModelDao getPaymentAttempt(final UUID attemptId, final InternalTenantContext context) {
        return paymentAttemptSqlDao.inTransaction(new Transaction<PaymentAttemptModelDao, PaymentAttemptSqlDao>() {
            @Override
            public PaymentAttemptModelDao inTransaction(final PaymentAttemptSqlDao transactional, final TransactionStatus status)
                    throws Exception {
                return transactional.getPaymentAttempt(attemptId.toString(), context);
            }
        });
    }

    @Override
    public void updateStatusForPaymentWithAttempt(final UUID paymentId,
                                                  final PaymentStatus paymentStatus,
                                                  final String gatewayErrorCode,
                                                  final String gatewayErrorMsg,
                                                  final String extFirstPaymentRefId,
                                                  final String extSecondPaymentRefId,
                                                  final UUID attemptId,
                                                  final InternalCallContext context) {
        paymentSqlDao.inTransaction(new Transaction<Void, PaymentSqlDao>() {

            @Override
            public Void inTransaction(final PaymentSqlDao transactional,
                                      final TransactionStatus status) throws Exception {
                updatePaymentStatusFromTransaction(paymentId, paymentStatus, extFirstPaymentRefId, extSecondPaymentRefId, context, transactional);
                final PaymentAttemptSqlDao transPaymentAttemptSqlDao = transactional.become(PaymentAttemptSqlDao.class);
                updatePaymentAttemptStatusFromTransaction(attemptId, paymentStatus, gatewayErrorCode, gatewayErrorMsg, context, transPaymentAttemptSqlDao);
                return null;
            }
        });
    }

    private void updatePaymentAmountFromTransaction(final UUID paymentId, final BigDecimal amount, final InternalCallContext context, final PaymentSqlDao transactional) {
        transactional.updatePaymentAmount(paymentId.toString(), amount, context);
        final PaymentModelDao savedPayment = transactional.getPayment(paymentId.toString(), context);
        final Long recordId = transactional.getRecordId(savedPayment.getId().toString(), context);
        final EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_HISTORY, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }

    private void updatePaymentStatusFromTransaction(final UUID paymentId, final PaymentStatus paymentStatus, final String extFirstPaymentRefId,
                                                    final String extSecondPaymentRefId, final InternalCallContext context, final PaymentSqlDao transactional) {
        transactional.updatePaymentStatusAndExtRef(paymentId.toString(), paymentStatus.toString(), extFirstPaymentRefId, extSecondPaymentRefId, context);
        final PaymentModelDao savedPayment = transactional.getPayment(paymentId.toString(), context);
        final Long recordId = transactional.getRecordId(savedPayment.getId().toString(), context);
        final EntityHistory<PaymentModelDao> history = new EntityHistory<PaymentModelDao>(savedPayment.getId(), recordId, savedPayment, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_HISTORY, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }

    private void updatePaymentAttemptStatusFromTransaction(final UUID attemptId, final PaymentStatus processingStatus, final String gatewayErrorCode,
                                                           final String gatewayErrorMsg, final InternalCallContext context, final PaymentAttemptSqlDao transactional) {
        transactional.updatePaymentAttemptStatus(attemptId.toString(), processingStatus.toString(), gatewayErrorCode, gatewayErrorMsg, context);
        final PaymentAttemptModelDao savedAttempt = transactional.getPaymentAttempt(attemptId.toString(), context);
        final Long recordId = transactional.getRecordId(savedAttempt.getId().toString(), context);
        final EntityHistory<PaymentAttemptModelDao> history = new EntityHistory<PaymentAttemptModelDao>(savedAttempt.getId(), recordId, savedAttempt, ChangeType.UPDATE);
        transactional.insertHistoryFromTransaction(history, context);
        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_ATTEMPT_HISTORY, historyRecordId, ChangeType.UPDATE);
        transactional.insertAuditFromTransaction(audit, context);
    }

    @Override
    public PaymentMethodModelDao insertPaymentMethod(final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        return paymentMethodSqlDao.inTransaction(new Transaction<PaymentMethodModelDao, PaymentMethodSqlDao>() {
            @Override
            public PaymentMethodModelDao inTransaction(final PaymentMethodSqlDao transactional, final TransactionStatus status)
                    throws Exception {
                return insertPaymentMethodInTransaction(transactional, paymentMethod, context);
            }
        });
    }

    private PaymentMethodModelDao insertPaymentMethodInTransaction(final PaymentMethodSqlDao transactional, final PaymentMethodModelDao paymentMethod, final InternalCallContext context) {
        transactional.insertPaymentMethod(paymentMethod, context);
        final PaymentMethodModelDao savedPaymentMethod = transactional.getPaymentMethod(paymentMethod.getId().toString(), context);
        final Long recordId = transactional.getRecordId(savedPaymentMethod.getId().toString(), context);
        final EntityHistory<PaymentMethodModelDao> history = new EntityHistory<PaymentMethodModelDao>(savedPaymentMethod.getId(), recordId, savedPaymentMethod, ChangeType.INSERT);
        transactional.insertHistoryFromTransaction(history, context);
        final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
        final EntityAudit audit = new EntityAudit(TableName.PAYMENT_METHOD_HISTORY, historyRecordId, ChangeType.INSERT);
        transactional.insertAuditFromTransaction(audit, context);
        return savedPaymentMethod;
    }

    @Override
    public List<PaymentMethodModelDao> refreshPaymentMethods(final UUID accountId, final List<PaymentMethodModelDao> paymentMethods, final InternalCallContext context) {
        return paymentMethodSqlDao.inTransaction(new Transaction<List<PaymentMethodModelDao>, PaymentMethodSqlDao>() {

            @Override
            public List<PaymentMethodModelDao> inTransaction(final PaymentMethodSqlDao transactional, final TransactionStatus status) throws Exception {
                final List<PaymentMethodModelDao> existingPaymentMethods = getPaymentMethodsInTransaction(transactional, accountId, context);

                final Set<String> externalPaymentIdProcessed = new HashSet<String>();
                for (final PaymentMethodModelDao finalPaymentMethod : paymentMethods) {
                    boolean isExistingPaymentMethod = false;

                    for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                        if (existingPaymentMethod.equals(finalPaymentMethod)) {
                            // We already have it - nothing to do
                            isExistingPaymentMethod = true;
                            break;
                        } else if (existingPaymentMethod.equalsButActive(finalPaymentMethod)) {
                            // We already have it but its status has changed - update it accordingly
                            // Note - in the remote system, the payment method will always be active
                            undeletedPaymentMethodInTransaction(transactional, existingPaymentMethod.getId(), context);
                            isExistingPaymentMethod = true;
                            break;
                        }
                        // Otherwise, we don't have it
                    }

                    if (!isExistingPaymentMethod) {
                        insertPaymentMethodInTransaction(transactional, finalPaymentMethod, context);
                    }

                    externalPaymentIdProcessed.add(finalPaymentMethod.getExternalId());
                }

                // Finally, mark as deleted the ones that don't exist in the specified list (remote system)
                for (final PaymentMethodModelDao existingPaymentMethod : existingPaymentMethods) {
                    if (!externalPaymentIdProcessed.contains(existingPaymentMethod.getExternalId())) {
                        deletedPaymentMethodInTransaction(transactional, existingPaymentMethod.getId(), context);
                    }
                }

                return getPaymentMethodsInTransaction(transactional, accountId, context);
            }
        });
    }

    @Override
    public RefundModelDao insertRefund(final RefundModelDao refundInfo, final InternalCallContext context) {
        return refundSqlDao.inTransaction(new Transaction<RefundModelDao, RefundSqlDao>() {

            @Override
            public RefundModelDao inTransaction(RefundSqlDao transactional,
                                                TransactionStatus status) throws Exception {

                transactional.insertRefund(refundInfo, context);
                final RefundModelDao savedRefund = transactional.getRefund(refundInfo.getId().toString(), context);
                final Long recordId = transactional.getRecordId(savedRefund.getId().toString(), context);
                final EntityHistory<RefundModelDao> history = new EntityHistory<RefundModelDao>(savedRefund.getId(), recordId, savedRefund, ChangeType.INSERT);
                transactional.insertHistoryFromTransaction(history, context);
                final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
                final EntityAudit audit = new EntityAudit(TableName.REFUND_HISTORY, historyRecordId, ChangeType.INSERT);
                transactional.insertAuditFromTransaction(audit, context);
                return savedRefund;
            }
        });
    }

    @Override
    public void updateRefundStatus(final UUID refundId, final RefundStatus refundStatus, final InternalCallContext context) {
        refundSqlDao.inTransaction(new Transaction<Void, RefundSqlDao>() {

            @Override
            public Void inTransaction(RefundSqlDao transactional,
                                      TransactionStatus status) throws Exception {
                transactional.updateStatus(refundId.toString(), refundStatus.toString(), context);

                final RefundModelDao savedRefund = transactional.getRefund(refundId.toString(), context);
                final Long recordId = transactional.getRecordId(savedRefund.getId().toString(), context);
                final EntityHistory<RefundModelDao> history = new EntityHistory<RefundModelDao>(savedRefund.getId(), recordId, savedRefund, ChangeType.UPDATE);
                transactional.insertHistoryFromTransaction(history, context);
                final Long historyRecordId = transactional.getHistoryRecordId(recordId, context);
                final EntityAudit audit = new EntityAudit(TableName.REFUND_HISTORY, historyRecordId, ChangeType.UPDATE);
                transactional.insertAuditFromTransaction(audit, context);
                return null;
            }
        });
    }

    @Override
    public RefundModelDao getRefund(final UUID refundId, final InternalTenantContext context) {
        return refundSqlDao.inTransaction(new Transaction<RefundModelDao, RefundSqlDao>() {

            @Override
            public RefundModelDao inTransaction(RefundSqlDao transactional,
                                                TransactionStatus status) throws Exception {
                return transactional.getRefund(refundId.toString(), context);
            }
        });
    }

    @Override
    public List<RefundModelDao> getRefundsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return refundSqlDao.inTransaction(new Transaction<List<RefundModelDao>, RefundSqlDao>() {

            @Override
            public List<RefundModelDao> inTransaction(RefundSqlDao transactional,
                                                      TransactionStatus status) throws Exception {
                return transactional.getRefundsForPayment(paymentId.toString(), context);
            }
        });
    }

    @Override
    public List<RefundModelDao> getRefundsForAccount(final UUID accountId, final InternalTenantContext context) {
        return refundSqlDao.inTransaction(new Transaction<List<RefundModelDao>, RefundSqlDao>() {

            @Override
            public List<RefundModelDao> inTransaction(RefundSqlDao transactional,
                                                      TransactionStatus status) throws Exception {
                return transactional.getRefundsForAccount(accountId.toString(), context);
            }
        });
    }

    @Override
    public PaymentMethodModelDao getPaymentMethod(final UUID paymentMethodId, final InternalTenantContext context) {
        return getPaymentMethodInTransaction(paymentMethodSqlDao, paymentMethodId, context);
    }

    @Override
    public PaymentMethodModelDao getPaymentMethodIncludedDeleted(final UUID paymentMethodId, final InternalTenantContext context) {
        return paymentMethodSqlDao.getPaymentMethodIncludedDelete(paymentMethodId.toString(), context);
    }

    private PaymentMethodModelDao getPaymentMethodInTransaction(final PaymentMethodSqlDao transactional, final UUID paymentMethodId, final InternalTenantContext context) {
        return transactional.getPaymentMethod(paymentMethodId.toString(), context);
    }

    @Override
    public List<PaymentMethodModelDao> getPaymentMethods(final UUID accountId, final InternalTenantContext context) {
        return getPaymentMethodsInTransaction(paymentMethodSqlDao, accountId, context);
    }

    private List<PaymentMethodModelDao> getPaymentMethodsInTransaction(final PaymentMethodSqlDao transactional, final UUID accountId, final InternalTenantContext context) {
        return transactional.getPaymentMethods(accountId.toString(), context);
    }

    @Override
    public void deletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        deletedPaymentMethodInTransaction(paymentMethodSqlDao, paymentMethodId, context);
    }

    private void deletedPaymentMethodInTransaction(final PaymentMethodSqlDao transactional, final UUID paymentMethodId, final InternalCallContext context) {
        transactional.markPaymentMethodAsDeleted(paymentMethodId.toString(), context);
    }

    @Override
    public void undeletedPaymentMethod(final UUID paymentMethodId, final InternalCallContext context) {
        undeletedPaymentMethodInTransaction(paymentMethodSqlDao, paymentMethodId, context);
    }

    private void undeletedPaymentMethodInTransaction(final PaymentMethodSqlDao transactional, final UUID paymentMethodId, final InternalCallContext context) {
        transactional.unmarkPaymentMethodAsDeleted(paymentMethodId.toString(), context);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForInvoice(final UUID invoiceId, final InternalTenantContext context) {
        return paymentSqlDao.getPaymentsForInvoice(invoiceId.toString(), context);
    }

    @Override
    public PaymentModelDao getLastPaymentForPaymentMethod(final UUID accountId, final UUID paymentMethodId, final InternalTenantContext context) {
        return paymentSqlDao.getLastPaymentForAccountAndPaymentMethod(accountId.toString(), paymentMethodId.toString(), context);
    }

    @Override
    public PaymentModelDao getPayment(final UUID paymentId, final InternalTenantContext context) {
        return paymentSqlDao.getPayment(paymentId.toString(), context);
    }

    @Override
    public List<PaymentModelDao> getPaymentsForAccount(final UUID accountId, final InternalTenantContext context) {
        return paymentSqlDao.getPaymentsForAccount(accountId.toString(), context);
    }

    @Override
    public List<PaymentAttemptModelDao> getAttemptsForPayment(final UUID paymentId, final InternalTenantContext context) {
        return paymentAttemptSqlDao.getPaymentAttempts(paymentId.toString(), context);
    }
}
