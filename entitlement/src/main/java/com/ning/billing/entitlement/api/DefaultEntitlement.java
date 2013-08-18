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

import com.ning.billing.ErrorCode;
import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.clock.Clock;
import com.ning.billing.entitlement.EntitlementService;
import com.ning.billing.entitlement.block.BlockingChecker;
import com.ning.billing.entitlement.dao.BlockingStateDao;
import com.ning.billing.subscription.api.SubscriptionBase;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.callcontext.InternalCallContext;
import com.ning.billing.util.callcontext.InternalCallContextFactory;
import com.ning.billing.util.entity.EntityBase;
import com.ning.billing.util.svcapi.junction.DefaultBlockingState;

public class DefaultEntitlement extends EntityBase implements Entitlement {

    protected final EntitlementDateHelper dateHelper;
    protected final SubscriptionBase subscriptionBase;
    protected final InternalCallContextFactory internalCallContextFactory;
    protected final Clock clock;
    protected final EntitlementState state;
    protected final BlockingState entitlementBlockingState;
    protected final BlockingChecker checker;
    protected final UUID accountId;
    protected final String externalKey;
    protected final EntitlementApi entitlementApi;
    protected final DateTimeZone accountTimeZone;
    protected final BlockingStateDao blockingStateDao;

    public DefaultEntitlement(final EntitlementDateHelper dateHelper, final SubscriptionBase subscriptionBase, final UUID accountId,
                              final String externalKey, final EntitlementState state, final BlockingState entitlementBlockingState, final DateTimeZone accountTimeZone,
                              final EntitlementApi entitlementApi, final InternalCallContextFactory internalCallContextFactory,
                              final BlockingStateDao blockingStateDao,
                              final Clock clock, final BlockingChecker checker) {
        super(subscriptionBase.getId(), subscriptionBase.getCreatedDate(), subscriptionBase.getUpdatedDate());
        this.dateHelper = dateHelper;
        this.subscriptionBase = subscriptionBase;
        this.accountId = accountId;
        this.externalKey = externalKey;
        this.state = state;
        this.entitlementApi = entitlementApi;
        this.entitlementBlockingState = entitlementBlockingState;
        this.accountTimeZone = accountTimeZone;
        this.internalCallContextFactory = internalCallContextFactory;
        this.clock = clock;
        this.checker = checker;
        this.blockingStateDao = blockingStateDao;
    }

    public DefaultEntitlement(final DefaultEntitlement in) {
        this(in.getDateHelper(),
             in.getSubscriptionBase(),
             in.getAccountId(),
             in.getExternalKey(),
             in.getState(),
             in.getEntitlementBlockingState(),
             in.getAccountTimeZone(),
             in.getEntitlementApi(),
             in.getInternalCallContextFactory(),
             in.getBlockingStateDao(),
             in.getClock(), in.getChecker());
    }

    // STEPH_ENT should be remove but beatrix tests need to be changed
    public SubscriptionBase getSubscriptionBase() {
        return subscriptionBase;
    }

    public EntitlementDateHelper getDateHelper() {
        return dateHelper;
    }

    public InternalCallContextFactory getInternalCallContextFactory() {
        return internalCallContextFactory;
    }

    public EntitlementApi getEntitlementApi() {
        return entitlementApi;
    }

    public Clock getClock() {
        return clock;
    }

    public BlockingState getEntitlementBlockingState() {
        return entitlementBlockingState;
    }

    public BlockingChecker getChecker() {
        return checker;
    }

    public DateTimeZone getAccountTimeZone() {
        return accountTimeZone;
    }

    public BlockingStateDao getBlockingStateDao() {
        return blockingStateDao;
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
        return state;
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
            return new LocalDate(entitlementBlockingState.getEffectiveDate(), accountTimeZone);
        }
        return null;
        //return subscriptionBase.getEndDate() != null ? new LocalDate(subscriptionBase.getEndDate(), accountTimeZone) : null;
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
    public Entitlement cancelEntitlementWithPolicy(final EntitlementActionPolicy entitlementPolicy, final CallContext callContext) throws EntitlementApiException {
        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDate(cancellationDate, callContext);
    }

    @Override
    public Entitlement cancelEntitlementWithDate(final LocalDate localCancelDate, final CallContext callContext) throws EntitlementApiException {

        if (state == EntitlementState.CANCELLED) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime effectiveCancelDate = dateHelper.fromLocalDateAndReferenceTime(localCancelDate, subscriptionBase.getStartDate(), contextWithValidAccountRecordId);
        try {
            subscriptionBase.cancel(null, callContext);
            blockingStateDao.setBlockingState(new DefaultBlockingState(getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveCancelDate), clock, contextWithValidAccountRecordId);
            return entitlementApi.getEntitlementForId(getId(), callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }


    @Override
    public Entitlement cancelEntitlementWithPolicyOverrideBillingPolicy(final EntitlementActionPolicy entitlementPolicy, final BillingActionPolicy billingPolicy, final CallContext callContext) throws EntitlementApiException {
        final LocalDate cancellationDate = getLocalDateFromEntitlementPolicy(entitlementPolicy);
        return cancelEntitlementWithDateOverrideBillingPolicy(cancellationDate, billingPolicy, callContext);
    }

    @Override
    public Entitlement cancelEntitlementWithDateOverrideBillingPolicy(final LocalDate localCancelDate, final BillingActionPolicy billingPolicy, final CallContext callContext) throws EntitlementApiException {

        if (state == EntitlementState.CANCELLED) {
            throw new EntitlementApiException(ErrorCode.SUB_CANCEL_BAD_STATE, getId(), EntitlementState.CANCELLED);
        }
        final InternalCallContext contextWithValidAccountRecordId = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final LocalDate effectiveLocalDate = new LocalDate(localCancelDate, accountTimeZone);
        final DateTime effectiveDate = dateHelper.fromLocalDateAndReferenceTime(effectiveLocalDate, subscriptionBase.getStartDate(), contextWithValidAccountRecordId);
        try {
            subscriptionBase.cancelWithPolicy(null, billingPolicy, callContext);
            blockingStateDao.setBlockingState(new DefaultBlockingState(getId(), BlockingStateType.SUBSCRIPTION, DefaultEntitlementApi.ENT_STATE_CANCELLED, EntitlementService.ENTITLEMENT_SERVICE_NAME, true, true, false, effectiveDate), clock, contextWithValidAccountRecordId);
            return entitlementApi.getEntitlementForId(getId(), callContext);
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    private LocalDate getLocalDateFromEntitlementPolicy(final EntitlementActionPolicy entitlementPolicy) {
        final LocalDate cancellationDate;
        switch (entitlementPolicy) {
            case IMMEDIATE:
                cancellationDate = new LocalDate(clock.getUTCNow(), accountTimeZone);
                break;
            case END_OF_TERM:
                cancellationDate = subscriptionBase.getChargedThroughDate() != null ? new LocalDate(subscriptionBase.getChargedThroughDate(), accountTimeZone) : new LocalDate(clock.getUTCNow(), accountTimeZone);
                break;
            default:
                throw new RuntimeException("Unsupported policy " + entitlementPolicy);
        }
        return cancellationDate;
    }


    @Override
    public void uncancel(final CallContext context) throws EntitlementApiException {
        // STEPH_ENT
    }


    @Override
    public Entitlement changePlan(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final CallContext callContext) throws EntitlementApiException {

        if (state != EntitlementState.ACTIVE) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), state);
        }

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), context);
        try {
            checker.checkBlockedChange(subscriptionBase, context);
            subscriptionBase.changePlan(productName, billingPeriod, priceList, requestedDate, callContext);
            return entitlementApi.getEntitlementForId(getId(), callContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement changePlanOverrideBillingPolicy(final String productName, final BillingPeriod billingPeriod, final String priceList, final LocalDate localDate, final BillingActionPolicy actionPolicy, final CallContext callContext) throws EntitlementApiException {

        if (state != EntitlementState.ACTIVE) {
            throw new EntitlementApiException(ErrorCode.SUB_CHANGE_NON_ACTIVE, getId(), state);
        }

        final InternalCallContext context = internalCallContextFactory.createInternalCallContext(accountId, callContext);
        final DateTime requestedDate = dateHelper.fromLocalDateAndReferenceTime(localDate, subscriptionBase.getStartDate(), context);
        try {
            checker.checkBlockedChange(subscriptionBase, context);
            subscriptionBase.changePlanWithPolicy(productName, billingPeriod, priceList, requestedDate, actionPolicy, callContext);
            return entitlementApi.getEntitlementForId(getId(), callContext);
        } catch (BlockingApiException e) {
            throw new EntitlementApiException(e, e.getCode(), e.getMessage());
        } catch (SubscriptionBaseApiException e) {
            throw new EntitlementApiException(e);
        }
    }

    @Override
    public Entitlement block(final String serviceName, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Entitlement unblock(final String serviceName, final LocalDate effectiveDate, final CallContext context) throws EntitlementApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

}
