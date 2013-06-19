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
    public String getType() {
        return "CreditCard";
    }

    @Override
    public String getCCName() {
        return "Bozo";
    }

    @Override
    public String getCCType() {
        return "Visa";
    }

    @Override
    public String getCCExpirationMonth() {
        return "12";
    }

    @Override
    public String getCCExpirationYear() {
        return "2013";
    }

    @Override
    public String getCCLast4() {
        return "4365";
    }

    @Override
    public String getAddress1() {
        return "34, street Foo";
    }

    @Override
    public String getAddress2() {
        return null;
    }

    @Override
    public String getCity() {
        return "SF";
    }

    @Override
    public String getState() {
        return "CA";
    }

    @Override
    public String getZip() {
        return "95321";
    }

    @Override
    public String getCountry() {
        return "Zimbawe";
    }

    @Override
    public List<PaymentMethodKVInfo> getProperties() {
        return ImmutableList.<PaymentMethodKVInfo>of();
    }
}
