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

package com.ning.billing.payment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.eventbus.Subscribe;
import com.ning.billing.payment.api.PaymentError;
import com.ning.billing.payment.api.PaymentInfo;

public class MockPaymentInfoReceiver {
    private final List<PaymentInfo> processedPayments = Collections.synchronizedList(new ArrayList<PaymentInfo>());
    private final List<PaymentError> errors = Collections.synchronizedList(new ArrayList<PaymentError>());

    @Subscribe
    public void processedPayment(PaymentInfo paymentInfo) {
        processedPayments.add(paymentInfo);
    }

    @Subscribe
    public void processedPaymentError(PaymentError paymentError) {
        errors.add(paymentError);
    }

    public List<PaymentInfo> getProcessedPayments() {
        return new ArrayList<PaymentInfo>(processedPayments);
    }

    public List<PaymentError> getErrors() {
        return new ArrayList<PaymentError>(errors);
    }

    public void clear() {
        processedPayments.clear();
        errors.clear();
    }
}