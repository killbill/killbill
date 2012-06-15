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

package com.ning.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.joda.time.DateTime;
import org.joda.time.Period;

import com.ning.billing.ErrorCode;
import com.ning.billing.junction.api.Blockable;
import com.ning.billing.junction.api.BlockingApi;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueState;
import com.ning.billing.overdue.config.api.BillingState;
import com.ning.billing.overdue.config.api.OverdueStateSet;
import com.ning.billing.util.config.ValidatingConfig;
import com.ning.billing.util.config.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class DefaultOverdueStateSet<T extends Blockable> extends ValidatingConfig<OverdueConfig> implements OverdueStateSet<T> {
    private static final Period ZERO_PERIOD = new Period();
    private final DefaultOverdueState<T> clearState = new DefaultOverdueState<T>().setName(BlockingApi.CLEAR_STATE_NAME).setClearState(true);

    protected abstract DefaultOverdueState<T>[] getStates();

    @Override
    public OverdueState<T> findState(final String stateName) throws OverdueApiException {
        if (stateName.equals(BlockingApi.CLEAR_STATE_NAME)) {
            return clearState;
        }
        for (final DefaultOverdueState<T> state : getStates()) {
            if (state.getName().equals(stateName)) {
                return state;
            }
        }
        throw new OverdueApiException(ErrorCode.CAT_NO_SUCH_OVEDUE_STATE, stateName);
    }


    /* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueBillingState#findClearState()
     */
    @Override
    public DefaultOverdueState<T> getClearState() throws OverdueApiException {
        return clearState;
    }

    /* (non-Javadoc)
     * @see com.ning.billing.catalog.overdue.OverdueBillingState#calculateOverdueState(com.ning.billing.catalog.api.overdue.BillingState, org.joda.time.DateTime)
     */
    @Override
    public DefaultOverdueState<T> calculateOverdueState(final BillingState<T> billingState, final DateTime now) throws OverdueApiException {
        for (final DefaultOverdueState<T> overdueState : getStates()) {
            if (overdueState.getCondition().evaluate(billingState, now)) {
                return overdueState;
            }
        }
        return getClearState();
    }

    @Override
    public ValidationErrors validate(final OverdueConfig root,
                                     final ValidationErrors errors) {
        for (final DefaultOverdueState<T> state : getStates()) {
            state.validate(root, errors);
        }
        try {
            getClearState();
        } catch (OverdueApiException e) {
            if (e.getCode() == ErrorCode.CAT_MISSING_CLEAR_STATE.getCode()) {
                errors.add("Overdue state set is missing a clear state.",
                           root.getURI(), this.getClass(), "");
            }
        }

        return errors;
    }

    @Override
    public int size() {
        return getStates().length;
    }

}
