/*
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

import java.util.Map;
import java.util.UUID;

import org.killbill.billing.payment.plugin.api.HostedPaymentPageFormDescriptor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="HostedPaymentPageFormDescriptor", parent = JsonBase.class)
public class HostedPaymentPageFormDescriptorJson extends JsonBase {

    private final UUID kbAccountId;
    private final String formMethod;
    private final String formUrl;
    private final Map<String, Object> formFields;
    private final Map<String, Object> properties;

    @JsonCreator
    public HostedPaymentPageFormDescriptorJson(@JsonProperty("kbAccountId") final UUID kbAccountId,
                                               @JsonProperty("formMethod") final String formMethod,
                                               @JsonProperty("formUrl") final String formUrl,
                                               @JsonProperty("formFields") final Map<String, Object> formFields,
                                               @JsonProperty("properties") final Map<String, Object> properties) {
        this.kbAccountId = kbAccountId;
        this.formMethod = formMethod;
        this.formUrl = formUrl;
        this.formFields = formFields;
        this.properties = properties;
    }

    public HostedPaymentPageFormDescriptorJson(final HostedPaymentPageFormDescriptor descriptor) {
        this.kbAccountId = descriptor.getKbAccountId();
        this.formMethod = descriptor.getFormMethod();
        this.formUrl = descriptor.getFormUrl();
        this.formFields = propertiesToMap(descriptor.getFormFields());
        this.properties = propertiesToMap(descriptor.getProperties());
    }

    public UUID getKbAccountId() {
        return kbAccountId;
    }

    public String getFormMethod() {
        return formMethod;
    }

    public String getFormUrl() {
        return formUrl;
    }

    public Map<String, Object> getFormFields() {
        return formFields;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HostedPaymentPageFormDescriptorJson{");
        sb.append("kbAccountId='").append(kbAccountId).append('\'');
        sb.append(", formMethod='").append(formMethod).append('\'');
        sb.append(", formUrl='").append(formUrl).append('\'');
        sb.append(", formFields=").append(formFields);
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

        final HostedPaymentPageFormDescriptorJson that = (HostedPaymentPageFormDescriptorJson) o;

        if (formFields != null ? !formFields.equals(that.formFields) : that.formFields != null) {
            return false;
        }
        if (formMethod != null ? !formMethod.equals(that.formMethod) : that.formMethod != null) {
            return false;
        }
        if (formUrl != null ? !formUrl.equals(that.formUrl) : that.formUrl != null) {
            return false;
        }
        if (kbAccountId != null ? !kbAccountId.equals(that.kbAccountId) : that.kbAccountId != null) {
            return false;
        }
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = kbAccountId != null ? kbAccountId.hashCode() : 0;
        result = 31 * result + (formMethod != null ? formMethod.hashCode() : 0);
        result = 31 * result + (formUrl != null ? formUrl.hashCode() : 0);
        result = 31 * result + (formFields != null ? formFields.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }
}
