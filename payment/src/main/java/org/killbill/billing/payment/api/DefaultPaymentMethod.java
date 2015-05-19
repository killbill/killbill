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

package org.killbill.billing.payment.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

import org.killbill.billing.payment.dao.PaymentMethodModelDao;
import org.killbill.billing.entity.EntityBase;
import org.killbill.billing.util.UUIDs;

public class DefaultPaymentMethod extends EntityBase implements PaymentMethod {

    private final String externalKey;
    private final UUID accountId;
    private final Boolean isActive;
    private final String pluginName;
    private final PaymentMethodPlugin pluginDetail;

    public DefaultPaymentMethod(final UUID paymentMethodId, final String externalKey, @Nullable final DateTime createdDate, @Nullable final DateTime updatedDate,
                                final UUID accountId, final Boolean isActive, final String pluginName, @Nullable final PaymentMethodPlugin pluginDetail) {
        super(paymentMethodId, createdDate, updatedDate);
        this.externalKey = externalKey;
        this.accountId = accountId;
        this.isActive = isActive;
        this.pluginName = pluginName;
        this.pluginDetail = pluginDetail;
    }

    public DefaultPaymentMethod(final String externalKey, final UUID accountId, final String pluginName, final PaymentMethodPlugin pluginDetail) {
        this(UUIDs.randomUUID(), externalKey, null, null, accountId, true, pluginName, pluginDetail);
    }

    public DefaultPaymentMethod(final UUID paymentMethodId, final String externalKey, final UUID accountId, final String pluginName) {
        this(paymentMethodId, externalKey, null, null, accountId, true, pluginName, null);
    }

    public DefaultPaymentMethod(final PaymentMethodModelDao input, @Nullable final PaymentMethodPlugin pluginDetail) {
        this(input.getId(), input.getExternalKey(), input.getCreatedDate(), input.getUpdatedDate(), input.getAccountId(), input.isActive(), input.getPluginName(), pluginDetail);
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public Boolean isActive() {
        return isActive;
    }

    @Override
    public String getPluginName() {
        return pluginName;
    }

    @Override
    public PaymentMethodPlugin getPluginDetail() {
        return pluginDetail;
    }
}
