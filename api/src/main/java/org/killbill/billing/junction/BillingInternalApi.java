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

package org.killbill.billing.junction;

import java.util.UUID;

import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;

public interface BillingInternalApi {

    /**
     * @return an ordered list of billing event for the given accounts
     */
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(UUID accountId, DryRunArguments dryRunArguments, InternalCallContext context) throws CatalogApiException, AccountApiException, SubscriptionBaseApiException;
}
