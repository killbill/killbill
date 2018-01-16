/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm.payments;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.killbill.automaton.OperationException;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ChargebackInitiated extends PaymentLeavingStateCallback {

    public ChargebackInitiated(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        super(daoHelper, paymentStateContext);
    }

    @Override
    public void leavingState(final State oldState) throws OperationException {
        // Sanity: chargeback reversals can only happen after a successful chargeback
        if (OperationResult.FAILURE.equals(paymentStateContext.getOverridePluginOperationResult())) {
            final List<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment = paymentStateContext.getPaymentId() != null ?
                                                                                          daoHelper.getPaymentDao().getTransactionsForPayment(paymentStateContext.getPaymentId(), paymentStateContext.getInternalCallContext()) :
                                                                                          ImmutableList.<PaymentTransactionModelDao>of();
            final Iterable<PaymentTransactionModelDao> existingPaymentTransactionsForTransactionIdOrKey = filterExistingPaymentTransactionsForTransactionIdOrKey(paymentTransactionsForCurrentPayment, paymentStateContext.getTransactionId(), paymentStateContext.getPaymentTransactionExternalKey());

            if (Iterables.isEmpty(existingPaymentTransactionsForTransactionIdOrKey)) {
                // Chargeback reversals can only happen after a successful chargeback
                throw new OperationException(new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentStateContext.getPaymentId()));
            }
        }

        super.leavingState(oldState);
    }

    private Iterable<PaymentTransactionModelDao> filterExistingPaymentTransactionsForTransactionIdOrKey(final Iterable<PaymentTransactionModelDao> paymentTransactionsForCurrentPayment, @Nullable final UUID paymentTransactionId, @Nullable final String paymentTransactionExternalKey) {
        return Iterables.filter(paymentTransactionsForCurrentPayment, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                if (paymentTransactionId != null && input.getId().equals(paymentTransactionId)) {
                    return true;
                }
                if (paymentTransactionExternalKey != null && input.getTransactionExternalKey().equals(paymentTransactionExternalKey)) {
                    return true;
                }
                return false;
            }
        });
    }
}
