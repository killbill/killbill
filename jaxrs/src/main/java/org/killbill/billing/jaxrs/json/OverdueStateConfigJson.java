/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueCancellationPolicy;
import org.killbill.billing.overdue.api.OverdueState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OverdueStateConfigJson {

    private final String name;
    private final Boolean isClearState;
    private final OverdueConditionJson condition;
    private final String externalMessage;
    private final Boolean blockChanges;
    private final Boolean disableEntitlement;
    private final OverdueCancellationPolicy subscriptionCancellationPolicy;
    private final Integer autoReevaluationIntervalDays;

    @JsonCreator
    public OverdueStateConfigJson(@JsonProperty("name") final String name,
                                  @JsonProperty("isClearState") final Boolean isClearState,
                                  @JsonProperty("condition") final OverdueConditionJson condition,
                                  @JsonProperty("externalMessage") final String externalMessage,
                                  @JsonProperty("blockChanges") final Boolean blockChanges,
                                  @JsonProperty("disableEntitlement") final Boolean disableEntitlement,
                                  @JsonProperty("subscriptionCancellationPolicy") final OverdueCancellationPolicy subscriptionCancellationPolicy,
                                  @JsonProperty("autoReevaluationIntervalDays") final Integer autoReevaluationInterval) {
        this.name = name;
        this.isClearState = isClearState;
        this.condition = condition;
        this.externalMessage = externalMessage;
        this.blockChanges = blockChanges;
        this.disableEntitlement = disableEntitlement;
        this.subscriptionCancellationPolicy = subscriptionCancellationPolicy;
        this.autoReevaluationIntervalDays = autoReevaluationInterval;
    }

    public OverdueStateConfigJson(final OverdueState input) {
        this.name = input.getName();
        this.isClearState = input.isClearState();
        this.condition = input.getOverdueCondition() != null ? new OverdueConditionJson(input.getOverdueCondition()) : null;
        this.externalMessage = input.getExternalMessage();
        this.blockChanges = input.isBlockChanges();
        this.disableEntitlement = input.isDisableEntitlementAndChangesBlocked();
        this.subscriptionCancellationPolicy = input.getOverdueCancellationPolicy();
        Integer tmpAutoReevaluationIntervalDays = null;
        try {
            tmpAutoReevaluationIntervalDays = input.getAutoReevaluationInterval().getDays();
        } catch (final OverdueApiException e) {
        } finally {
            this.autoReevaluationIntervalDays = tmpAutoReevaluationIntervalDays;
        }
    }

    public String getName() {
        return name;
    }

    @JsonProperty("isClearState")
    public Boolean isClearState() {
        return isClearState;
    }

    public OverdueConditionJson getCondition() {
        return condition;
    }

    public String getExternalMessage() {
        return externalMessage;
    }

    public Boolean getBlockChanges() {
        return blockChanges;
    }

    public Boolean getDisableEntitlement() {
        return disableEntitlement;
    }

    public OverdueCancellationPolicy getSubscriptionCancellationPolicy() {
        return subscriptionCancellationPolicy;
    }

    public Integer getAutoReevaluationIntervalDays() {
        return autoReevaluationIntervalDays;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverdueStateConfigJson)) {
            return false;
        }

        final OverdueStateConfigJson that = (OverdueStateConfigJson) o;

        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (isClearState != null ? !isClearState.equals(that.isClearState) : that.isClearState != null) {
            return false;
        }
        if (condition != null ? !condition.equals(that.condition) : that.condition != null) {
            return false;
        }
        if (externalMessage != null ? !externalMessage.equals(that.externalMessage) : that.externalMessage != null) {
            return false;
        }
        if (blockChanges != null ? !blockChanges.equals(that.blockChanges) : that.blockChanges != null) {
            return false;
        }
        if (disableEntitlement != null ? !disableEntitlement.equals(that.disableEntitlement) : that.disableEntitlement != null) {
            return false;
        }
        if (subscriptionCancellationPolicy != that.subscriptionCancellationPolicy) {
            return false;
        }
        return autoReevaluationIntervalDays != null ? autoReevaluationIntervalDays.equals(that.autoReevaluationIntervalDays) : that.autoReevaluationIntervalDays == null;

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (isClearState != null ? isClearState.hashCode() : 0);
        result = 31 * result + (condition != null ? condition.hashCode() : 0);
        result = 31 * result + (externalMessage != null ? externalMessage.hashCode() : 0);
        result = 31 * result + (blockChanges != null ? blockChanges.hashCode() : 0);
        result = 31 * result + (disableEntitlement != null ? disableEntitlement.hashCode() : 0);
        result = 31 * result + (subscriptionCancellationPolicy != null ? subscriptionCancellationPolicy.hashCode() : 0);
        result = 31 * result + (autoReevaluationIntervalDays != null ? autoReevaluationIntervalDays.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "OverdueStateConfigJson{" +
               "name='" + name + '\'' +
               ", isClearState=" + isClearState +
               ", condition=" + condition +
               ", externalMessage='" + externalMessage + '\'' +
               ", blockChanges=" + blockChanges +
               ", disableEntitlement=" + disableEntitlement +
               ", subscriptionCancellationPolicy=" + subscriptionCancellationPolicy +
               ", autoReevaluationIntervalDays=" + autoReevaluationIntervalDays +
               '}';
    }
}
