/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.payment.api;

import java.util.List;
import java.util.UUID;

public class TestPaymentMethodPlugin extends TestPaymentMethodPluginBase implements PaymentMethodPlugin {

    private final UUID kbPaymentMethodId;
    private final String externalPaymentMethodId;
    private final boolean isDefaultPaymentMethod;
    private final List<PluginProperty> properties;

    public TestPaymentMethodPlugin(final UUID kbPaymentMethodId, final PaymentMethodPlugin src, final String externalPaymentId) {
        this.kbPaymentMethodId = kbPaymentMethodId;
        this.externalPaymentMethodId = externalPaymentId;
        this.isDefaultPaymentMethod = src.isDefaultPaymentMethod();
        this.properties = src.getProperties();
    }

    @Override
    public UUID getKbPaymentMethodId() {
        return kbPaymentMethodId;
    }

    @Override
    public String getExternalPaymentMethodId() {
        return externalPaymentMethodId;
    }

    @Override
    public boolean isDefaultPaymentMethod() {
        return isDefaultPaymentMethod;
    }

    @Override
    public List<PluginProperty> getProperties() {
        return properties;
    }
}
