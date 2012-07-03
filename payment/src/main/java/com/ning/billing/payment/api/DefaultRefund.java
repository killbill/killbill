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
package com.ning.billing.payment.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.EntityBase;

public class DefaultRefund extends EntityBase implements Refund {

    private final UUID paymentId;
    private final BigDecimal amount;
    private final Currency currency;
    private final boolean isAdjusted;

    public DefaultRefund(final UUID id, final UUID paymentId, final BigDecimal amount,
            final Currency currency, final boolean isAdjusted) {
        super(id);
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.isAdjusted = isAdjusted;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    public BigDecimal getRefundAmount() {
        return amount;
    }

    @Override
    public Currency getCurrency() {
        return currency;
    }

    @Override
    public boolean isAdjusted() {
        return isAdjusted;
    }
}
