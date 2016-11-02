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

package org.killbill.billing.jaxrs.json;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.BlockingStateType;
import org.killbill.billing.util.audit.AccountAuditLogs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

public class BlockingStateJson extends JsonBase {

    @ApiModelProperty(dataType = "java.util.UUID")
    private final String blockedId;
    private final String stateName;
    private final String service;
    private final Boolean blockChange;
    private final Boolean blockEntitlement;
    private final Boolean blockBilling;
    private final DateTime effectiveDate;
    private final BlockingStateType type;

    @JsonCreator
    public BlockingStateJson(@JsonProperty("blockedId") final String blockedId,
                             @JsonProperty("stateName") final String stateName,
                             @JsonProperty("service") final String service,
                             @JsonProperty("blockChange") final Boolean blockChange,
                             @JsonProperty("blockEntitlement") final Boolean blockEntitlement,
                             @JsonProperty("blockBilling") final Boolean blockBilling,
                             @JsonProperty("effectiveDate") final DateTime effectiveDate,
                             @JsonProperty("type") final BlockingStateType type,
                             @JsonProperty("auditLogs") @Nullable final List<AuditLogJson> auditLogs) {
        super(auditLogs);
        this.blockedId = blockedId;
        this.stateName = stateName;
        this.service = service;
        this.blockChange = blockChange;
        this.blockEntitlement = blockEntitlement;
        this.blockBilling = blockBilling;
        this.effectiveDate = effectiveDate;
        this.type = type;
    }

    public BlockingStateJson(final BlockingState input, final AccountAuditLogs accountAuditLogs) {
        this(input.getBlockedId().toString(),
             input.getStateName(),
             input.getService(),
             input.isBlockChange(),
             input.isBlockEntitlement(),
             input.isBlockBilling(),
             input.getEffectiveDate(),
             input.getType(),
             toAuditLogJson(accountAuditLogs == null ? null : accountAuditLogs.getAuditLogsForBlockingState(input.getId())));
    }


    public String getBlockedId() {
        return blockedId;
    }

    public String getStateName() {
        return stateName;
    }

    public String getService() {
        return service;
    }

    public Boolean isBlockChange() {
        return blockChange;
    }

    public Boolean isBlockEntitlement() {
        return blockEntitlement;
    }

    public Boolean isBlockBilling() {
        return blockBilling;
    }

    public DateTime getEffectiveDate() {
        return effectiveDate;
    }

    public BlockingStateType getType() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockingStateJson)) {
            return false;
        }

        final BlockingStateJson that = (BlockingStateJson) o;

        if (blockChange != that.blockChange) {
            return false;
        }
        if (blockEntitlement != that.blockEntitlement) {
            return false;
        }
        if (blockBilling != that.blockBilling) {
            return false;
        }
        if (blockedId != null ? !blockedId.equals(that.blockedId) : that.blockedId != null) {
            return false;
        }
        if (stateName != null ? !stateName.equals(that.stateName) : that.stateName != null) {
            return false;
        }
        if (service != null ? !service.equals(that.service) : that.service != null) {
            return false;
        }
        if (effectiveDate != null ? effectiveDate.compareTo(that.effectiveDate) != 0 : that.effectiveDate != null) {
            return false;
        }
        return type == that.type;

    }

    @Override
    public int hashCode() {
        int result = blockedId != null ? blockedId.hashCode() : 0;
        result = 31 * result + (stateName != null ? stateName.hashCode() : 0);
        result = 31 * result + (service != null ? service.hashCode() : 0);
        result = 31 * result + (blockChange ? 1 : 0);
        result = 31 * result + (blockEntitlement ? 1 : 0);
        result = 31 * result + (blockBilling ? 1 : 0);
        result = 31 * result + (effectiveDate != null ? effectiveDate.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "BlockingStateJson{" +
               "blockedId='" + blockedId + '\'' +
               ", stateName='" + stateName + '\'' +
               ", service='" + service + '\'' +
               ", blockChange=" + blockChange +
               ", blockEntitlement=" + blockEntitlement +
               ", blockBilling=" + blockBilling +
               ", effectiveDate=" + effectiveDate +
               ", type=" + type +
               '}';
    }
}
