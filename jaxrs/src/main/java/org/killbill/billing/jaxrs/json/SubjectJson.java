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

import javax.annotation.Nullable;

import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

@ApiModel(value="Subject")
public class SubjectJson {

    private final String principal;
    private final Boolean isAuthenticated;
    private final Boolean isRemembered;
    private final SessionJson session;

    @JsonCreator
    public SubjectJson(@JsonProperty("principal") final String principal,
                       @JsonProperty("isAuthenticated") final Boolean isAuthenticated,
                       @JsonProperty("isRemembered") final Boolean isRemembered,
                       @JsonProperty("session") @Nullable final SessionJson session) {
        this.principal = principal;
        this.isAuthenticated = isAuthenticated;
        this.isRemembered = isRemembered;
        this.session = session;
    }

    public SubjectJson(final Subject subject) {
        this.principal = subject.getPrincipal() == null ? null : subject.getPrincipal().toString();
        this.isAuthenticated = subject.isAuthenticated();
        this.isRemembered = subject.isRemembered();
        final Session subjectSession = subject.getSession(false);
        this.session = subjectSession == null ? null : new SessionJson(subjectSession);
    }

    public String getPrincipal() {
        return principal;
    }

    public Boolean getIsAuthenticated() {
        return isAuthenticated;
    }

    public Boolean getIsRemembered() {
        return isRemembered;
    }

    public SessionJson getSession() {
        return session;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("SubjectJson{");
        sb.append("principal='").append(principal).append('\'');
        sb.append(", isAuthenticated=").append(isAuthenticated);
        sb.append(", isRemembered=").append(isRemembered);
        sb.append(", session=").append(session);
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

        final SubjectJson that = (SubjectJson) o;

        if (isAuthenticated != null ? !isAuthenticated.equals(that.isAuthenticated) : that.isAuthenticated != null) {
            return false;
        }
        if (isRemembered != null ? !isRemembered.equals(that.isRemembered) : that.isRemembered != null) {
            return false;
        }
        if (principal != null ? !principal.equals(that.principal) : that.principal != null) {
            return false;
        }
        if (session != null ? !session.equals(that.session) : that.session != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = principal != null ? principal.hashCode() : 0;
        result = 31 * result + (isAuthenticated != null ? isAuthenticated.hashCode() : 0);
        result = 31 * result + (isRemembered != null ? isRemembered.hashCode() : 0);
        result = 31 * result + (session != null ? session.hashCode() : 0);
        return result;
    }
}
