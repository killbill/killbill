/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.core.sm;

import javax.annotation.Nullable;

import org.killbill.automaton.Operation;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public abstract class DirectPaymentEnteringStateCallback implements EnteringStateCallback {

    private final Logger logger = LoggerFactory.getLogger(DirectPaymentEnteringStateCallback.class);

    protected final DirectPaymentAutomatonDAOHelper daoHelper;
    protected final DirectPaymentStateContext directPaymentStateContext;

    protected DirectPaymentEnteringStateCallback(final DirectPaymentAutomatonDAOHelper daoHelper, final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.directPaymentStateContext = directPaymentStateContext;
    }

    @Override
    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        logger.debug("Entering state {} with result {}", newState.getName(), operationResult);

        // Check for illegal state (should never happen)
        Preconditions.checkState(directPaymentStateContext.getDirectPaymentTransactionModelDao() != null && directPaymentStateContext.getDirectPaymentTransactionModelDao().getId() != null);

        final PaymentTransactionInfoPlugin paymentInfoPlugin = directPaymentStateContext.getPaymentInfoPlugin();
        final TransactionStatus paymentStatus = paymentPluginStatusToPaymentStatus(paymentInfoPlugin, operationResult);

        daoHelper.processPaymentInfoPlugin(paymentStatus, paymentInfoPlugin, newState.getName());
    }

    private TransactionStatus paymentPluginStatusToPaymentStatus(@Nullable final PaymentTransactionInfoPlugin paymentInfoPlugin, final OperationResult operationResult) {
        if (paymentInfoPlugin == null) {
            if (OperationResult.EXCEPTION.equals(operationResult)) {
                // We got an exception during the plugin call
                return TransactionStatus.PLUGIN_FAILURE;
            } else {
                // The plugin completed the call but returned null?! Bad plugin...
                return TransactionStatus.UNKNOWN;
            }
        }

        if (paymentInfoPlugin.getStatus() == null) {
            // The plugin completed the call but returned an incomplete PaymentInfoPlugin?! Bad plugin...
            return TransactionStatus.UNKNOWN;
        }

        switch (paymentInfoPlugin.getStatus()) {
            case UNDEFINED:
                return TransactionStatus.UNKNOWN;
            case PROCESSED:
                return TransactionStatus.SUCCESS;
            case PENDING:
                return TransactionStatus.PENDING;
            case ERROR:
                return TransactionStatus.PAYMENT_FAILURE;
            default:
                return TransactionStatus.UNKNOWN;
        }
    }
}
