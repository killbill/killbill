package org.killbill.billing.usage.api.user;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.usage.api.RolledUpUsage;
import org.killbill.billing.usage.api.UsageUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.TenantContext;


public class MockUsageUserApi implements UsageUserApi {

    private List<RolledUpUsage> result;

    @Override
    public void recordRolledUpUsage(final UUID uuid, final String s, final DateTime dateTime, final DateTime dateTime2, final BigDecimal bigDecimal, final CallContext callContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RolledUpUsage getUsageForSubscription(final UUID uuid, final String s, final DateTime dateTime, final DateTime dateTime2, final TenantContext tenantContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<RolledUpUsage> getAllUsageForSubscription(final UUID uuid, final Set<String> strings, final List<DateTime> dateTimes, final TenantContext tenantContext) {
        return result;
    }

    public void setAllUsageForSubscription(final List<RolledUpUsage> result) {
        this.result = result;
    }
}
