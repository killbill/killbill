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

package org.killbill.billing.overdue.config;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;
import org.killbill.billing.overdue.config.api.BillingState;
import org.killbill.billing.overdue.config.api.OverdueStateSet;
import org.killbill.billing.overdue.wrapper.OverdueWrapper;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public abstract class DefaultOverdueStateSet extends ValidatingConfig<DefaultOverdueConfig> implements OverdueStateSet {

    private static final Period ZERO_PERIOD = new Period();
    private final DefaultOverdueState clearState = new DefaultOverdueState().setName(OverdueWrapper.CLEAR_STATE_NAME).setClearState(true);

    public abstract DefaultOverdueState[] getStates();

    @Override
    public OverdueState findState(final String stateName) throws OverdueApiException {
        if (stateName.equals(OverdueWrapper.CLEAR_STATE_NAME)) {
            return clearState;
        }
        for (final DefaultOverdueState state : getStates()) {
            if (state.getName().equals(stateName)) {
                return state;
            }
        }
        throw new OverdueApiException(ErrorCode.CAT_NO_SUCH_OVERDUE_STATE, stateName);
    }

    /* (non-Javadoc)
     * @see org.killbill.billing.catalog.overdue.OverdueBillingState#findClearState()
     */
    @Override
    public DefaultOverdueState getClearState() throws OverdueApiException {
        return clearState;
    }

    @Override
    public DefaultOverdueState calculateOverdueState(final BillingState billingState, final LocalDate now) throws OverdueApiException {
        for (final DefaultOverdueState overdueState : getStates()) {
            if (overdueState.getConditionEvaluation() != null && overdueState.getConditionEvaluation().evaluate(billingState, now)) {
                return overdueState;
            }
        }
        return getClearState();
    }

    @Override
    public ValidationErrors validate(final DefaultOverdueConfig root,
                                     final ValidationErrors errors) {
        for (final DefaultOverdueState state : getStates()) {
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

    @Override
    public OverdueState getFirstState() {
        return getStates()[size() - 1];
    }
}
