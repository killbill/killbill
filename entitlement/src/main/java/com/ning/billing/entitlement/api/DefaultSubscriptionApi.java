package com.ning.billing.entitlement.api;

import java.util.List;
import java.util.UUID;

import com.ning.billing.util.callcontext.TenantContext;

public class DefaultSubscriptionApi implements SubscriptionApi  {

    @Override
    public Subscription getSubscriptionForEntitlementId(final UUID entitlementId, final TenantContext context) throws SubscriptionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SubscriptionBundle getSubscriptionBundle(final UUID bundleId, final TenantContext context) throws SubscriptionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public SubscriptionBundle getSubscriptionBundleForAccountIdAndExternalKey(final UUID accountId, final String externalKey, final TenantContext context) throws SubscriptionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public List<SubscriptionBundle> getSubscriptionBundlesForAccountId(final UUID accountId, final TenantContext context) throws SubscriptionApiException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
