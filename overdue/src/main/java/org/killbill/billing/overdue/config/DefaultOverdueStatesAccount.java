/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.overdue.config;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import javax.xml.bind.annotation.XmlElement;

import org.joda.time.Period;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.overdue.api.OverdueStatesAccount;

public class DefaultOverdueStatesAccount extends DefaultOverdueStateSet implements OverdueStatesAccount, Externalizable {

    @XmlElement(required = false, name = "initialReevaluationInterval")
    private DefaultDuration initialReevaluationInterval;

    @SuppressWarnings("unchecked")
    @XmlElement(required = true, name = "state")
    private DefaultOverdueState[] accountOverdueStates = new DefaultOverdueState[0];

    // Required for deserialization
    public DefaultOverdueStatesAccount() {
    }

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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DefaultOverdueStatesAccount that = (DefaultOverdueStatesAccount) o;

        if (initialReevaluationInterval != null ? !initialReevaluationInterval.equals(that.initialReevaluationInterval) : that.initialReevaluationInterval != null) {
            return false;
        }
        return Arrays.equals(accountOverdueStates, that.accountOverdueStates);
    }

    @Override
    public int hashCode() {
        int result = initialReevaluationInterval != null ? initialReevaluationInterval.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(accountOverdueStates);
        return result;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeObject(initialReevaluationInterval);
        out.writeObject(accountOverdueStates);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.initialReevaluationInterval = (DefaultDuration) in.readObject();
        this.accountOverdueStates = (DefaultOverdueState[]) in.readObject();
    }
}
