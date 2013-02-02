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
package com.ning.billing.jaxrs.json;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.PaymentMethodPlugin;
import com.ning.billing.payment.api.PaymentMethodPlugin.PaymentMethodKVInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class PaymentMethodJson {

    private final String paymentMethodId;
    private final String accountId;
    private final Boolean isDefault;
    private final String pluginName;
    private final PaymentMethodPluginDetailJson pluginInfo;

    @JsonCreator
    public PaymentMethodJson(@JsonProperty("paymentMethodId") final String paymentMethodId,
                             @JsonProperty("accountId") final String accountId,
                             @JsonProperty("isDefault") final Boolean isDefault,
                             @JsonProperty("pluginName") final String pluginName,
                             @JsonProperty("pluginInfo") final PaymentMethodPluginDetailJson pluginInfo) {
        super();
        this.paymentMethodId = paymentMethodId;
        this.accountId = accountId;
        this.isDefault = isDefault;
        this.pluginName = pluginName;
        this.pluginInfo = pluginInfo;
    }

    public static PaymentMethodJson toPaymentMethodJson(final Account account, final PaymentMethod in) {

        final boolean isDefault = account.getPaymentMethodId() != null && account.getPaymentMethodId().equals(in.getId());
        PaymentMethodPluginDetailJson detail = null;
        if (in.getPluginDetail() != null) {
            List<PaymentMethodProperties> properties = null;
            if (in.getPluginDetail().getProperties() != null) {
                properties = new ArrayList<PaymentMethodJson.PaymentMethodProperties>(Collections2.transform(in.getPluginDetail().getProperties(), new Function<PaymentMethodKVInfo, PaymentMethodProperties>() {
                    @Override
                    public PaymentMethodProperties apply(final PaymentMethodKVInfo input) {
                        return new PaymentMethodProperties(input.getKey(), input.getValue() == null ? null : input.getValue().toString(), input.getIsUpdatable());
                    }
                }));
            }
            detail = new PaymentMethodPluginDetailJson(in.getPluginDetail().getExternalPaymentMethodId(), properties);
        }
        return new PaymentMethodJson(in.getId().toString(), account.getId().toString(), isDefault, in.getPluginName(), detail);
    }

    public PaymentMethod toPaymentMethod() {
        return new PaymentMethod() {
            @Override
            public Boolean isActive() {
                return true;
            }

            @Override
            public String getPluginName() {
                return pluginName;
            }

            @Override
            public UUID getId() {
                return paymentMethodId != null ? UUID.fromString(paymentMethodId) : null;
            }

            @Override
            public DateTime getCreatedDate() {
                return null;
            }

            @Override
            public DateTime getUpdatedDate() {
                return null;
            }

            @Override
            public UUID getAccountId() {
                return accountId != null ? UUID.fromString(accountId) : null;
            }

            @Override
            public PaymentMethodPlugin getPluginDetail() {
                return new PaymentMethodPlugin() {
                    @Override
                    public boolean isDefaultPaymentMethod() {
                        // N/A
                        return false;
                    }

                    @Override
                    public String getValueString(final String key) {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getExternalPaymentMethodId() {
                        return pluginInfo.getExternalPaymentId();
                    }

                    @Override
                    public List<PaymentMethodKVInfo> getProperties() {
                        if (pluginInfo.getProperties() != null) {
                            final List<PaymentMethodKVInfo> result = new LinkedList<PaymentMethodPlugin.PaymentMethodKVInfo>();
                            for (final PaymentMethodProperties cur : pluginInfo.getProperties()) {
                                result.add(new PaymentMethodKVInfo(cur.getKey(), cur.getValue(), cur.isUpdatable));
                            }
                            return result;
                        }
                        return null;
                    }
                };
            }
        };
    }

    public PaymentMethodJson() {
        this(null, null, null, null, null);
    }

    public String getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getAccountId() {
        return accountId;
    }

    @JsonProperty("isDefault")
    public Boolean isDefault() {
        return isDefault;
    }

    public String getPluginName() {
        return pluginName;
    }

    public PaymentMethodPluginDetailJson getPluginInfo() {
        return pluginInfo;
    }

    public static class PaymentMethodPluginDetailJson {

        private final String externalPaymentId;
        private final List<PaymentMethodProperties> properties;


        @JsonCreator
        public PaymentMethodPluginDetailJson(@JsonProperty("externalPaymentId") final String externalPaymentId,
                                             @JsonProperty("properties") final List<PaymentMethodProperties> properties) {
            super();
            this.externalPaymentId = externalPaymentId;
            this.properties = properties;
        }

        public PaymentMethodPluginDetailJson() {
            this(null, null);
        }

        public String getExternalPaymentId() {
            return externalPaymentId;
        }

        public List<PaymentMethodProperties> getProperties() {
            return properties;
        }
    }

    public static final class PaymentMethodProperties {
        private final String key;
        private final String value;
        private final Boolean isUpdatable;

        @JsonCreator
        public PaymentMethodProperties(@JsonProperty("key") final String key,
                                       @JsonProperty("value") final String value,
                                       @JsonProperty("isUpdatable") final Boolean isUpdatable) {
            super();
            this.key = key;
            this.value = value;
            this.isUpdatable = isUpdatable;
        }

        public PaymentMethodProperties() {
            this(null, null, null);
        }


        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public Boolean getIsUpdatable() {
            return isUpdatable;
        }
    }
}
