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

import org.killbill.billing.payment.api.PluginProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="PluginProperty")
public class PluginPropertyJson {

    private final String key;
    private final String value;
    private final Boolean isUpdatable;

    @JsonCreator
    public PluginPropertyJson(@JsonProperty("key") final String key,
                              @JsonProperty("value") final String value,
                              @JsonProperty("isUpdatable") final Boolean isUpdatable) {
        this.key = key;
        this.value = value;
        this.isUpdatable = isUpdatable;
    }

    public PluginPropertyJson(final PluginProperty pluginProperty) {
        this(pluginProperty.getKey(), pluginProperty.getValue() == null ? null : pluginProperty.getValue().toString(), pluginProperty.getIsUpdatable());
    }

    public PluginProperty toPluginProperty() {
        return new PluginProperty(key, value, isUpdatable);
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
        final StringBuilder sb = new StringBuilder("PluginPropertyJson{");
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

        final PluginPropertyJson that = (PluginPropertyJson) o;

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
