/*
 * Copyright 2015 Groupon, Inc
 * Copyright 2015 The Billing Project, LLC
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

import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="ComboHostedPaymentPage")
public class ComboHostedPaymentPageJson extends ComboPaymentJson {

    //@ApiModelProperty(name = required = true)
    private final HostedPaymentPageFieldsJson hostedPaymentPageFields;

    @JsonCreator
    public ComboHostedPaymentPageJson(@JsonProperty("account") final AccountJson account,
                                      @JsonProperty("paymentMethod") final PaymentMethodJson paymentMethod,
                                      @JsonProperty("hostedPaymentPageFields") final HostedPaymentPageFieldsJson hostedPaymentPageFields,
                                      @JsonProperty("paymentMethodPluginProperties") final List<PluginPropertyJson> paymentMethodPluginProperties,
                                      @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(account, paymentMethod, paymentMethodPluginProperties, auditLogs);
        this.hostedPaymentPageFields = hostedPaymentPageFields;
    }

    public HostedPaymentPageFieldsJson getHostedPaymentPageFields() {
        return hostedPaymentPageFields;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ComboHostedPaymentPageJson{");
        sb.append("hostedPaymentPageFields=").append(hostedPaymentPageFields);
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
        if (!super.equals(o)) {
            return false;
        }

        final ComboHostedPaymentPageJson that = (ComboHostedPaymentPageJson) o;

        return !(hostedPaymentPageFields != null ? !hostedPaymentPageFields.equals(that.hostedPaymentPageFields) : that.hostedPaymentPageFields != null);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (hostedPaymentPageFields != null ? hostedPaymentPageFields.hashCode() : 0);
        return result;
    }
}
