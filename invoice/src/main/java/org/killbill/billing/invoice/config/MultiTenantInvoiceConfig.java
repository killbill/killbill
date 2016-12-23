/*
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

package org.killbill.billing.invoice.config;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.glue.InvoiceModule;
import org.killbill.billing.util.config.definition.InvoiceConfig;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.skife.config.TimeSpan;

public class MultiTenantInvoiceConfig extends MultiTenantConfigBase implements InvoiceConfig {

    private final InvoiceConfig staticConfig;

    @Inject
    public MultiTenantInvoiceConfig(@Named(InvoiceModule.STATIC_CONFIG) final InvoiceConfig staticConfig, final CacheConfig cacheConfig) {
        super(cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public int getNumberOfMonthsInFuture() {
        return staticConfig.getNumberOfMonthsInFuture();
    }

    @Override
    public int getNumberOfMonthsInFuture(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getNumberOfMonthsInFuture", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getNumberOfMonthsInFuture();
    }

    @Override
    public boolean isSanitySafetyBoundEnabled() {
        return staticConfig.isSanitySafetyBoundEnabled();
    }

    @Override
    public boolean isSanitySafetyBoundEnabled(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("isSanitySafetyBoundEnabled", tenantContext);
        if (result != null) {
            return Boolean.parseBoolean(result);
        }
        return isSanitySafetyBoundEnabled();
    }

    @Override
    public int getMaxDailyNumberOfItemsSafetyBound() {
        return staticConfig.getMaxDailyNumberOfItemsSafetyBound();
    }

    @Override
    public int getMaxDailyNumberOfItemsSafetyBound(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getMaxDailyNumberOfItemsSafetyBound", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getMaxDailyNumberOfItemsSafetyBound();
    }

    @Override
    public TimeSpan getDryRunNotificationSchedule() {
        return staticConfig.getDryRunNotificationSchedule();
    }

    @Override
    public TimeSpan getDryRunNotificationSchedule(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getDryRunNotificationSchedule", tenantContext);
        if (result != null) {
            return new TimeSpan(result);
        }
        return getDryRunNotificationSchedule();
    }

    @Override
    public int getMaxRawUsagePreviousPeriod() {
        return staticConfig.getMaxRawUsagePreviousPeriod();
    }

    @Override
    public int getMaxRawUsagePreviousPeriod(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getMaxRawUsagePreviousPeriod", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getMaxRawUsagePreviousPeriod();
    }

    @Override
    public boolean isEmailNotificationsEnabled() {
        return staticConfig.isEmailNotificationsEnabled();
    }

    @Override
    public int getMaxGlobalLockRetries() {
        return staticConfig.getMaxGlobalLockRetries();
    }

    @Override
    public boolean isInvoicingSystemEnabled() {
        return staticConfig.isInvoicingSystemEnabled();
    }

    @Override
    public boolean isInvoicingSystemEnabled(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("isInvoicingSystemEnabled", tenantContext);
        if (result != null) {
            return Boolean.parseBoolean(result);
        }
        return isInvoicingSystemEnabled();
    }

    @Override
    protected Class<? extends KillbillConfig> getConfigClass() {
        return InvoiceConfig.class;
    }
}
