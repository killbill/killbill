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

package com.ning.billing.payment.api;

import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableList;

public class TestPaymentMethodPluginBase implements PaymentMethodPlugin {

    @Override
    public String getExternalPaymentMethodId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean isDefaultPaymentMethod() {
        return false;
    }

    @Override
    public String getValueString(final String key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCCName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCCType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCCExpirationMonth() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCCExpirationYear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCCLast4() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAddress1() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAddress2() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getZip() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCountry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<PaymentMethodKVInfo> getProperties() {
        return ImmutableList.<PaymentMethodKVInfo>of();
    }
}
