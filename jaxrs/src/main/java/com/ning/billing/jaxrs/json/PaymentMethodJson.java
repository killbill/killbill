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
import com.ning.billing.payment.api.PaymentMethodKVInfo;
import com.ning.billing.payment.api.PaymentMethodPlugin;

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
        this.paymentMethodId = paymentMethodId;
        this.accountId = accountId;
        this.isDefault = isDefault;
        this.pluginName = pluginName;
        this.pluginInfo = pluginInfo;
    }

    public static PaymentMethodJson toPaymentMethodJson(final Account account, final PaymentMethod in) {
        final boolean isDefault = account.getPaymentMethodId() != null && account.getPaymentMethodId().equals(in.getId());
        final PaymentMethodPlugin pluginDetail = in.getPluginDetail();
        PaymentMethodPluginDetailJson pluginDetailJson = null;
        if (pluginDetail != null) {
            List<PaymentMethodProperties> properties = null;
            if (pluginDetail.getProperties() != null) {
                properties = new ArrayList<PaymentMethodJson.PaymentMethodProperties>(Collections2.transform(pluginDetail.getProperties(), new Function<PaymentMethodKVInfo, PaymentMethodProperties>() {
                    @Override
                    public PaymentMethodProperties apply(final PaymentMethodKVInfo input) {
                        return new PaymentMethodProperties(input.getKey(), input.getValue() == null ? null : input.getValue().toString(), input.getIsUpdatable());
                    }
                }));
            }
            pluginDetailJson = new PaymentMethodPluginDetailJson(pluginDetail.getExternalPaymentMethodId(),
                                                                 pluginDetail.isDefaultPaymentMethod(),
                                                                 pluginDetail.getType(),
                                                                 pluginDetail.getCCName(),
                                                                 pluginDetail.getCCType(),
                                                                 pluginDetail.getCCExpirationMonth(),
                                                                 pluginDetail.getCCExpirationYear(),
                                                                 pluginDetail.getCCLast4(),
                                                                 pluginDetail.getAddress1(),
                                                                 pluginDetail.getAddress2(),
                                                                 pluginDetail.getCity(),
                                                                 pluginDetail.getState(),
                                                                 pluginDetail.getZip(),
                                                                 pluginDetail.getCountry(),
                                                                 properties);
        }
        return new PaymentMethodJson(in.getId().toString(), account.getId().toString(), isDefault, in.getPluginName(), pluginDetailJson);
    }

    public PaymentMethod toPaymentMethod(final String accountId) {
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
                return UUID.fromString(accountId);
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
                    public String getType() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCCName() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCCType() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCCExpirationMonth() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCCExpirationYear() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCCLast4() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getAddress1() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getAddress2() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCity() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getState() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getZip() {
                        // N/A
                        return null;
                    }

                    @Override
                    public String getCountry() {
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
                            final List<PaymentMethodKVInfo> result = new LinkedList<PaymentMethodKVInfo>();
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
        private final Boolean isDefaultPaymentMethod;
        private final String type;
        private final String ccName;
        private final String ccType;
        private final String ccExpirationMonth;
        private final String ccExpirationYear;
        private final String ccLast4;
        private final String address1;
        private final String address2;
        private final String city;
        private final String state;
        private final String zip;
        private final String country;
        private final List<PaymentMethodProperties> properties;

        @JsonCreator
        public PaymentMethodPluginDetailJson(@JsonProperty("externalPaymentId") final String externalPaymentId,
                                             @JsonProperty("isDefaultPaymentMethod") final Boolean isDefaultPaymentMethod,
                                             @JsonProperty("type") final String type,
                                             @JsonProperty("ccName") final String ccName,
                                             @JsonProperty("ccType") final String ccType,
                                             @JsonProperty("ccExpirationMonth") final String ccExpirationMonth,
                                             @JsonProperty("ccExpirationYear") final String ccExpirationYear,
                                             @JsonProperty("ccLast4") final String ccLast4,
                                             @JsonProperty("address1") final String address1,
                                             @JsonProperty("address2") final String address2,
                                             @JsonProperty("city") final String city,
                                             @JsonProperty("state") final String state,
                                             @JsonProperty("zip") final String zip,
                                             @JsonProperty("country") final String country,
                                             @JsonProperty("properties") final List<PaymentMethodProperties> properties) {
            this.externalPaymentId = externalPaymentId;
            this.isDefaultPaymentMethod = isDefaultPaymentMethod;
            this.type = type;
            this.ccName = ccName;
            this.ccType = ccType;
            this.ccExpirationMonth = ccExpirationMonth;
            this.ccExpirationYear = ccExpirationYear;
            this.ccLast4 = ccLast4;
            this.address1 = address1;
            this.address2 = address2;
            this.city = city;
            this.state = state;
            this.zip = zip;
            this.country = country;
            this.properties = properties;
        }

        public String getExternalPaymentId() {
            return externalPaymentId;
        }

        public Boolean getIsDefaultPaymentMethod() {
            return isDefaultPaymentMethod;
        }

        public String getType() {
            return type;
        }

        public String getCcName() {
            return ccName;
        }

        public String getCcType() {
            return ccType;
        }

        public String getCcExpirationMonth() {
            return ccExpirationMonth;
        }

        public String getCcExpirationYear() {
            return ccExpirationYear;
        }

        public String getCcLast4() {
            return ccLast4;
        }

        public String getAddress1() {
            return address1;
        }

        public String getAddress2() {
            return address2;
        }

        public String getCity() {
            return city;
        }

        public String getState() {
            return state;
        }

        public String getZip() {
            return zip;
        }

        public String getCountry() {
            return country;
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
