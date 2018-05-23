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

package org.killbill.billing.jaxrs.json;

import java.util.UUID;

import org.killbill.billing.tenant.api.Tenant;
import org.killbill.billing.tenant.api.TenantData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Tenant", parent = JsonBase.class)
public class TenantJson extends JsonBase {

    private final UUID tenantId;
    private final String externalKey;
    @ApiModelProperty(required = true)
    private final String apiKey;
    @ApiModelProperty(required = true)
    private final String apiSecret;

    @JsonCreator
    public TenantJson(@JsonProperty("tenantId") final UUID tenantId,
                      @JsonProperty("externalKey") final String externalKey,
                      @JsonProperty("apiKey") final String apiKey,
                      @JsonProperty("apiSecret") final String apiSecret) {
        this.tenantId = tenantId;
        this.externalKey = externalKey;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public TenantJson(final Tenant tenant) {
        this(tenant.getId(), tenant.getExternalKey(), tenant.getApiKey(), tenant.getApiSecret());
    }

    public TenantData toTenantData() {
        return new TenantData() {
            @Override
            public String getExternalKey() {
                return externalKey;
            }

            @Override
            public String getApiKey() {
                return apiKey;
            }

            @Override
            public String getApiSecret() {
                return apiSecret;
            }
        };
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("TenantJson");
        sb.append("{tenantId='").append(tenantId).append('\'');
        sb.append(", externalKey='").append(externalKey).append('\'');
        sb.append(", apiKey='").append(apiKey).append('\'');
        sb.append(", apiSecret='").append(apiSecret).append('\'');
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

        final TenantJson that = (TenantJson) o;

        if (apiKey != null ? !apiKey.equals(that.apiKey) : that.apiKey != null) {
            return false;
        }
        if (apiSecret != null ? !apiSecret.equals(that.apiSecret) : that.apiSecret != null) {
            return false;
        }
        if (externalKey != null ? !externalKey.equals(that.externalKey) : that.externalKey != null) {
            return false;
        }
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = tenantId != null ? tenantId.hashCode() : 0;
        result = 31 * result + (externalKey != null ? externalKey.hashCode() : 0);
        result = 31 * result + (apiKey != null ? apiKey.hashCode() : 0);
        result = 31 * result + (apiSecret != null ? apiSecret.hashCode() : 0);
        return result;
    }
}
