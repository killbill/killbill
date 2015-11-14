/*
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

package org.killbill.billing.util.nodes.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PluginServiceInfoModelJson {

    private final String serviceTypeName;
    private final String registrationName;

    @JsonCreator
    public PluginServiceInfoModelJson(@JsonProperty("serviceTypeName") final String serviceTypeName,
                                      @JsonProperty("registrationName") final String registrationName) {
        this.serviceTypeName = serviceTypeName;
        this.registrationName = registrationName;
    }

    public String getServiceTypeName() {
        return serviceTypeName;
    }

    public String getRegistrationName() {
        return registrationName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PluginServiceInfoModelJson)) {
            return false;
        }

        final PluginServiceInfoModelJson that = (PluginServiceInfoModelJson) o;

        if (serviceTypeName != null ? !serviceTypeName.equals(that.serviceTypeName) : that.serviceTypeName != null) {
            return false;
        }
        return !(registrationName != null ? !registrationName.equals(that.registrationName) : that.registrationName != null);

    }

    @Override
    public int hashCode() {
        int result = serviceTypeName != null ? serviceTypeName.hashCode() : 0;
        result = 31 * result + (registrationName != null ? registrationName.hashCode() : 0);
        return result;
    }
}
