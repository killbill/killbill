/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.payment.dao.PaymentDao;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.commons.locker.GlobalLocker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultAdminPaymentApi implements AdminPaymentApi {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminPaymentApi.class);

    private final PaymentDao paymentDao;
    private final GlobalLocker locker;
    private final InternalCallContextFactory internalCallContextFactory;

    @Inject
    public DefaultAdminPaymentApi(final PaymentDao paymentDao, final InternalCallContextFactory internalCallContextFactory, final GlobalLocker locker) {
        this.paymentDao = paymentDao;
        this.internalCallContextFactory = internalCallContextFactory;
        this.locker = locker;
    }

    @Override
    public void fixPaymentTransactionState(final Payment payment, PaymentTransaction paymentTransaction, TransactionStatus transactionStatus, @Nullable String lastSuccessPaymentState, String currentPaymentStateName,
                                              Iterable<PluginProperty> properties, CallContext callContext)
            throws PaymentApiException {

        final InternalCallContext internalCallContext = internalCallContextFactory.createInternalCallContext(payment.getAccountId(), callContext);
        paymentDao.updatePaymentAndTransactionOnCompletion(payment.getAccountId(), payment.getId(), paymentTransaction.getTransactionType(),
                                                       currentPaymentStateName, lastSuccessPaymentState, paymentTransaction.getId(),
                                                       transactionStatus, paymentTransaction.getProcessedAmount(), paymentTransaction.getProcessedCurrency(),
                                                       paymentTransaction.getGatewayErrorCode(), paymentTransaction.getGatewayErrorMsg(), internalCallContext);
    }
}
