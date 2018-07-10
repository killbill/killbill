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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.killbill.billing.payment.plugin.api.GatewayNotification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="GatewayNotification", parent = JsonBase.class)
public class GatewayNotificationJson extends JsonBase {

    private final UUID kbPaymentId;
    private final Integer status;
    private final String entity;
    private final Map<String, List<String>> headers;
    private final Map<String, Object> properties;

    @JsonCreator
    public GatewayNotificationJson(@JsonProperty("kbPaymentId") final UUID kbPaymentId,
                                   @JsonProperty("status") final Integer status,
                                   @JsonProperty("entity") final String entity,
                                   @JsonProperty("headers") final Map<String, List<String>> headers,
                                   @JsonProperty("properties") final Map<String, Object> properties) {
        this.kbPaymentId = kbPaymentId;
        this.status = status;
        this.entity = entity;
        this.headers = headers;
        this.properties = properties;
    }

    public GatewayNotificationJson(final GatewayNotification notification) {
        this.kbPaymentId = notification.getKbPaymentId();
        this.status = notification.getStatus();
        this.entity = notification.getEntity();
        this.headers = notification.getHeaders();
        this.properties = propertiesToMap(notification.getProperties());
    }

    public Response toResponse() {
        final ResponseBuilder responseBuilder = Response.status(status == null ? Status.OK : Status.fromStatusCode(status));
        if (entity != null) {
            responseBuilder.entity(entity);
        }
        if (headers != null) {
            for (final String key : headers.keySet()) {
                if (headers.get(key) != null) {
                    for (final String value : headers.get(key)) {
                        responseBuilder.header(key, value);
                    }
                }
            }
        }

        return responseBuilder.build();
    }

    public UUID getKbPaymentId() {
        return kbPaymentId;
    }

    public Integer getStatus() {
        return status;
    }

    public String getEntity() {
        return entity;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("GatewayNotificationJson{");
        sb.append("kbPaymentId='").append(kbPaymentId).append('\'');
        sb.append(", status=").append(status);
        sb.append(", entity='").append(entity).append('\'');
        sb.append(", headers=").append(headers);
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

        final GatewayNotificationJson that = (GatewayNotificationJson) o;

        if (entity != null ? !entity.equals(that.entity) : that.entity != null) {
            return false;
        }
        if (headers != null ? !headers.equals(that.headers) : that.headers != null) {
            return false;
        }
        if (kbPaymentId != null ? !kbPaymentId.equals(that.kbPaymentId) : that.kbPaymentId != null) {
            return false;
        }
        if (properties != null ? !properties.equals(that.properties) : that.properties != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = kbPaymentId != null ? kbPaymentId.hashCode() : 0;
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (entity != null ? entity.hashCode() : 0);
        result = 31 * result + (headers != null ? headers.hashCode() : 0);
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }
}
