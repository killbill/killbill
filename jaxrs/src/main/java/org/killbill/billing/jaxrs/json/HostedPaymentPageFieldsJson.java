/*
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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;


@ApiModel(value="HostedPaymentPageFields")
public class HostedPaymentPageFieldsJson  {

    private final List<PluginPropertyJson> formFields;


    @JsonCreator
    public HostedPaymentPageFieldsJson(@JsonProperty("formFields") final List<PluginPropertyJson> formFields) {
        this.formFields = formFields;
    }

    public List<PluginPropertyJson> getFormFields() {
        return formFields;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("HostedPaymentPageFieldsJson{");
        sb.append(", formFields=").append(formFields);
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

        final HostedPaymentPageFieldsJson that = (HostedPaymentPageFieldsJson) o;

        if (formFields != null ? !formFields.equals(that.formFields) : that.formFields != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return formFields != null ? formFields.hashCode() : 0;
    }
}
