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

import java.util.List;

public interface PaymentMethodPlugin {

    public String getExternalPaymentMethodId();

    public boolean isDefaultPaymentMethod();

    public List<PaymentMethodKVInfo> getProperties();

    public String getValueString(String key);

    public class PaymentMethodKVInfo {
        private final String key;
        private final Object value;
        private final Boolean isUpdatable;

        public PaymentMethodKVInfo(final String key, final Object value, final Boolean isUpdatable) {
            super();
            this.key = key;
            this.value = value;
            this.isUpdatable = isUpdatable;
        }

        public String getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Boolean getIsUpdatable() {
            return isUpdatable;
        }
    }
}
