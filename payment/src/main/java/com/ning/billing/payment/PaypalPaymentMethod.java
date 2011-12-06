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

import com.ning.billing.payment.api.PaymentMethodInfo;

public class PaypalPaymentMethod  extends PaymentMethodInfo {
    private final String baid;

    public PaypalPaymentMethod(String id,
                               String accountId,
                               String baid,
                               Boolean defaultMethod,
                               String email,
                               String type) {
        super(id, accountId, defaultMethod, email, "paypal");
        this.baid = baid;
    }

    public String getBaid() {
        return baid;
    }

}
