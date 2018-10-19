/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.overdue.caching;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.ObjectType;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.overdue.api.OverdueApiException;
import org.killbill.billing.overdue.api.OverdueConfig;
import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.billing.util.cache.Cachable.CacheType;
import org.killbill.billing.util.cache.CacheController;
import org.killbill.billing.util.cache.CacheControllerDispatcher;
import org.killbill.billing.util.cache.CacheLoaderArgument;
import org.killbill.billing.util.cache.TenantOverdueConfigCacheLoader.LoaderCallback;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.xmlloader.XMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOverdueConfigCache implements OverdueConfigCache {

    private static final Logger log = LoggerFactory.getLogger(DefaultOverdueConfigCache.class);

    private final CacheController<Long, OverdueConfig> cacheController;
    private final CacheLoaderArgument cacheLoaderArgument;

    private OverdueConfig defaultOverdueConfig;

    @Inject
    public DefaultOverdueConfigCache(final CacheControllerDispatcher cacheControllerDispatcher) {
        this.cacheController = cacheControllerDispatcher.getCacheController(CacheType.TENANT_OVERDUE_CONFIG);
        this.cacheLoaderArgument = initializeCacheLoaderArgument();

        try {
            // Provided in the classpath
            final URI noOverdueConfigURI = new URI("NoOverdueConfig.xml");
            defaultOverdueConfig = XMLLoader.getObjectFromUri(noOverdueConfigURI, DefaultOverdueConfig.class);
        } catch (final Exception e) {
            defaultOverdueConfig = new DefaultOverdueConfig();
            log.error("Exception loading NoOverdueConfig - should never happen!", e);
        }
    }

    @Override
    public void loadDefaultOverdueConfig(@Nullable final String configURI) throws OverdueApiException {
        boolean missingOrCorruptedDefaultConfig;
        try {
            if (configURI == null || configURI.isEmpty()) {
                missingOrCorruptedDefaultConfig = true;
            } else {
                final URI u = new URI(configURI);
                defaultOverdueConfig = XMLLoader.getObjectFromUri(u, DefaultOverdueConfig.class);
                missingOrCorruptedDefaultConfig = (defaultOverdueConfig == null);
            }
        } catch (final Exception e) {
            missingOrCorruptedDefaultConfig = true;
            log.warn("Exception loading default overdue config from " + configURI, e);
        }
        if (missingOrCorruptedDefaultConfig) {
            log.warn("Overdue system disabled: unable to load the overdue config from " + configURI);
        }
    }

    @Override
    public void loadDefaultOverdueConfig(final OverdueConfig config) throws OverdueApiException {
        defaultOverdueConfig = config;
    }

    @Override
    public OverdueConfig getOverdueConfig(final InternalTenantContext tenantContext) throws OverdueApiException {
        if (InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID.equals(tenantContext.getTenantRecordId())) {
            return defaultOverdueConfig;
        }
        // The cache loader might choke on some bad xml -- unlikely since we check its validity prior storing it,
        // but to be on the safe side;;
        try {
            final OverdueConfig overdueConfig = cacheController.get(tenantContext.getTenantRecordId(), cacheLoaderArgument);
            return (overdueConfig != null) ? overdueConfig : defaultOverdueConfig;
        } catch (final IllegalStateException e) {
            throw new OverdueApiException(ErrorCode.OVERDUE_INVALID_FOR_TENANT, tenantContext.getTenantRecordId());
        }
    }

    @Override
    public void clearOverdueConfig(final InternalTenantContext tenantContext) {
        if (!InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID.equals(tenantContext.getTenantRecordId())) {
            cacheController.remove(tenantContext.getTenantRecordId());
        }
    }

    private CacheLoaderArgument initializeCacheLoaderArgument() {
        final LoaderCallback loaderCallback = new LoaderCallback() {
            @Override
            public OverdueConfig loadOverdueConfig(final String overdueConfigXML) throws OverdueApiException {
                final InputStream overdueConfigStream = new ByteArrayInputStream(overdueConfigXML.getBytes());
                try {
                    return XMLLoader.getObjectFromStream(overdueConfigStream, DefaultOverdueConfig.class);
                } catch (final Exception e) {
                    throw new OverdueApiException(ErrorCode.OVERDUE_INVALID_FOR_TENANT, "Problem encountered loading overdue config ", e);
                }
            }
        };
        final Object[] args = new Object[1];
        args[0] = loaderCallback;
        final ObjectType irrelevant = null;
        final InternalTenantContext notUsed = null;
        return new CacheLoaderArgument(irrelevant, args, notUsed);
    }

}
