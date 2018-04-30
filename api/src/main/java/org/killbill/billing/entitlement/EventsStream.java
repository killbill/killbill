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

package org.killbill.billing.entitlement;

import java.util.Collection;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.entitlement.api.BlockingState;
import org.killbill.billing.entitlement.api.Entitlement.EntitlementState;
import org.killbill.billing.subscription.api.SubscriptionBase;

public interface EventsStream {

    UUID getAccountId();

    UUID getBundleId();

    String getBundleExternalKey();

    UUID getEntitlementId();

    EntitlementState getEntitlementState();

    LocalDate getEntitlementEffectiveStartDate();

    LocalDate getEntitlementEffectiveEndDate();

    DateTime getEntitlementEffectiveStartDateTime();

    DateTime getEntitlementEffectiveEndDateTime();

    SubscriptionBase getSubscriptionBase();

    SubscriptionBase getBasePlanSubscriptionBase();

    boolean isEntitlementActive();

    boolean isEntitlementPending();

    boolean isBlockChange();

    boolean isEntitlementCancelled();

    boolean isSubscriptionCancelled();

    boolean isBlockChange(final DateTime effectiveDate);

    boolean isBlockEntitlement(final DateTime effectiveDate);

    int getDefaultBillCycleDayLocal();

    Collection<BlockingState> getPendingEntitlementCancellationEvents();

    BlockingState getEntitlementCancellationEvent();

    // All blocking states for the account, associated bundle or subscription
    Collection<BlockingState> getBlockingStates();

    Collection<BlockingState> computeAddonsBlockingStatesForNextSubscriptionBaseEvent(DateTime effectiveDate);

    Collection<BlockingState> computeAddonsBlockingStatesForFutureSubscriptionBaseEvents();

    InternalTenantContext getInternalTenantContext();
}
