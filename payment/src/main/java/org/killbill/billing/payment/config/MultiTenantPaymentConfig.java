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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.killbill.billing.util.config.definition.PaymentConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.Param;
import org.skife.config.TimeSpan;

public class MultiTenantPaymentConfig extends MultiTenantConfigBase implements PaymentConfig {

    private final PaymentConfig staticConfig;

    @Inject
    public MultiTenantPaymentConfig(@Named(KillBillModule.STATIC_CONFIG) final PaymentConfig staticConfig, final CacheConfig cacheConfig) {
        super(cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public List<Integer> getPaymentFailureRetryDays() {
        return staticConfig.getPaymentFailureRetryDays();
    }

    @Override
    public List<Integer> getPaymentFailureRetryDays(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPaymentFailureRetryDays", tenantContext);
        if (result != null) {
            return convertToListInteger(result, "getPaymentFailureRetryDays");
        }
        return getPaymentFailureRetryDays();
    }

    @Override
    public int getPluginFailureInitialRetryInSec() {
        return staticConfig.getPluginFailureInitialRetryInSec();
    }

    @Override
    public int getPluginFailureInitialRetryInSec(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPluginFailureInitialRetryInSec", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getPluginFailureInitialRetryInSec();
    }

    @Override
    public int getPluginFailureRetryMultiplier() {
        return staticConfig.getPluginFailureRetryMultiplier();
    }

    @Override
    public int getPluginFailureRetryMultiplier(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPluginFailureRetryMultiplier", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getPluginFailureRetryMultiplier();
    }

    @Override
    public List<TimeSpan> getUnknownTransactionsRetries() {
        return staticConfig.getUnknownTransactionsRetries();
    }

    @Override
    public List<TimeSpan> getUnknownTransactionsRetries(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getUnknownTransactionsRetries", tenantContext);
        if (result != null) {
            return convertToListTimeSpan(result, "getUnknownTransactionsRetries");
        }
        return getUnknownTransactionsRetries();
    }

    @Override
    public List<TimeSpan> getPendingTransactionsRetries() {
        return staticConfig.getPendingTransactionsRetries();
    }

    @Override
    public List<TimeSpan> getPendingTransactionsRetries(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPendingTransactionsRetries", tenantContext);
        if (result != null) {
            return convertToListTimeSpan(result, "getPendingTransactionsRetries");
        }
        return getPendingTransactionsRetries();
    }

    @Override
    public int getPluginFailureRetryMaxAttempts() {
        return staticConfig.getPluginFailureRetryMaxAttempts();
    }

    @Override
    public int getPluginFailureRetryMaxAttempts(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPluginFailureRetryMaxAttempts", tenantContext);
        if (result != null) {
            return Integer.parseInt(result);
        }
        return getPluginFailureRetryMaxAttempts();
    }

    @Override
    public List<String> getPaymentControlPluginNames() {
        return staticConfig.getPaymentControlPluginNames();
    }

    @Override
    public List<String> getPaymentControlPluginNames(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPaymentControlPluginNames", tenantContext);
        if (result != null) {
            return convertToListString(result, "getPaymentControlPluginNames");
        }
        return getPaymentControlPluginNames();
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
    public boolean isAllowedToOverwritePaymentMethodId() {
        return staticConfig.isAllowedToOverwritePaymentMethodId();
    }

    @Override
    protected Class<? extends KillbillConfig> getConfigClass() {
        return PaymentConfig.class;
    }
}
