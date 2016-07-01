/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * Groupon licenses this file to you under the Apache License, version 2.0
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

import org.killbill.automaton.OperationException;
import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PaymentLeavingStateCallback implements LeavingStateCallback {

    private static final Logger logger = LoggerFactory.getLogger(PaymentLeavingStateCallback.class);

    protected final PaymentAutomatonDAOHelper daoHelper;
    protected final PaymentStateContext paymentStateContext;

    protected PaymentLeavingStateCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.paymentStateContext = paymentStateContext;
    }

    @Override
    public void leavingState(final State oldState) throws OperationException {
        logger.debug("Leaving state {}", oldState.getName());

        try {
            // We always create a new transaction (even in case of PAYMENT_FAILURE or PLUGIN_FAILURE) except for PENDING payments (completion)
            if (paymentStateContext.getPaymentTransactionModelDao() == null || paymentStateContext.getPaymentTransactionModelDao().getTransactionStatus() != TransactionStatus.PENDING) {
                daoHelper.createNewPaymentTransaction();
            }
        } catch (final PaymentApiException e) {
            throw new OperationException(e);
        }
    }
}
