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

import org.killbill.billing.catalog.api.CurrencyValueNull;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.api.OverdueState;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
}
