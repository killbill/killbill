package com.ning.billing.subscription.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.ActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.subscription.api.user.SubscriptionSourceType;
import com.ning.billing.subscription.api.user.SubscriptionState;
import com.ning.billing.subscription.api.user.SubscriptionTransition;
import com.ning.billing.subscription.api.user.SubscriptionUserApiException;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;

public interface SubscriptionBase extends Entity, Blockable {


    public boolean cancel(final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean cancelWithPolicy(final DateTime requestedDate, final ActionPolicy policy, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean uncancel(final CallContext context)
            throws SubscriptionUserApiException;

    public boolean changePlan(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate,
                                        final ActionPolicy policy, final CallContext context)
            throws SubscriptionUserApiException;

    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws SubscriptionUserApiException;

    public UUID getBundleId();

    public SubscriptionState getState();

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
}
