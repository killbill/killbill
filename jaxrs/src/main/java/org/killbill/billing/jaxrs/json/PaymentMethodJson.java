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

package org.killbill.billing.jaxrs.json;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.payment.api.PaymentMethod;
import org.killbill.billing.payment.api.PaymentMethodPlugin;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

public class PaymentMethodJson extends JsonBase {

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
                             @JsonProperty("pluginInfo") final PaymentMethodPluginDetailJson pluginInfo,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.paymentMethodId = paymentMethodId;
        this.accountId = accountId;
        this.isDefault = isDefault;
        this.pluginName = pluginName;
        this.pluginInfo = pluginInfo;
    }

    public static PaymentMethodJson toPaymentMethodJson(final Account account, final PaymentMethod in, @Nullable final AccountAuditLogs accountAuditLogs) {
        final boolean isDefault = account.getPaymentMethodId() != null && account.getPaymentMethodId().equals(in.getId());
        final PaymentMethodPlugin pluginDetail = in.getPluginDetail();
        PaymentMethodPluginDetailJson pluginDetailJson = null;
        if (pluginDetail != null) {
            List<PaymentMethodProperties> properties = null;
            if (pluginDetail.getProperties() != null) {
                properties = new ArrayList<PaymentMethodJson.PaymentMethodProperties>(Collections2.transform(pluginDetail.getProperties(), new Function<PluginProperty, PaymentMethodProperties>() {
                    @Override
                    public PaymentMethodProperties apply(final PluginProperty input) {
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
        return new PaymentMethodJson(in.getId().toString(), account.getId().toString(), isDefault, in.getPluginName(),
                                     pluginDetailJson, toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForPaymentMethod(in.getId())));
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
                    public UUID getKbPaymentMethodId() {
                        return paymentMethodId == null ? null : UUID.fromString(paymentMethodId);
                    }

                    @Override
                    public boolean isDefaultPaymentMethod() {
                        // N/A
                        return false;
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
                    public List<PluginProperty> getProperties() {
                        if (pluginInfo.getProperties() != null) {
                            final List<PluginProperty> result = new LinkedList<PluginProperty>();
                            for (final PaymentMethodProperties cur : pluginInfo.getProperties()) {
                                result.add(new PluginProperty(cur.getKey(), cur.getValue(), cur.isUpdatable));
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

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("PaymentMethodJson{");
        sb.append("paymentMethodId='").append(paymentMethodId).append('\'');
        sb.append(", accountId='").append(accountId).append('\'');
        sb.append(", isDefault=").append(isDefault);
        sb.append(", pluginName='").append(pluginName).append('\'');
        sb.append(", pluginInfo=").append(pluginInfo);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PaymentMethodJson that = (PaymentMethodJson) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (isDefault != null ? !isDefault.equals(that.isDefault) : that.isDefault != null) {
            return false;
        }
        if (paymentMethodId != null ? !paymentMethodId.equals(that.paymentMethodId) : that.paymentMethodId != null) {
            return false;
        }
        if (pluginInfo != null ? !pluginInfo.equals(that.pluginInfo) : that.pluginInfo != null) {
            return false;
        }
        if (pluginName != null ? !pluginName.equals(that.pluginName) : that.pluginName != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = paymentMethodId != null ? paymentMethodId.hashCode() : 0;
        result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
        result = 31 * result + (isDefault != null ? isDefault.hashCode() : 0);
        result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
        result = 31 * result + (pluginInfo != null ? pluginInfo.hashCode() : 0);
        return result;
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PaymentMethodPluginDetailJson{");
            sb.append("externalPaymentId='").append(externalPaymentId).append('\'');
            sb.append(", isDefaultPaymentMethod=").append(isDefaultPaymentMethod);
            sb.append(", type='").append(type).append('\'');
            sb.append(", ccName='").append(ccName).append('\'');
            sb.append(", ccType='").append(ccType).append('\'');
            sb.append(", ccExpirationMonth='").append(ccExpirationMonth).append('\'');
            sb.append(", ccExpirationYear='").append(ccExpirationYear).append('\'');
            sb.append(", ccLast4='").append(ccLast4).append('\'');
            sb.append(", address1='").append(address1).append('\'');
            sb.append(", address2='").append(address2).append('\'');
            sb.append(", city='").append(city).append('\'');
            sb.append(", state='").append(state).append('\'');
            sb.append(", zip='").append(zip).append('\'');
            sb.append(", country='").append(country).append('\'');
            sb.append(", properties=").append(properties);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PaymentMethodPluginDetailJson that = (PaymentMethodPluginDetailJson) o;

            if (address1 != null ? !address1.equals(that.address1) : that.address1 != null) {
                return false;
            }
            if (address2 != null ? !address2.equals(that.address2) : that.address2 != null) {
                return false;
            }
            if (ccExpirationMonth != null ? !ccExpirationMonth.equals(that.ccExpirationMonth) : that.ccExpirationMonth != null) {
                return false;
            }
            if (ccExpirationYear != null ? !ccExpirationYear.equals(that.ccExpirationYear) : that.ccExpirationYear != null) {
                return false;
            }
            if (ccLast4 != null ? !ccLast4.equals(that.ccLast4) : that.ccLast4 != null) {
                return false;
            }
            if (ccName != null ? !ccName.equals(that.ccName) : that.ccName != null) {
                return false;
            }
            if (ccType != null ? !ccType.equals(that.ccType) : that.ccType != null) {
                return false;
            }
            if (city != null ? !city.equals(that.city) : that.city != null) {
                return false;
            }
            if (country != null ? !country.equals(that.country) : that.country != null) {
                return false;
            }
            if (externalPaymentId != null ? !externalPaymentId.equals(that.externalPaymentId) : that.externalPaymentId != null) {
                return false;
            }
            if (isDefaultPaymentMethod != null ? !isDefaultPaymentMethod.equals(that.isDefaultPaymentMethod) : that.isDefaultPaymentMethod != null) {
                return false;
            }
            if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
                return false;
            }
            if (state != null ? !state.equals(that.state) : that.state != null) {
                return false;
            }
            if (type != null ? !type.equals(that.type) : that.type != null) {
                return false;
            }
            if (zip != null ? !zip.equals(that.zip) : that.zip != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = externalPaymentId != null ? externalPaymentId.hashCode() : 0;
            result = 31 * result + (isDefaultPaymentMethod != null ? isDefaultPaymentMethod.hashCode() : 0);
            result = 31 * result + (type != null ? type.hashCode() : 0);
            result = 31 * result + (ccName != null ? ccName.hashCode() : 0);
            result = 31 * result + (ccType != null ? ccType.hashCode() : 0);
            result = 31 * result + (ccExpirationMonth != null ? ccExpirationMonth.hashCode() : 0);
            result = 31 * result + (ccExpirationYear != null ? ccExpirationYear.hashCode() : 0);
            result = 31 * result + (ccLast4 != null ? ccLast4.hashCode() : 0);
            result = 31 * result + (address1 != null ? address1.hashCode() : 0);
            result = 31 * result + (address2 != null ? address2.hashCode() : 0);
            result = 31 * result + (city != null ? city.hashCode() : 0);
            result = 31 * result + (state != null ? state.hashCode() : 0);
            result = 31 * result + (zip != null ? zip.hashCode() : 0);
            result = 31 * result + (country != null ? country.hashCode() : 0);
            result = 31 * result + (properties != null ? properties.hashCode() : 0);
            return result;
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

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PaymentMethodProperties{");
            sb.append("key='").append(key).append('\'');
            sb.append(", value='").append(value).append('\'');
            sb.append(", isUpdatable=").append(isUpdatable);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final PaymentMethodProperties that = (PaymentMethodProperties) o;

            if (isUpdatable != null ? !isUpdatable.equals(that.isUpdatable) : that.isUpdatable != null) {
                return false;
            }
            if (key != null ? !key.equals(that.key) : that.key != null) {
                return false;
            }
            if (value != null ? !value.equals(that.value) : that.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            result = 31 * result + (isUpdatable != null ? isUpdatable.hashCode() : 0);
            return result;
        }
    }
}
