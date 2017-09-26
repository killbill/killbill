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

import java.util.List;

import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;

import org.joda.time.Period;

import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.api.OverdueStatesAccount;

public class DefaultOverdueStatesAccount extends DefaultOverdueStateSet implements OverdueStatesAccount {

    @XmlElement(required = false, name = "initialReevaluationInterval")
    private DefaultDuration initialReevaluationInterval;

    @SuppressWarnings("unchecked")
    @XmlElement(required = true, name = "state")
    private DefaultOverdueState[] accountOverdueStates = new DefaultOverdueState[0];


    @Override
    public DefaultOverdueState[] getStates() {
        return accountOverdueStates;
    }

    @Override
    public Period getInitialReevaluationInterval() {
        if (initialReevaluationInterval == null || initialReevaluationInterval.getUnit() == TimeUnit.UNLIMITED || initialReevaluationInterval.getNumber() == 0) {
            return null;
        }
        return initialReevaluationInterval.toJodaPeriod();
    }

    public DefaultOverdueStatesAccount setAccountOverdueStates(final DefaultOverdueState[] accountOverdueStates) {
        this.accountOverdueStates = accountOverdueStates;
        return this;
    }

    public DefaultOverdueStatesAccount setInitialReevaluationInterval(final DefaultDuration initialReevaluationInterval) {
        this.initialReevaluationInterval = initialReevaluationInterval;
        return this;
    }
}
