/*
 * Copyright 2024 PayTheFly
 * Copyright 2024 The Billing Project, LLC
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

package org.killbill.billing.plugin.paythefly;

import java.util.Map;

import javax.annotation.Nullable;

import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.tenant.api.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health check for the PayTheFly plugin.
 *
 * <p>Reports healthy if the plugin is properly configured (non-empty projectId, etc.).</p>
 */
public class PayTheFlyHealthcheck implements Healthcheck {

    private static final Logger logger = LoggerFactory.getLogger(PayTheFlyHealthcheck.class);

    private final PayTheFlyConfigPropertiesConfigurationHandler configHandler;

    public PayTheFlyHealthcheck(final PayTheFlyConfigPropertiesConfigurationHandler configHandler) {
        this.configHandler = configHandler;
    }

    @Override
    public HealthStatus getHealthStatus(@Nullable final Tenant tenant, @Nullable final Map properties) {
        if (tenant == null) {
            return HealthStatus.unHealthy("No tenant provided");
        }
        final PayTheFlyConfigProperties config = configHandler.getConfigurable(tenant.getId());
        if (config == null || !config.isConfigured()) {
            return HealthStatus.unHealthy("PayTheFly plugin not configured for tenant " + tenant.getId());
        }
        return HealthStatus.healthy("PayTheFly plugin is configured and ready");
    }
}
