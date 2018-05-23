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

import java.util.List;

import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.DefaultDuration;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueState;
import org.killbill.billing.overdue.config.DefaultOverdueStatesAccount;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.swagger.annotations.ApiModel;

@ApiModel(value="Overdue")
public class OverdueJson {

    private final Integer initialReevaluationIntervalDays;
    private final List<OverdueStateConfigJson> overdueStates;

    @JsonCreator
    public OverdueJson(@JsonProperty("initialReevaluationInterval") final Integer initialReevaluationInterval,
                       @JsonProperty("overdueStates") final List<OverdueStateConfigJson> overdueStates) {
        this.initialReevaluationIntervalDays = initialReevaluationInterval;
        this.overdueStates = overdueStates;
    }

    public OverdueJson(final OverdueConfig overdueConfig) {
        this.initialReevaluationIntervalDays = overdueConfig.getOverdueStatesAccount().getInitialReevaluationInterval() != null ?
                                               overdueConfig.getOverdueStatesAccount().getInitialReevaluationInterval().getDays() : null;
        this.overdueStates = ImmutableList.copyOf(Iterables.transform(ImmutableList.copyOf(overdueConfig.getOverdueStatesAccount().getStates()), new Function<OverdueState, OverdueStateConfigJson>() {
            @Override
            public OverdueStateConfigJson apply(final OverdueState input) {
                    return new OverdueStateConfigJson(input);
            }
        }));
    }

    public Integer getInitialReevaluationInterval() {
        return initialReevaluationIntervalDays;
    }

    public List<OverdueStateConfigJson> getOverdueStates() {
        return overdueStates;
    }

    @Override
    public String toString() {
        return "OverdueJson{" +
               "initialReevaluationIntervalDays=" + initialReevaluationIntervalDays +
               ", overdueStates=" + overdueStates +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverdueJson)) {
            return false;
        }

        final OverdueJson that = (OverdueJson) o;

        if (initialReevaluationIntervalDays != null ? !initialReevaluationIntervalDays.equals(that.initialReevaluationIntervalDays) : that.initialReevaluationIntervalDays != null) {
            return false;
        }
        return overdueStates != null ? overdueStates.equals(that.overdueStates) : that.overdueStates == null;

    }

    @Override
    public int hashCode() {
        int result = initialReevaluationIntervalDays != null ? initialReevaluationIntervalDays.hashCode() : 0;
        result = 31 * result + (overdueStates != null ? overdueStates.hashCode() : 0);
        return result;
    }

    public static OverdueConfig toOverdueConfigWithValidation(final OverdueJson input) {
        final DefaultOverdueConfig result = new DefaultOverdueConfig();
        final DefaultOverdueStatesAccount overdueStateAccount = new DefaultOverdueStatesAccount();
        result.setOverdueStates(overdueStateAccount);

        final DefaultOverdueState [] states = new DefaultOverdueState[input.getOverdueStates().size()];
        int i = 0;


        int prevTimeSinceEarliestUnpaidInvoice = -1;
        for (final OverdueStateConfigJson cur : input.getOverdueStates()) {

            Preconditions.checkNotNull(cur.getName());

            // We only support timeSinceEarliestUnpaidInvoiceEqualsOrExceeds condition (see #611)
            Preconditions.checkNotNull(cur.getCondition());
            Preconditions.checkNotNull(cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds());
            Preconditions.checkNotNull(cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getUnit());
            Preconditions.checkState(cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getUnit() == TimeUnit.DAYS);
            Preconditions.checkState(cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getNumber() > 0);

            final DefaultOverdueState state = new DefaultOverdueState();
            state.setName(cur.getName());
            state.setExternalMessage(cur.getExternalMessage());
            state.setBlockChanges(cur.isBlockChanges());
            state.setDisableEntitlement(cur.isDisableEntitlement());
            state.setSubscriptionCancellationPolicy(cur.getSubscriptionCancellationPolicy());
            state.setClearState(cur.isClearState());
            state.setAutoReevaluationInterval(computeReevaluationInterval(cur.getAutoReevaluationIntervalDays(), prevTimeSinceEarliestUnpaidInvoice, cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getNumber()));
            state.setCondition(OverdueConditionJson.toOverdueCondition(cur.getCondition()));
            states[i++] = state;

            prevTimeSinceEarliestUnpaidInvoice = cur.getCondition().getTimeSinceEarliestUnpaidInvoiceEqualsOrExceeds().getNumber();
        }
        overdueStateAccount.setAccountOverdueStates(states);
        overdueStateAccount.setInitialReevaluationInterval(computeReevaluationInterval(null, prevTimeSinceEarliestUnpaidInvoice, 0));
        return result;
    }

    // Unless the user knows what it's doing (inputReevaluationInterval != null), for time based condition we set the reevaluation interval to match the transition to the next state
    private static DefaultDuration computeReevaluationInterval(final Integer inputReevaluationInterval,  int prevTimeSinceEarliestUnpaidInvoice, int curTimeSinceEarliestUnpaidInvoice) {
        if (inputReevaluationInterval != null && inputReevaluationInterval > 0) {
            return new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(inputReevaluationInterval);
        }

        if (prevTimeSinceEarliestUnpaidInvoice == -1) {
            return null;
        }

        Preconditions.checkState(prevTimeSinceEarliestUnpaidInvoice - curTimeSinceEarliestUnpaidInvoice > 0);

        return new DefaultDuration().setUnit(TimeUnit.DAYS).setNumber(prevTimeSinceEarliestUnpaidInvoice - curTimeSinceEarliestUnpaidInvoice);
    }
}
