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

package com.ning.billing.overdue.config.api;

import org.joda.time.LocalDate;

import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.overdue.OverdueApiException;
import com.ning.billing.overdue.OverdueState;

public interface OverdueStateSet {

    public abstract OverdueState getClearState() throws OverdueApiException;

    public abstract OverdueState findState(String stateName) throws OverdueApiException;

    /**
     * Compute an overdue state, given a billing state, at a given day.
     *
     * @param billingState the billing state
     * @param now          the day to use to calculate the overdue state, in the account timezone
     * @return the overdue state
     * @throws OverdueApiException
     */
    public abstract OverdueState calculateOverdueState(BillingState billingState, LocalDate now) throws OverdueApiException;

    public abstract int size();

    public abstract OverdueState getFirstState();
}
