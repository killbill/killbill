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

import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.glue.InvoiceModule;
import org.killbill.billing.util.config.definition.InvoiceConfig;
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
    public int getNumberOfMonthsInFuture(final InternalTenantContext tenantContext) {

        final Method method = new Object(){}.getClass().getEnclosingMethod();
        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return staticConfig.getNumberOfMonthsInFuture(tenantContext);
    }

    @Override
    public TimeSpan getDryRunNotificationSchedule(final InternalTenantContext tenantContext) {
        final Method method = new Object(){}.getClass().getEnclosingMethod();
        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return new TimeSpan(result);
        }
        return staticConfig.getDryRunNotificationSchedule(tenantContext);
    }

    @Override
    public int getMaxRawUsagePreviousPeriod(final InternalTenantContext tenantContext) {
        final Method method = new Object(){}.getClass().getEnclosingMethod();
        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return staticConfig.getMaxRawUsagePreviousPeriod(tenantContext);
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
    protected Method getConfigStaticMethod(final String methodName) {
        try {
            return InvoiceConfig.class.getMethod(methodName, InternalTenantContext.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
