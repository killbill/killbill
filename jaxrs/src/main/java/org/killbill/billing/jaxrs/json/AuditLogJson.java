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

import org.joda.time.DateTime;

import org.killbill.billing.ObjectType;
import org.killbill.billing.util.audit.AuditLog;
import org.killbill.billing.util.audit.AuditLogWithHistory;
import org.killbill.billing.util.entity.Entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value="AuditLog")
public class AuditLogJson {

    private final String changeType;
    @ApiModelProperty(dataType = "org.joda.time.DateTime")
    private final DateTime changeDate;
    private final String changedBy;
    private final String reasonCode;
    private final String comments;
    private final String userToken;

    private final ObjectType objectType;
    private final UUID objectId;

    private final Entity history;

    @JsonCreator
    public AuditLogJson(@JsonProperty("changeType") final String changeType,
                        @JsonProperty("changeDate") final DateTime changeDate,
                        @JsonProperty("objectType") final ObjectType objectType,
                        @JsonProperty("objectId") final UUID objectId,
                        @JsonProperty("changedBy") final String changedBy,
                        @JsonProperty("reasonCode") final String reasonCode,
                        @JsonProperty("comments") final String comments,
                        @JsonProperty("userToken") final String userToken,
                        @JsonProperty("history") final Entity history) {
        this.changeType = changeType;
        this.changeDate = changeDate;
        this.changedBy = changedBy;
        this.reasonCode = reasonCode;
        this.comments = comments;
        this.userToken = userToken;
        this.objectType = objectType;
        this.objectId = objectId;
        this.history = history;
    }

    public AuditLogJson(final AuditLogWithHistory auditLogWithHistory) {
        this(auditLogWithHistory.getChangeType().toString(), auditLogWithHistory.getCreatedDate(), auditLogWithHistory.getAuditedObjectType(), auditLogWithHistory.getAuditedEntityId(), auditLogWithHistory.getUserName(), auditLogWithHistory.getReasonCode(),
             auditLogWithHistory.getComment(), auditLogWithHistory.getUserToken(), auditLogWithHistory.getEntity());
    }

    public AuditLogJson(final AuditLog auditLog) {
        this(auditLog.getChangeType().toString(), auditLog.getCreatedDate(), auditLog.getAuditedObjectType(), auditLog.getAuditedEntityId(), auditLog.getUserName(), auditLog.getReasonCode(),
             auditLog.getComment(), auditLog.getUserToken(), null);
    }

    public String getChangeType() {
        return changeType;
    }

    public DateTime getChangeDate() {
        return changeDate;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getComments() {
        return comments;
    }

    public String getUserToken() {
        return userToken;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public UUID getObjectId() {
        return objectId;
    }

    public Entity getHistory() {
        return history;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("AuditLogJson");
        sb.append("{changeType='").append(changeType).append('\'');
        sb.append(", changeDate=").append(changeDate);
        sb.append(", objectType='").append(objectType).append('\'');
        sb.append(", objectId='").append(objectId).append('\'');
        sb.append(", changedBy=").append(changedBy);
        sb.append(", reasonCode='").append(reasonCode).append('\'');
        sb.append(", comments='").append(comments).append('\'');
        sb.append(", userToken='").append(userToken).append('\'');
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

        final AuditLogJson that = (AuditLogJson) o;

        if (changeDate != null ? changeDate.compareTo(that.changeDate) != 0 : that.changeDate != null) {
            return false;
        }
        if (changeType != null ? !changeType.equals(that.changeType) : that.changeType != null) {
            return false;
        }
        if (changedBy != null ? !changedBy.equals(that.changedBy) : that.changedBy != null) {
            return false;
        }
        if (comments != null ? !comments.equals(that.comments) : that.comments != null) {
            return false;
        }
        if (reasonCode != null ? !reasonCode.equals(that.reasonCode) : that.reasonCode != null) {
            return false;
        }
        if (userToken != null ? !userToken.equals(that.userToken) : that.userToken != null) {
            return false;
        }
        if (objectType != that.objectType) {
            return false;
        }
        if (objectId != null ? !objectId.equals(that.objectId) : that.objectId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = changeType != null ? changeType.hashCode() : 0;
        result = 31 * result + (changeDate != null ? changeDate.hashCode() : 0);
        result = 31 * result + (changedBy != null ? changedBy.hashCode() : 0);
        result = 31 * result + (reasonCode != null ? reasonCode.hashCode() : 0);
        result = 31 * result + (comments != null ? comments.hashCode() : 0);
        result = 31 * result + (userToken != null ? userToken.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (objectId != null ? objectId.hashCode() : 0);
        return result;
    }
}
