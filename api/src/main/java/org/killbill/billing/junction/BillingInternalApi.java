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

package org.killbill.billing.junction;

import java.util.UUID;

import org.joda.time.LocalDate;
import org.killbill.billing.account.api.AccountApiException;
import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.invoice.api.DryRunArguments;
import org.killbill.billing.subscription.api.user.SubscriptionBaseApiException;

public interface BillingInternalApi {

    /**
     * Note: this method assumes the lock is taken (https://github.com/killbill/killbill/issues/282)
     *
     * @return an ordered list of billing event for the given accounts
     */
    public BillingEventSet getBillingEventsForAccountAndUpdateAccountBCD(UUID accountId, DryRunArguments dryRunArguments, LocalDate cutoffDt, InternalCallContext context) throws CatalogApiException, AccountApiException, SubscriptionBaseApiException;
}
