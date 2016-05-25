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

import org.killbill.automaton.OperationResult;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.dao.PaymentTransactionModelDao;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

public class ChargebackInitiated extends PaymentLeavingStateCallback {

    public ChargebackInitiated(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        super(daoHelper, paymentStateContext);
    }

    @Override
    protected void validatePaymentIdAndTransactionType(final Iterable<PaymentTransactionModelDao> existingPaymentTransactions) throws PaymentApiException {
        if (OperationResult.FAILURE.equals(paymentStateContext.getOverridePluginOperationResult()) && !existingPaymentTransactions.iterator().hasNext()) {
            // Chargeback reversals can only happen after a successful chargeback
            throw new PaymentApiException(ErrorCode.PAYMENT_NO_SUCH_SUCCESS_PAYMENT, paymentStateContext.getPaymentId());
        }
        super.validatePaymentIdAndTransactionType(existingPaymentTransactions);
    }

    @Override
    protected void validateUniqueTransactionExternalKey(final Iterable<PaymentTransactionModelDao> existingPaymentTransactions) throws PaymentApiException {
        // If no key specified, system will allocate a unique one later, there is nothing to check
        if (paymentStateContext.getPaymentTransactionExternalKey() == null) {
            return;
        }

        // The main difference with the default implementation is that an existing transaction in a SUCCESS state can exist (chargeback reversal)
        if (Iterables.any(existingPaymentTransactions, new Predicate<PaymentTransactionModelDao>() {
            @Override
            public boolean apply(final PaymentTransactionModelDao input) {
                // An existing transaction for a different payment (to do really well, we should also check on paymentExternalKey which is not available here)
                return (paymentStateContext.getPaymentId() != null && input.getPaymentId().compareTo(paymentStateContext.getPaymentId()) != 0) ||
                       // Or, an existing transaction for a different account.
                       (!input.getAccountRecordId().equals(paymentStateContext.getInternalCallContext().getAccountRecordId()));

            }
        })) {
            throw new PaymentApiException(ErrorCode.PAYMENT_ACTIVE_TRANSACTION_KEY_EXISTS, paymentStateContext.getPaymentTransactionExternalKey());
        }
    }
}
