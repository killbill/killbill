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

package org.killbill.billing.server.config;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.util.config.definition.KillbillConfig;
import org.killbill.billing.util.config.definition.NotificationConfig;
import org.killbill.billing.util.config.tenant.CacheConfig;
import org.killbill.billing.util.config.tenant.MultiTenantConfigBase;
import org.killbill.billing.util.glue.KillBillModule;
import org.skife.config.Param;
import org.skife.config.TimeSpan;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public class MultiTenantNotificationConfig extends MultiTenantConfigBase implements NotificationConfig {

    private final Map<String, Method> methodsCache = new HashMap<String, Method>();
    private final NotificationConfig staticConfig;

    @Inject
    public MultiTenantNotificationConfig(@Named(KillBillModule.STATIC_CONFIG) final NotificationConfig staticConfig, final CacheConfig cacheConfig) {
        super(cacheConfig);
        this.staticConfig = staticConfig;
    }

    @Override
    public List<TimeSpan> getPushNotificationsRetries() {
        return staticConfig.getPushNotificationsRetries();
    }

    @Override
    public List<TimeSpan> getPushNotificationsRetries(@Param("dummy") final InternalTenantContext tenantContext) {
        final String result = getStringTenantConfig("getPushNotificationsRetries", tenantContext);
        if (result != null) {
            return convertToListTimeSpan(result, "getPushNotificationsRetries");
        }
        return getPushNotificationsRetries();
    }

    @Override
    protected Class<? extends KillbillConfig> getConfigClass() {
        return NotificationConfig.class;
    }
}
