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

package org.killbill.billing.payment.config;

import java.lang.reflect.Method;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.glue.PaymentModule;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.skife.config.Param;
import org.skife.config.TimeSpan;

public class MultiTenantPaymentConfig extends MultiTenantConfigBase implements PaymentConfig {

    private final PaymentConfig staticConfig;

    @Inject
    public MultiTenantPaymentConfig(@Named(PaymentModule.STATIC_CONFIG) final PaymentConfig staticConfig, final CacheConfig cacheConfig) {
        super(cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public List<Integer> getPaymentFailureRetryDays(@Param("dummy") final InternalTenantContext tenantContext) {
        // There is no good way to achieve that in java; this solution is expensive (we could consider hardcoding the method name each time instead)
        final Method method = new Object() {}.getClass().getEnclosingMethod();
        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return convertToListInteger(result, method.getName());
        }
        return staticConfig.getPaymentFailureRetryDays(tenantContext);
    }

    @Override
    public int getPluginFailureInitialRetryInSec(@Param("dummy") final InternalTenantContext tenantContext) {
        final Method method = new Object() {}.getClass().getEnclosingMethod();
        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return staticConfig.getPluginFailureInitialRetryInSec(tenantContext);
    }

    @Override
    public int getPluginFailureRetryMultiplier(@Param("dummy") final InternalTenantContext tenantContext) {
        final Method method = new Object() {}.getClass().getEnclosingMethod();

        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return staticConfig.getPluginFailureRetryMultiplier(tenantContext);
    }

    @Override
    public List<TimeSpan> getIncompleteTransactionsRetries(@Param("dummy") final InternalTenantContext tenantContext) {
        final Method method = new Object() {}.getClass().getEnclosingMethod();

        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return convertToListTimeSpan(result, method.getName());
        }
        return staticConfig.getIncompleteTransactionsRetries(tenantContext);
    }

    @Override
    public int getPluginFailureRetryMaxAttempts(@Param("dummy") final InternalTenantContext tenantContext) {

        final Method method = new Object() {}.getClass().getEnclosingMethod();

        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return staticConfig.getPluginFailureRetryMaxAttempts(tenantContext);
    }

    @Override
    public List<String> getPaymentControlPluginNames(@Param("dummy") final InternalTenantContext tenantContext) {

        final Method method = new Object() {}.getClass().getEnclosingMethod();

        final String result = getStringTenantConfig(method.getName(), tenantContext);
        if (result != null) {
            return convertToListString(result, method.getName());
        }
        return staticConfig.getPaymentControlPluginNames(tenantContext);
    }

    @Override
    public TimeSpan getJanitorRunningRate() {
        return staticConfig.getJanitorRunningRate();
    }

    @Override
    public TimeSpan getIncompleteAttemptsTimeSpanDelay() {
        return staticConfig.getIncompleteAttemptsTimeSpanDelay();
    }

    @Override
    public String getDefaultPaymentProvider() {
        return staticConfig.getDefaultPaymentProvider();
    }

    @Override
    public TimeSpan getPaymentPluginTimeout() {
        return staticConfig.getPaymentPluginTimeout();
    }

    @Override
    public int getPaymentPluginThreadNb() {
        return staticConfig.getPaymentPluginThreadNb();
    }

    @Override
    public int getMaxGlobalLockRetries() {
        return staticConfig.getMaxGlobalLockRetries();
    }

    @Override
    protected Method getConfigStaticMethod(final String methodName) {
        try {
            return PaymentConfig.class.getMethod(methodName, InternalTenantContext.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

}
