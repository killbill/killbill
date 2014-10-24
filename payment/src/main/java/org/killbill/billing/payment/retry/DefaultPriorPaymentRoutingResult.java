/*
 * Copyright 2014 Groupon, Inc
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

package org.killbill.billing.payment.retry;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.routing.plugin.api.PriorPaymentRoutingResult;

public class DefaultPriorPaymentRoutingResult implements PriorPaymentRoutingResult {

    private final boolean isAborted;
    private final BigDecimal adjustedRetryAmount;
    private final Currency adjustedCurrency;
    private final UUID adjustedPaymentMethodId;

    public DefaultPriorPaymentRoutingResult(final boolean isAborted,
                                            final BigDecimal adjustedRetryAmount,
                                            final Currency adjustedCurrency,
                                            final UUID adjustedPaymentMethodId) {
        this.isAborted = isAborted;
        this.adjustedRetryAmount = adjustedRetryAmount;
        this.adjustedCurrency = adjustedCurrency;
        this.adjustedPaymentMethodId = adjustedPaymentMethodId;
    }

    public DefaultPriorPaymentRoutingResult(final boolean isAborted) {
        this(isAborted, null, null, null);
    }

    @Override
    public boolean isAborted() {
        return isAborted;
    }

    @Override
    public BigDecimal getAdjustedAmount() {
        return adjustedRetryAmount;
    }

    @Override
    public Currency getAdjustedCurrency() {
        return adjustedCurrency;
    }

    @Override
    public UUID getAdjustedPaymentMethodId() {
        return adjustedPaymentMethodId;
    }
}
