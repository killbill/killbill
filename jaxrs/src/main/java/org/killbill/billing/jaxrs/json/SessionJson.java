/*
 * Copyright 2010-2014 Ning, Inc.
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

import org.apache.shiro.session.Session;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="Session")
public class SessionJson {

    private final String id;
    @ApiModelProperty(dataType = "org.joda.time.DateTime")
    private final DateTime startDate;
    @ApiModelProperty(dataType = "org.joda.time.DateTime")
    private final DateTime lastAccessDate;
    private final Long timeout;
    private final String host;

    @JsonCreator
    public SessionJson(@JsonProperty("id") final String id,
                       @JsonProperty("startDate") final DateTime startDate,
                       @JsonProperty("lastAccessDate") final DateTime lastAccessDate,
                       @JsonProperty("timeout") final Long timeout,
                       @JsonProperty("host") final String host) {
        this.id = id;
        this.startDate = startDate;
        this.lastAccessDate = lastAccessDate;
        this.timeout = timeout;
        this.host = host;
    }

    public SessionJson(final Session session) {
        this.id = session.getId() == null ? null : session.getId().toString();
        this.startDate = session.getStartTimestamp() == null ? null : new DateTime(session.getStartTimestamp(), DateTimeZone.UTC);
        this.lastAccessDate = session.getLastAccessTime() == null ? null : new DateTime(session.getLastAccessTime(), DateTimeZone.UTC);
        this.timeout = session.getTimeout();
        this.host = session.getHost();
    }

    public String getId() {
        return id;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public DateTime getLastAccessDate() {
        return lastAccessDate;
    }

    public Long getTimeout() {
        return timeout;
    }

    public String getHost() {
        return host;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SessionJson{");
        sb.append("id='").append(id).append('\'');
        sb.append(", startDate=").append(startDate);
        sb.append(", lastAccessDate=").append(lastAccessDate);
        sb.append(", timeout=").append(timeout);
        sb.append(", host='").append(host).append('\'');
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

        final SessionJson that = (SessionJson) o;

        if (host != null ? !host.equals(that.host) : that.host != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (lastAccessDate != null ? !lastAccessDate.equals(that.lastAccessDate) : that.lastAccessDate != null) {
            return false;
        }
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) {
            return false;
        }
        if (timeout != null ? !timeout.equals(that.timeout) : that.timeout != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (lastAccessDate != null ? lastAccessDate.hashCode() : 0);
        result = 31 * result + (timeout != null ? timeout.hashCode() : 0);
        result = 31 * result + (host != null ? host.hashCode() : 0);
        return result;
    }
}
