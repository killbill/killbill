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

package com.ning.billing.entitlement.api.billing;

import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;

public interface EntitlementBillingApi {


    /**
     *
     * @param accountId 
     * @return an ordered list of billing event for the given accounts
     *
     */
    public SortedSet<BillingEvent> getBillingEventsForAccount(UUID accountId);

    public UUID getAccountIdFromSubscriptionId(UUID subscriptionId) throws EntitlementBillingApiException;

    public void setChargedThroughDate(UUID subscriptionId, DateTime ctd) throws EntitlementBillingApiException;

}
