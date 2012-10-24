/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.util.callcontext;

import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;

/**
 * Internal use only
 */
public class InternalCallContext extends InternalTenantContext {

    private final UUID userToken;
    private final String userName;
    private final CallOrigin callOrigin;
    private final UserType userType;
    private final String reasonCode;
    private final String comment;
    private final DateTime createdDate;
    private final DateTime updatedDate;

    public InternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final UUID userToken, final String userName,
                               final CallOrigin callOrigin, final UserType userType, final String reasonCode, final String comment,
                               final DateTime createdDate, final DateTime updatedDate) {
        super(tenantRecordId, accountRecordId);
        this.userToken = userToken;
        this.userName = userName;
        this.callOrigin = callOrigin;
        this.userType = userType;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.createdDate = createdDate;
        this.updatedDate = updatedDate;
    }

    public InternalCallContext(final Long tenantRecordId, @Nullable final Long accountRecordId, final CallContext callContext) {
        this(tenantRecordId, accountRecordId, callContext.getUserToken(), callContext.getUserName(), callContext.getCallOrigin(),
             callContext.getUserType(), callContext.getReasonCode(), callContext.getComment(), callContext.getCreatedDate(),
             callContext.getUpdatedDate());
    }


    // TODO should not be needed if all services are using internal API
    // Unfortunately not true as some APIs ae hidden in object-- e.g OverdueStateApplicator is doing subscription.cancelWithPolicy(polciy, context);
    //
    public CallContext toCallContext() {
        return new DefaultCallContext(null, userName, callOrigin, userType, reasonCode, comment, userToken, createdDate, updatedDate);
    }

    public UUID getUserToken() {
        return userToken;
    }

    public String getUserName() {
        return userName;
    }

    public CallOrigin getCallOrigin() {
        return callOrigin;
    }

    public UserType getUserType() {
        return userType;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComment() {
        return comment;
    }

    public DateTime getCreatedDate() {
        return createdDate;
    }

    public DateTime getUpdatedDate() {
        return updatedDate;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("InternalCallContext");
        sb.append("{userToken=").append(userToken);
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", callOrigin=").append(callOrigin);
        sb.append(", userType=").append(userType);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comment='").append(comment).append('\'');
        sb.append(", createdDate=").append(createdDate);
        sb.append(", updatedDate=").append(updatedDate);
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

        final InternalCallContext that = (InternalCallContext) o;

        if (callOrigin != that.callOrigin) {
            return false;
        }
        if (comment != null ? !comment.equals(that.comment) : that.comment != null) {
            return false;
        }
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) {
            return false;
        }
        if (reasonCode != null ? !reasonCode.equals(that.reasonCode) : that.reasonCode != null) {
            return false;
        }
        if (updatedDate != null ? !updatedDate.equals(that.updatedDate) : that.updatedDate != null) {
            return false;
        }
        if (userName != null ? !userName.equals(that.userName) : that.userName != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }
        if (userType != that.userType) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (userName != null ? userName.hashCode() : 0);
        result = 31 * result + (callOrigin != null ? callOrigin.hashCode() : 0);
        result = 31 * result + (userType != null ? userType.hashCode() : 0);
        result = 31 * result + (reasonCode != null ? reasonCode.hashCode() : 0);
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (updatedDate != null ? updatedDate.hashCode() : 0);
        return result;
    }
}
