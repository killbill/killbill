package com.ning.billing.subscription.api;

import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.BillingActionPolicy;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Plan;
import com.ning.billing.catalog.api.PlanPhase;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.Blockable;
import com.ning.billing.entitlement.api.Entitlement.EntitlementSourceType;
import com.ning.billing.entitlement.api.Entitlement.EntitlementState;
import com.ning.billing.subscription.api.user.SubscriptionBaseApiException;
import com.ning.billing.subscription.api.user.SubscriptionBaseTransition;
import com.ning.billing.subscription.api.user.SubscriptionState;
import com.ning.billing.util.callcontext.CallContext;
import com.ning.billing.util.entity.Entity;

public interface SubscriptionBase extends Entity, Blockable {


    public boolean cancel(final DateTime requestedDate, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean cancelWithPolicy(final DateTime requestedDate, final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean uncancel(final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean changePlan(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean changePlanWithPolicy(final String productName, final BillingPeriod term, final String priceList, final DateTime requestedDate,
                                        final BillingActionPolicy policy, final CallContext context)
            throws SubscriptionBaseApiException;

    public boolean recreate(final PlanPhaseSpecifier spec, final DateTime requestedDate, final CallContext context)
            throws SubscriptionBaseApiException;

    public UUID getBundleId();

    public EntitlementState getState();

    public EntitlementSourceType getSourceType();

    public DateTime getStartDate();

    public DateTime getEndDate();

    public DateTime getFutureEndDate();

    public Plan getCurrentPlan();

    public Plan getLastActivePlan();

    public PriceList getCurrentPriceList();

    public PlanPhase getCurrentPhase();

    public Product getLastActiveProduct();

    public PriceList getLastActivePriceList();

    public ProductCategory getLastActiveCategory();

    public BillingPeriod getLastActiveBillingPeriod();

    public DateTime getChargedThroughDate();

    public DateTime getPaidThroughDate();

    public ProductCategory getCategory();

    public SubscriptionBaseTransition getPendingTransition();

    public SubscriptionBaseTransition getPreviousTransition();

    public List<SubscriptionBaseTransition> getAllTransitions();
}
