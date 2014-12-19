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

package org.killbill.billing.overdue.config.api;

import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueState;

public interface OverdueStateSet {

    public OverdueState getClearState() throws OverdueApiException;

    public OverdueState findState(String stateName) throws OverdueApiException;

    /**
     * Compute an overdue state, given a billing state, at a given day.
     *
     * @param billingState the billing state
     * @param now          the day to use to calculate the overdue state, in the account timezone
     * @return the overdue state
     * @throws OverdueApiException
     */
    public OverdueState calculateOverdueState(BillingState billingState, LocalDate now) throws OverdueApiException;

    public int size();

    public OverdueState getFirstState();

    public Period getInitialReevaluationInterval();
}
