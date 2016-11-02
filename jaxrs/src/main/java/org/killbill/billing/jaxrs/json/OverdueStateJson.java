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

import java.util.List;

import org.joda.time.Period;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.util.config.definition.PaymentConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OverdueStateJson {

    private final String name;
    private final String externalMessage;
    private final List<Integer> daysBetweenPaymentRetries;
    private final Boolean disableEntitlementAndChangesBlocked;
    private final Boolean blockChanges;
    private final Boolean isClearState;
    private final Integer reevaluationIntervalDays;

    @JsonCreator
    public OverdueStateJson(@JsonProperty("name") final String name,
                            @JsonProperty("externalMessage") final String externalMessage,
                            @JsonProperty("daysBetweenPaymentRetries") final List<Integer> daysBetweenPaymentRetries,
                            @JsonProperty("disableEntitlementAndChangesBlocked") final Boolean disableEntitlementAndChangesBlocked,
                            @JsonProperty("blockChanges") final Boolean blockChanges,
                            @JsonProperty("clearState") final Boolean isClearState,
                            @JsonProperty("reevaluationIntervalDays") final Integer reevaluationIntervalDays) {
        this.name = name;
        this.externalMessage = externalMessage;
        this.daysBetweenPaymentRetries = daysBetweenPaymentRetries;
        this.disableEntitlementAndChangesBlocked = disableEntitlementAndChangesBlocked;
        this.blockChanges = blockChanges;
        this.isClearState = isClearState;
        this.reevaluationIntervalDays = reevaluationIntervalDays;
    }

    public OverdueStateJson(final OverdueState overdueState, final PaymentConfig paymentConfig) {
        this.name = overdueState.getName();
        this.externalMessage = overdueState.getExternalMessage();
        // TODO this is broken if the per tenant system property was updated, but should we really return that in the OverdueState ?
        this.daysBetweenPaymentRetries = paymentConfig.getPaymentFailureRetryDays(null);
        this.disableEntitlementAndChangesBlocked = overdueState.isDisableEntitlementAndChangesBlocked();
        this.blockChanges = overdueState.isBlockChanges();
        this.isClearState = overdueState.isClearState();

        Period reevaluationIntervalPeriod = null;
        try {
            reevaluationIntervalPeriod = overdueState.getAutoReevaluationInterval();
        } catch (final OverdueApiException ignored) {
        }

        if (reevaluationIntervalPeriod != null) {
            this.reevaluationIntervalDays = reevaluationIntervalPeriod.getDays();
        } else {
            this.reevaluationIntervalDays = null;
        }
    }

    public String getName() {
        return name;
    }

    public String getExternalMessage() {
        return externalMessage;
    }

    public List<Integer> getDaysBetweenPaymentRetries() {
        return daysBetweenPaymentRetries;
    }

    public Boolean isDisableEntitlementAndChangesBlocked() {
        return disableEntitlementAndChangesBlocked;
    }

    public Boolean isBlockChanges() {
        return blockChanges;
    }

    public Boolean isClearState() {
        return isClearState;
    }

    public Integer getReevaluationIntervalDays() {
        return reevaluationIntervalDays;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("OverdueStateJson");
        sb.append("{name='").append(name).append('\'');
        sb.append(", externalMessage='").append(externalMessage).append('\'');
        sb.append(", daysBetweenPaymentRetries=").append(daysBetweenPaymentRetries);
        sb.append(", disableEntitlementAndChangesBlocked=").append(disableEntitlementAndChangesBlocked);
        sb.append(", blockChanges=").append(blockChanges);
        sb.append(", isClearState=").append(isClearState);
        sb.append(", reevaluationIntervalDays=").append(reevaluationIntervalDays);
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

        final OverdueStateJson that = (OverdueStateJson) o;

        if (blockChanges != null ? !blockChanges.equals(that.blockChanges) : that.blockChanges != null) {
            return false;
        }
        if (daysBetweenPaymentRetries != null ? !daysBetweenPaymentRetries.equals(that.daysBetweenPaymentRetries) : that.daysBetweenPaymentRetries != null) {
            return false;
        }
        if (disableEntitlementAndChangesBlocked != null ? !disableEntitlementAndChangesBlocked.equals(that.disableEntitlementAndChangesBlocked) : that.disableEntitlementAndChangesBlocked != null) {
            return false;
        }
        if (externalMessage != null ? !externalMessage.equals(that.externalMessage) : that.externalMessage != null) {
            return false;
        }
        if (isClearState != null ? !isClearState.equals(that.isClearState) : that.isClearState != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (reevaluationIntervalDays != null ? !reevaluationIntervalDays.equals(that.reevaluationIntervalDays) : that.reevaluationIntervalDays != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (externalMessage != null ? externalMessage.hashCode() : 0);
        result = 31 * result + (daysBetweenPaymentRetries != null ? daysBetweenPaymentRetries.hashCode() : 0);
        result = 31 * result + (disableEntitlementAndChangesBlocked != null ? disableEntitlementAndChangesBlocked.hashCode() : 0);
        result = 31 * result + (blockChanges != null ? blockChanges.hashCode() : 0);
        result = 31 * result + (isClearState != null ? isClearState.hashCode() : 0);
        result = 31 * result + (reevaluationIntervalDays != null ? reevaluationIntervalDays.hashCode() : 0);
        return result;
    }
}
