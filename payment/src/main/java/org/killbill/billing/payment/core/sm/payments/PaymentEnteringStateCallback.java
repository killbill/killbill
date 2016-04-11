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

package org.killbill.billing.payment.core.sm.payments;

import org.killbill.automaton.Operation;
import org.killbill.automaton.OperationResult;
import org.killbill.automaton.State;
import org.killbill.automaton.State.EnteringStateCallback;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.events.BusInternalEvent;
import org.killbill.billing.payment.api.DefaultPaymentErrorEvent;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.TransactionStatus;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.core.sm.PaymentAutomatonDAOHelper;
import org.killbill.billing.payment.core.sm.PaymentStateContext;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.bus.api.PersistentBus.EventBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PaymentEnteringStateCallback implements EnteringStateCallback {

    private final Logger logger = LoggerFactory.getLogger(PaymentEnteringStateCallback.class);

    protected final PaymentAutomatonDAOHelper daoHelper;
    protected final PaymentStateContext paymentStateContext;

    protected PaymentEnteringStateCallback(final PaymentAutomatonDAOHelper daoHelper, final PaymentStateContext paymentStateContext) throws PaymentApiException {
        this.daoHelper = daoHelper;
        this.paymentStateContext = paymentStateContext;
    }

    @Override
    public void enteringState(final State newState, final Operation.OperationCallback operationCallback, final OperationResult operationResult, final LeavingStateCallback leavingStateCallback) {
        logger.debug("Entering state {} with result {}", newState.getName(), operationResult);

        // If the transaction was not created -- for instance we had an exception in leavingState callback then we bail; if not, then update state:
        if (paymentStateContext.getPaymentTransactionModelDao() != null && paymentStateContext.getPaymentTransactionModelDao().getId() != null) {
            final PaymentTransactionInfoPlugin paymentInfoPlugin = paymentStateContext.getPaymentTransactionInfoPlugin();
            final TransactionStatus transactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(paymentInfoPlugin);
            // The bus event will be posted from the transaction
            daoHelper.processPaymentInfoPlugin(transactionStatus, paymentInfoPlugin, newState.getName());
        } else if (!paymentStateContext.isApiPayment()) {
            //
            // If there is NO transaction to update (because payment transaction did not occur), then there is something wrong happening (maybe a missing defaultPaymentMethodId, ...)
            // so, if the call does NOT originates from api then we still want to send a bus event so the system can react to it if needed.
            //
            final BusInternalEvent event = new DefaultPaymentErrorEvent(paymentStateContext.getAccount().getId(),
                                                                        null,
                                                                        null,
                                                                        paymentStateContext.getAmount(),
                                                                        paymentStateContext.getCurrency(),
                                                                        null,
                                                                        paymentStateContext.getTransactionType(),
                                                                        null,
                                                                        "Early abortion of payment transaction",
                                                                        paymentStateContext.getInternalCallContext().getAccountRecordId(),
                                                                        paymentStateContext.getInternalCallContext().getTenantRecordId(),
                                                                        paymentStateContext.getInternalCallContext().getUserToken());
            try {
                daoHelper.getEventBus().post(event);
            } catch (EventBusException e) {
                logger.warn("Failed to post event {}", event, e);
            }
        }
    }
}
