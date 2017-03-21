/*
 * Copyright 2016-2017 Groupon, Inc
 * Copyright 2016-2017 The Billing Project, LLC
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

package org.killbill.billing.util.cache;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TenantStateMachineConfigCacheLoader extends BaseCacheLoader<String, Object> {

    private static final Pattern PATTERN = Pattern.compile(TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_.toString() + "(.*)");
    private static final Logger log = LoggerFactory.getLogger(TenantStateMachineConfigCacheLoader.class);

    private final TenantInternalApi tenantApi;

    @Inject
    public TenantStateMachineConfigCacheLoader(final TenantInternalApi tenantApi) {
        super();
        this.tenantApi = tenantApi;
    }

    @Override
    public CacheType getCacheType() {
        return CacheType.TENANT_PAYMENT_STATE_MACHINE_CONFIG;
    }

    @Override
    public Object compute(final String key, final CacheLoaderArgument cacheLoaderArgument) {
        final String[] parts = key.split(CacheControllerDispatcher.CACHE_KEY_SEPARATOR);
        final String rawKey = parts[0];
        final Matcher matcher = PATTERN.matcher(rawKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unexpected key " + rawKey);
        }
        final String pluginName = matcher.group(1);
        final String tenantRecordId = parts[1];

        final LoaderCallback callback = (LoaderCallback) cacheLoaderArgument.getArgs()[0];

        final InternalTenantContext internalTenantContext = new InternalTenantContext(Long.valueOf(tenantRecordId));
        final String stateMachineConfigXML = tenantApi.getPluginPaymentStateMachineConfig(pluginName, internalTenantContext);
        if (stateMachineConfigXML == null) {
            return null;
        }

        try {
            log.info("Loading config state machine cache for pluginName='{}', tenantRecordId='{}'", pluginName, internalTenantContext.getTenantRecordId());
            return callback.loadStateMachineConfig(stateMachineConfigXML);
        } catch (final PaymentApiException e) {
            throw new IllegalStateException(String.format("Failed to de-serialize state machine config for tenantRecordId='%s'", internalTenantContext.getTenantRecordId()), e);
        }
    }

    public interface LoaderCallback {

        public Object loadStateMachineConfig(final String stateMachineConfigXML) throws PaymentApiException;
    }
}
