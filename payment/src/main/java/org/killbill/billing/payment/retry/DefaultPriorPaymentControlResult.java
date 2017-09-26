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

package org.killbill.billing.payment.retry;

import java.math.BigDecimal;
import java.util.UUID;

import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;

public class DefaultPriorPaymentControlResult implements PriorPaymentControlResult {

    private final boolean isAborted;
    private final BigDecimal adjustedRetryAmount;
    private final Currency adjustedCurrency;
    private final UUID adjustedPaymentMethodId;
    private final String adjustedPaymentPluginName;
    private final Iterable<PluginProperty> adjustedPluginProperties;

    public DefaultPriorPaymentControlResult(final boolean isAborted,
                                            final BigDecimal adjustedRetryAmount,
                                            final Currency adjustedCurrency,
                                            final UUID adjustedPaymentMethodId,
                                            final String adjustedPaymentPluginName,
                                            final Iterable<PluginProperty> adjustedPluginProperties) {
        this.isAborted = isAborted;
        this.adjustedRetryAmount = adjustedRetryAmount;
        this.adjustedCurrency = adjustedCurrency;
        this.adjustedPaymentMethodId = adjustedPaymentMethodId;
        this.adjustedPluginProperties = adjustedPluginProperties;
        this.adjustedPaymentPluginName = adjustedPaymentPluginName;
    }

    public DefaultPriorPaymentControlResult(final boolean isAborted, final BigDecimal adjustedRetryAmount) {
        this(isAborted, adjustedRetryAmount, null, null, null, null);
    }

    public DefaultPriorPaymentControlResult(final boolean isAborted) {
        this(isAborted, null);
    }


    public DefaultPriorPaymentControlResult(final boolean isAborted, final UUID adjustedPaymentMethodId, final String adjustedPaymentPluginName, final BigDecimal adjustedAmount, final Currency adjustedCurrency, final Iterable<PluginProperty> adjustedPluginProperties) {
        this(isAborted, adjustedAmount, adjustedCurrency, adjustedPaymentMethodId, adjustedPaymentPluginName, adjustedPluginProperties);
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

    @Override
    public String getAdjustedPluginName() {
        return adjustedPaymentPluginName;
    }

    @Override
    public Iterable<PluginProperty> getAdjustedPluginProperties() {
        return adjustedPluginProperties;
    }
}
