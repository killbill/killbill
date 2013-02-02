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

package com.ning.billing.analytics.api.sanity;

import java.util.Collection;
import java.util.UUID;

import com.ning.billing.util.callcontext.TenantContext;

public interface AnalyticsSanityApi {

    /**
     * @return the list of account ids not in sync with entitlement
     */
    public Collection<UUID> checkAnalyticsInSyncWithEntitlement(TenantContext context);

    /**
     * @return the list of account ids not in sync with invoice
     */
    public Collection<UUID> checkAnalyticsInSyncWithInvoice(TenantContext context);

    /**
     * @return the list of account ids not in sync with payment
     */
    public Collection<UUID> checkAnalyticsInSyncWithPayment(TenantContext context);

    /**
     * @return the list of account ids not in sync with the tag service
     */
    public Collection<UUID> checkAnalyticsInSyncWithTag(TenantContext context);

    /**
     * @return the list of account ids not self-consistent in Analytics
     */
    public Collection<UUID> checkAnalyticsConsistency(TenantContext context);
}
