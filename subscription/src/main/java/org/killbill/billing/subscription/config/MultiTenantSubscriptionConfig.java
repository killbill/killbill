/*
 * Copyright 2020-2022 Equinix, Inc
 * Copyright 2014-2022 The Billing Project, LLC
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

package org.killbill.billing.subscription.config;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.killbill.billing.util.config.definition.SubscriptionConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.killbill.billing.util.glue.KillBillModule;

public class MultiTenantSubscriptionConfig extends MultiTenantConfigBase implements SubscriptionConfig {

    private final SubscriptionConfig staticConfig;

    @Inject
    public MultiTenantSubscriptionConfig(@Named(KillBillModule.STATIC_CONFIG) final SubscriptionConfig staticConfig, final CacheConfig cacheConfig) {
        super(staticConfig, cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public boolean isEffectiveDateForExistingSubscriptionsAlignedToBCD() {
        return staticConfig.isEffectiveDateForExistingSubscriptionsAlignedToBCD();
    }

    @Override
    public boolean isEffectiveDateForExistingSubscriptionsAlignedToBCD(final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("isEffectiveDateForExistingSubscriptionsAlignedToBCD", tenantContext);
        if (result != null) {
            return Boolean.parseBoolean(result);
        }
        return isEffectiveDateForExistingSubscriptionsAlignedToBCD();
    }

    @Override
    protected Class<? extends KillbillConfig> getConfigClass() {
        return SubscriptionConfig.class;
    }
}
