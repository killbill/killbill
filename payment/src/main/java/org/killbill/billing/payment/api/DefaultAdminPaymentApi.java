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

package org.killbill.billing.payment.api;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.core.PaymentTransactionInfoPluginConverter;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.config.definition.PaymentConfig;

public class DefaultAdminPaymentApi extends DefaultApiBase implements AdminPaymentApi {

    private final PaymentDao paymentDao;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAdminPaymentApi(final PaymentConfig paymentConfig, final PaymentDao paymentDao, final InternalCallContextFactory internalCallContextFactory) {
        super(paymentConfig, internalCallContextFactory);
        this.paymentDao = paymentDao;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public void fixPaymentTransactionState(final Payment payment,
                                           final PaymentTransaction paymentTransaction,
                                           @Nullable final TransactionStatus transactionStatusMaybeNull,
                                           @Nullable final String lastSuccessPaymentState,
                                           final String currentPaymentStateName,
                                           final Iterable<PluginProperty> properties,
                                           final CallContext callContext) throws PaymentApiException {
        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(payment.getAccountId(), callContext);

        final TransactionStatus transactionStatus;
        if (transactionStatusMaybeNull == null) {
            checkNotNullParameter(paymentTransaction.getPaymentInfoPlugin(), "PaymentTransactionInfoPlugin");
            transactionStatus = PaymentTransactionInfoPluginConverter.toTransactionStatus(paymentTransaction.getPaymentInfoPlugin());
        } else {
            transactionStatus = transactionStatusMaybeNull;
        }

        paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(),
                                                           null,
                                                           payment.getId(),
                                                           paymentTransaction.getTransactionType(),
                                                           currentPaymentStateName,
                                                           lastSuccessPaymentState,
                                                           paymentTransaction.getId(),
                                                           transactionStatus,
                                                           paymentTransaction.getProcessedAmount(),
                                                           paymentTransaction.getProcessedCurrency(),
                                                           paymentTransaction.getGatewayErrorCode(),
                                                           paymentTransaction.getGatewayErrorMsg(),
                                                           internalCallContext);
    }
}
