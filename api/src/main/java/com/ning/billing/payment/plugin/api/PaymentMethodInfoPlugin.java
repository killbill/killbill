/*
 * Copyright 2010-2013 Ning, Inc.
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

package com.ning.billing.payment.plugin.api;

import java.util.UUID;

/**
 * Returns the plugin view of existing payment methods
 */
public interface PaymentMethodInfoPlugin {

    /**
     *
     * @return the Killbill accountId
     */
    public UUID getAccountId();

    /**
     *
     * @return the killbillPaymentMethodId
     */
    public UUID getPaymentMethodId();

    /**
     *
     * @return default payment method set on the gateway
     */
    public boolean isDefault();

    /**
     *
     * @return the external paymentMethodId on the gateway
     */
    public String getExternalPaymentMethodId();
}
