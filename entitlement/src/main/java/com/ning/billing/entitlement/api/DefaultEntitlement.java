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

package com.ning.billing.entitlement.api;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.subscription.api.user.Subscription;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.timezone.DateAndTimeZoneContext;

public class DefaultEntitlement implements Entitlement {

    private final AccountInternalApi accountApi;
    private final Subscription subscription;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;
    private final BlockingChecker checker;

    public DefaultEntitlement(final AccountInternalApi accountApi, final Subscription subscription, final InternalCallContextFactory internalCallContextFactory, final Clock clock, final BlockingChecker checker) {
        this.accountApi = accountApi;
        this.subscription = subscription;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.checker = checker;
    }


    @Override
    public boolean cancelEntitlementWithDate(final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        final DateTime requestedDate = fromLocalDateAndReferenceTime(localDate, subscription.getStartDate(), clock, context);
        try {
            return subscription.cancel(requestedDate, callContext);
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public boolean cancelEntitlementWithPolicy(final EntitlementActionPolicy entitlementActionPolicy, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean cancel(final LocalDate localDate, final ActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean cancelEntitlementWithDateOverrideBillingPolicy(final EntitlementActionPolicy entitlementActionPolicy, final ActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean uncancel(final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean changePlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(callContext);
        final DateTime requestedDate = fromLocalDateAndReferenceTime(localDate, subscription.getStartDate(), clock, context);
        try {
            checker.checkBlockedChange(subscription, context);
            return subscription.changePlan(productName, billingPeriod, priceList, requestedDate, callContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        } catch (SubscriptionUserApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public boolean changePlanOverrideBillingPolicy(final String s, final BillingPeriod billingPeriod, final String s2, final LocalDate localDate, final ActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean pause(final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean resume(final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {
        return false;
    }


    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public UUID getId() {
        return subscription.getId();
    }

    /*
      public SubscriptionSourceType getSourceType();

    public DateTime getStartDate();

    public DateTime getEndDate();

    public DateTime getFutureEndDate();

    public Plan getCurrentPlan();

    public Plan getLastActivePlan();

    public PriceList getCurrentPriceList();

    public PlanPhase getCurrentPhase();

    public String getLastActiveProductName();

    public String getLastActivePriceListName();

    public String getLastActiveCategoryName();

    public String getLastActiveBillingPeriod();

    public DateTime getChargedThroughDate();

    public DateTime getPaidThroughDate();

    public ProductCategory getCategory();

    public SubscriptionTransition getPendingTransition();

    public SubscriptionTransition getPreviousTransition();

    public List<SubscriptionTransition> getAllTransitions();
     */


    private DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime subscriptionStartDate, final Clock clock, final InternalCallContext callContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final DateAndTimeZoneContext timeZoneContext = new DateAndTimeZoneContext(subscriptionStartDate, account.getTimeZone(), clock);
            return timeZoneContext.computeUTCDateTimeFromLocalDate(requestedDate);
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }

}
