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

import org.killbill.automaton.OperationResult;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dispatcher.PluginDispatcher;
import org.killbill.billing.payment.plugin.api.PaymentPluginApiException;
import org.killbill.billing.payment.plugin.api.PaymentTransactionInfoPlugin;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefundOperation extends DirectPaymentOperation {

    private final Logger logger = LoggerFactory.getLogger(RefundOperation.class);

    public RefundOperation(final DirectPaymentAutomatonDAOHelper daoHelper,
                           final GlobalLocker locker, final PluginDispatcher<OperationResult> paymentPluginDispatcher,
                           final DirectPaymentStateContext directPaymentStateContext) throws PaymentApiException {
        super(daoHelper, locker, paymentPluginDispatcher, directPaymentStateContext);
    }

    @Override
    protected PaymentTransactionInfoPlugin doPluginOperation() throws PaymentPluginApiException {
        logger.debug("Starting REFUND for payment {} ({} {})", directPaymentStateContext.getDirectPaymentId(), directPaymentStateContext.getAmount(), directPaymentStateContext.getCurrency());
        return plugin.refundPayment(directPaymentStateContext.getAccount().getId(),
                                    directPaymentStateContext.getDirectPaymentId(),
                                    directPaymentStateContext.getTransactionPaymentId(),
                                    directPaymentStateContext.getPaymentMethodId(),
                                    directPaymentStateContext.getAmount(),
                                    directPaymentStateContext.getCurrency(),
                                    directPaymentStateContext.getProperties(),
                                    directPaymentStateContext.getCallContext());
    }
}
