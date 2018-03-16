/*
 * Copyright 2010-2013 Ning, Inc.
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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="PaymentMethod", parent = JsonBase.class)
public class PaymentMethodJson extends JsonBase {

    private final String externalKey;
    private final UUID paymentMethodId;
    private final UUID accountId;
    private final Boolean isDefault;
    private final String pluginName;
    private final PaymentMethodPluginDetailJson pluginInfo;

    @JsonCreator
    public PaymentMethodJson(@JsonProperty("paymentMethodId") final UUID paymentMethodId,
                             @JsonProperty("externalKey") final String externalKey,
                             @JsonProperty("accountId") final UUID accountId,
                             @JsonProperty("isDefault") final Boolean isDefault,
                             @JsonProperty("pluginName") final String pluginName,
                             @JsonProperty("pluginInfo") @Nullable final PaymentMethodPluginDetailJson pluginInfo,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.externalKey = externalKey;
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
            List<PluginPropertyJson> properties = null;
            if (pluginDetail.getProperties() != null) {
                properties = new ArrayList<PluginPropertyJson>(Collections2.transform(pluginDetail.getProperties(), new Function<PluginProperty, PluginPropertyJson>() {
                    @Override
                    public PluginPropertyJson apply(final PluginProperty input) {
                        return new PluginPropertyJson(input.getKey(), input.getValue() == null ? null : input.getValue().toString(), input.getIsUpdatable());
                    }
                }));
            }
            pluginDetailJson = new PaymentMethodPluginDetailJson(pluginDetail.getExternalPaymentMethodId(),
                                                                 pluginDetail.isDefaultPaymentMethod(),
                                                                 properties);
        }
        return new PaymentMethodJson(in.getId(), in.getExternalKey(), account.getId(), isDefault, in.getPluginName(),
                                     pluginDetailJson, toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForPaymentMethod(in.getId())));
    }

    public PaymentMethod toPaymentMethod(final UUID accountId) {
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
                return paymentMethodId;
            }

            @Override
            public String getExternalKey() {
                return externalKey;
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
                return accountId;
            }

            @Override
            public PaymentMethodPlugin getPluginDetail() {
                return new PaymentMethodPlugin() {
                    @Override
                    public UUID getKbPaymentMethodId() {
                        return paymentMethodId;
                    }

                    @Override
                    public boolean isDefaultPaymentMethod() {
                        // N/A
                        return false;
                    }

                    @Override
                    public String getExternalPaymentMethodId() {
                        return pluginInfo != null ? pluginInfo.getExternalPaymentMethodId() : null;
                    }

                    @Override
                    public List<PluginProperty> getProperties() {
                        if (pluginInfo != null && pluginInfo.getProperties() != null) {
                            final List<PluginProperty> result = new LinkedList<PluginProperty>();
                            for (final PluginPropertyJson cur : pluginInfo.getProperties()) {
                                result.add(new PluginProperty(cur.getKey(), cur.getValue(), cur.getIsUpdatable()));
                            }
                            return result;
                        }
                        return null;
                    }
                };
            }
        };
    }

    public UUID getPaymentMethodId() {
        return paymentMethodId;
    }

    public UUID getAccountId() {
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

    public String getExternalKey() {
        return externalKey;
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


    @ApiModel(value="PaymentMethodPluginDetail")
    public static class PaymentMethodPluginDetailJson {

        private final String externalPaymentMethodId;
        private final Boolean isDefaultPaymentMethod;
        private final List<PluginPropertyJson> properties;

        @JsonCreator
        public PaymentMethodPluginDetailJson(@JsonProperty("externalPaymentMethodId") final String externalPaymentMethodId,
                                             @JsonProperty("isDefaultPaymentMethod") final Boolean isDefaultPaymentMethod,
                                             @JsonProperty("properties") final List<PluginPropertyJson> properties) {
            this.externalPaymentMethodId = externalPaymentMethodId;
            this.isDefaultPaymentMethod = isDefaultPaymentMethod;
            this.properties = properties;
        }

        public String getExternalPaymentMethodId() {
            return externalPaymentMethodId;
        }

        public Boolean getIsDefaultPaymentMethod() {
            return isDefaultPaymentMethod;
        }

        public List<PluginPropertyJson> getProperties() {
            return properties;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PaymentMethodPluginDetailJson{");
            sb.append("externalPaymentMethodId='").append(externalPaymentMethodId).append('\'');
            sb.append(", isDefaultPaymentMethod=").append(isDefaultPaymentMethod);
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

            if (externalPaymentMethodId != null ? !externalPaymentMethodId.equals(that.externalPaymentMethodId) : that.externalPaymentMethodId != null) {
                return false;
            }
            if (isDefaultPaymentMethod != null ? !isDefaultPaymentMethod.equals(that.isDefaultPaymentMethod) : that.isDefaultPaymentMethod != null) {
                return false;
            }
            if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = externalPaymentMethodId != null ? externalPaymentMethodId.hashCode() : 0;
            result = 31 * result + (isDefaultPaymentMethod != null ? isDefaultPaymentMethod.hashCode() : 0);
            result = 31 * result + (properties != null ? properties.hashCode() : 0);
            return result;
        }
    }
}
