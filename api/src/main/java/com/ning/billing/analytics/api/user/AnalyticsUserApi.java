/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api.user;

import java.util.List;

import com.ning.billing.account.api.Account;
import com.ning.billing.analytics.api.BusinessAccount;
import com.ning.billing.analytics.api.BusinessInvoice;
import com.ning.billing.analytics.api.BusinessInvoicePayment;
import com.ning.billing.analytics.api.BusinessOverdueStatus;
import com.ning.billing.analytics.api.BusinessSnapshot;
import com.ning.billing.analytics.api.BusinessSubscriptionTransition;
import com.ning.billing.analytics.api.BusinessTag;
import com.ning.billing.analytics.api.TimeSeriesData;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.TenantContext;

public interface AnalyticsUserApi {

    public BusinessSnapshot getBusinessSnapshot(Account account, TenantContext context);

    public BusinessAccount getAccountByKey(String accountKey, TenantContext context);

    public List<BusinessSubscriptionTransition> getTransitionsForBundle(String externalKey, TenantContext context);

    public List<BusinessInvoice> getInvoicesForAccount(String accountKey, TenantContext context);

    public List<BusinessInvoicePayment> getInvoicePaymentsForAccount(String accountKey, TenantContext context);

    public List<BusinessOverdueStatus> getOverdueStatusesForBundle(String externalKey, TenantContext context);

    public List<BusinessTag> getTagsForAccount(String accountKey, TenantContext context);

    /**
     * @return the number of accounts created per day
     */
    public TimeSeriesData getAccountsCreatedOverTime(TenantContext context);

    /**
     * @param productType catalog name
     * @param slug        plan phase name, as returned by PlanPhase#getName()
     * @return the number of new subscriptions created per day (transfers not included)
     */
    public TimeSeriesData getSubscriptionsCreatedOverTime(String productType, String slug, TenantContext context);

    /**
     * Rebuild all analytics tables for an account
     *
     * @param account account
     * @param context call context
     */
    void rebuildAnalyticsForAccount(Account account, CallContext context);
}
