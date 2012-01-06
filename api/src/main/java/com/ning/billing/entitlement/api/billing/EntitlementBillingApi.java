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

import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.account.api.Account;

public interface EntitlementBillingApi {

    /**
     *
     * @param accountId 
     * @return an ordered list of billing events for the given account
     * @return the list of accounts which have active subscriptions
     */
    public List<Account> getActiveAccounts();

    /**
     *
     * @param subscriptionId the subscriptionId of interest for a gievn account
     * @return an ordered list of billing event
     *
     */
    public SortedSet<BillingEvent> getBillingEventsForSubscription(UUID accountId);


    public void setChargedThroughDate(UUID subscriptionId, DateTime ctd);

}
