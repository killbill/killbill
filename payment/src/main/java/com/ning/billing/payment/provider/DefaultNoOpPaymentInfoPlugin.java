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
package com.ning.billing.payment.provider;

import java.math.BigDecimal;

import org.joda.time.DateTime;

import com.ning.billing.payment.plugin.api.PaymentInfoPlugin;

public class DefaultNoOpPaymentInfoPlugin implements PaymentInfoPlugin {

    private final BigDecimal amount;
    private final DateTime effectiveDate;
    private final DateTime createdDate;
    private final PaymentPluginStatus status;
    private final String error;


    public DefaultNoOpPaymentInfoPlugin(final BigDecimal amount, final DateTime effectiveDate,
                                        final DateTime createdDate, final PaymentPluginStatus status, final String error) {
        super();
        this.amount = amount;
        this.effectiveDate = effectiveDate;
        this.createdDate = createdDate;
        this.status = status;
        this.error = error;
    }


    @Override
    public BigDecimal getAmount() {
        return amount;
    }


    @Override
    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    @Override
    public PaymentPluginStatus getStatus() {
        return status;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public String getGatewayError() {
        return error;
    }

    @Override
    public String getGatewayErrorCode() {
        return null;
    }
}
