/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.callcontext;

import java.util.UUID;

import org.joda.time.DateTime;

import org.killbill.billing.util.callcontext.CallOrigin;
import org.killbill.billing.util.callcontext.UserType;
import org.killbill.clock.Clock;

public class DefaultCallContext extends CallContextBase {

    private final DateTime createdDate;
    private final DateTime updateDate;

    public DefaultCallContext(final UUID accountId, final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType,
                              final UUID userToken, final Clock clock) {
        super(accountId, tenantId, userName, callOrigin, userType, userToken);
        this.createdDate = clock.getUTCNow();
        this.updateDate = createdDate;
    }

    public DefaultCallContext(final UUID accountId, final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType,
                              final String reasonCode, final String comment,
                              final UUID userToken, final Clock clock) {
        super(accountId, tenantId, userName, callOrigin, userType, reasonCode, comment, userToken);
        this.createdDate = clock.getUTCNow();
        this.updateDate = createdDate;
    }

    public DefaultCallContext(final UUID accountId, final UUID tenantId, final String userName, final DateTime createdDate, final String reasonCode,
                              final String comment, final UUID userToken) {
        super(accountId, tenantId, userName, null, null, reasonCode, comment, userToken);
        this.createdDate = createdDate;
        this.updateDate = createdDate;
    }

    public DefaultCallContext(final UUID accountId, final UUID tenantId, final String userName, final CallOrigin callOrigin, final UserType userType, final String reasonCode,
                              final String comment, final UUID userToken, final DateTime createdDate, final DateTime updatedDate) {
        super(accountId, tenantId, userName, callOrigin, userType, reasonCode, comment, userToken);
        this.createdDate = createdDate;
        this.updateDate = updatedDate;
    }

    @Override
    public DateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public DateTime getUpdatedDate() {
        return createdDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("CallContextBase");
        sb.append("{accountId=").append(accountId);
        sb.append(", tenantId='").append(tenantId).append('\'');
        sb.append(", userToken='").append(userToken).append('\'');
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", callOrigin=").append(callOrigin);
        sb.append(", userType=").append(userType);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comments='").append(comments).append('\'');
        sb.append(", createdDate='").append(createdDate).append('\'');
        sb.append(", updatedDate='").append(createdDate).append('\'');
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

        final DefaultCallContext that = (DefaultCallContext) o;

        if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) {
            return false;
        }
        if (tenantId != null ? !tenantId.equals(that.tenantId) : that.tenantId != null) {
            return false;
        }
        if (callOrigin != that.callOrigin) {
            return false;
        }
        if (comments != null ? !comments.equals(that.comments) : that.comments != null) {
            return false;
        }
        if (reasonCode != null ? !reasonCode.equals(that.reasonCode) : that.reasonCode != null) {
            return false;
        }
        if (userName != null ? !userName.equals(that.userName) : that.userName != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }
        if (createdDate != null ? createdDate.compareTo(that.createdDate) != 0 : that.createdDate != null) {
            return false;
        }
        if (userType != that.userType) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = accountId != null ? accountId.hashCode() : 0;
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (userName != null ? userName.hashCode() : 0);
        result = 31 * result + (callOrigin != null ? callOrigin.hashCode() : 0);
        result = 31 * result + (userType != null ? userType.hashCode() : 0);
        result = 31 * result + (reasonCode != null ? reasonCode.hashCode() : 0);
        result = 31 * result + (comments != null ? comments.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        return result;
    }
}
