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

import java.util.List;

import org.killbill.automaton.State;
import org.killbill.automaton.State.LeavingStateCallback;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.dao.DirectPaymentTransactionModelDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DirectPaymentLeavingStateCallback implements LeavingStateCallback {

    private final Logger logger = LoggerFactory.getLogger(DirectPaymentLeavingStateCallback.class);

    protected final DirectPaymentAutomatonDAOHelper daoHelper;

    protected DirectPaymentLeavingStateCallback(final DirectPaymentAutomatonDAOHelper daoHelper) throws PaymentApiException {
        this.daoHelper = daoHelper;
    }

    @Override
    public void leavingState(final State oldState) {
        logger.debug("Leaving state {}", oldState.getName());

        // Create or update the direct payment and transaction
        daoHelper.createNewDirectPaymentTransaction();
    }
}
