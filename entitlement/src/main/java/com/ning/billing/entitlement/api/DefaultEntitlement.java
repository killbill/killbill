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
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountApiException;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.svcapi.account.AccountInternalApi;
import com.ning.billing.util.timezone.DateAndTimeZoneContext;

public class DefaultEntitlement extends EntityBase implements Entitlement {

    private final AccountInternalApi accountApi;
    private final SubscriptionBase subscriptionBase;
    private final InternalCallContextFactory internalCallContextFactory;
    private final Clock clock;
    private final boolean isBlocked;
    private final BlockingState entitlementBlockingState;
    private final BlockingChecker checker;
    private final UUID accountId;
    private final String externalKey;
    private final DateTimeZone accountTimeZone;

    public DefaultEntitlement(final AccountInternalApi accountApi, final SubscriptionBase subscriptionBase, final UUID accountId,
                              final String externalKey, final boolean isBlocked, final BlockingState entitlementBlockingState, final DateTimeZone accountTimeZone,
                              final InternalCallContextFactory internalCallContextFactory,
                              final Clock clock, final BlockingChecker checker) {
        super(subscriptionBase.getId(), subscriptionBase.getCreatedDate(), subscriptionBase.getUpdatedDate());
        this.accountApi = accountApi;
        this.subscriptionBase = subscriptionBase;
        this.accountId = accountId;
        this.externalKey = externalKey;
        this.isBlocked = isBlocked;
        this.entitlementBlockingState = entitlementBlockingState;
        this.accountTimeZone = accountTimeZone;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.checker = checker;
    }

    // STEPH_ENT should be remove but beatrix tests need to be changed
    public SubscriptionBase getSubscriptionBase() {
        return subscriptionBase;
    }

    @Override
    public UUID getBaseEntitlementId() {
        return subscriptionBase.getId();
    }

    @Override
    public UUID getBundleId() {
        return subscriptionBase.getBundleId();
    }

    @Override
    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public String getExternalKey() {
        return externalKey;
    }

    @Override
    public EntitlementState getState() {
        return isBlocked ? EntitlementState.BLOCKED : subscriptionBase.getState();
    }

    @Override
    public EntitlementSourceType getSourceType() {
        return subscriptionBase.getSourceType();
    }

    @Override
    public LocalDate getEffectiveStartDate() {
        return new LocalDate(subscriptionBase.getStartDate(), accountTimeZone);
    }

    @Override
    public LocalDate getEffectiveEndDate() {
        if (entitlementBlockingState != null && entitlementBlockingState.getStateName().equals(DefaultEntitlementApi.ENT_STATE_CANCELLED)) {
            return new LocalDate(entitlementBlockingState.getCreatedDate(), accountTimeZone);
        }
        return new LocalDate(subscriptionBase.getEndDate(), accountTimeZone);
    }

    @Override
    public LocalDate getRequestedEndDate() {
        // STEPH_ENT
        return null; //subscriptionBase.;
    }

    @Override
    public Product getProduct() {
        return subscriptionBase.getCurrentPlan().getProduct();
    }

    @Override
    public Plan getPlan() {
        return subscriptionBase.getCurrentPlan();
    }

    @Override
    public PriceList getPriceList() {
        return subscriptionBase.getCurrentPriceList();
    }

    @Override
    public PlanPhase getCurrentPhase() {
        return subscriptionBase.getCurrentPhase();
    }

    @Override
    public ProductCategory getProductCategory() {
        return subscriptionBase.getCategory();
    }

    @Override
    public Product getLastActiveProduct() {
        return subscriptionBase.getLastActiveProduct();
    }

    @Override
    public Plan getLastActivePlan() {
        return subscriptionBase.getLastActivePlan();
    }

    @Override
    public PriceList getLastActivePriceList() {
        return subscriptionBase.getLastActivePriceList();
    }

    @Override
    public ProductCategory getLastActiveProductCategory() {
        return subscriptionBase.getLastActiveCategory();
    }


    @Override
    public boolean cancelEntitlementWithDate(final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime requestedDate = fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), clock, context);
        try {
            return subscriptionBase.cancel(requestedDate, callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    @Override
    public boolean cancelEntitlementWithPolicy(final EntitlementActionPolicy entitlementBillingActionPolicy, final CallContext callContext) throws EntitlementApiException {
        return false;
    }

    @Override
    public boolean cancelEntitlementWithDateOverrideBillingPolicy(final LocalDate effectiveDate, final BillingActionPolicy billingPolicy, final CallContext context) throws EntitlementApiException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean cancelEntitlementWithPolicyOverrideBillingPolicy(final EntitlementActionPolicy policy, final BillingActionPolicy billingPolicy, final CallContext context) throws EntitlementApiException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void uncancel(final CallContext context) throws EntitlementApiException {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    @Override
    public boolean changePlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime requestedDate = fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), clock, context);
        try {
            checker.checkBlockedChange(subscriptionBase, context);
            return subscriptionBase.changePlan(productName, billingPeriod, priceList, requestedDate, callContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public boolean changePlanOverrideBillingPolicy(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final BillingActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {
        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime requestedDate = fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), clock, context);
        try {
            checker.checkBlockedChange(subscriptionBase, context);
            return subscriptionBase.changePlanWithPolicy(productName, billingPeriod, priceList, requestedDate, actionPolicy, callContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public boolean block(final String serviceName, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {



        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean unblock(final String serviceName, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }



    /**
     * Returns a DateTime that is equals or beforeNow and whose LocalDate using the account timeZone is the one provided
     * <p/>
     * Relies on the subscriptionStartDate for the reference time
     *
     * @param requestedDate
     * @param subscriptionStartDate
     * @param clock
     * @param callContext
     * @return
     * @throws EntitlementApiException
     */
    private DateTime fromLocalDateAndReferenceTime(final LocalDate requestedDate, final DateTime subscriptionStartDate, final Clock clock, final InternalCallContext callContext) throws EntitlementApiException {
        try {
            final Account account = accountApi.getAccountByRecordId(callContext.getAccountRecordId(), callContext);
            final DateAndTimeZoneContext timeZoneContext = new DateAndTimeZoneContext(subscriptionStartDate, account.getTimeZone(), clock);
            final DateTime computedTime = timeZoneContext.computeUTCDateTimeFromLocalDate(requestedDate);

            return computedTime.isAfter(clock.getUTCNow()) ? clock.getUTCNow() : computedTime;
        } catch (AccountApiException e) {
            throw new EntitlementApiException(e);
        }
    }


}
