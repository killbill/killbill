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

package org.killbill.billing.tenant.api;

import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Named;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.tenant.dao.TenantModelDao;
import org.killbill.billing.tenant.glue.DefaultTenantModule;
import org.killbill.billing.util.LocaleUtils;

/**
 * This is the private API which is used to extract per tenant objects (catalog, overdue, invoice templates, ..)
 * <p/>
 * Some of these per tenant objects are cached at a higher level in their respective modules (catalog, overdue) to
 * avoid reconstructing the object state from the xml definition each time. As a result, the module also registers
 * a callback which is used for the cache invalidation when the state changes and the operation occurred on a remote node.
 * For those objects, the private api is called from the module.
 * <p/>
 * Some others (invoice templates,...) are not cached (yet) and so the logic is simpler.
 * <p/>
 * The api can only be used to retrieve objects where no caching is required.
 */
public class DefaultTenantInternalApi implements TenantInternalApi {

    private final TenantDao tenantDao;
    private final TenantCacheInvalidation tenantCacheInvalidation;

    @Inject
    public DefaultTenantInternalApi(@Named(DefaultTenantModule.NO_CACHING_TENANT) final TenantDao tenantDao,
                                    final TenantCacheInvalidation tenantCacheInvalidation) {
        this.tenantDao = tenantDao;
        this.tenantCacheInvalidation = tenantCacheInvalidation;
    }

    @Override
    public void initializeCacheInvalidationCallback(final TenantKey key, final CacheInvalidationCallback cacheInvalidationCallback) {
        tenantCacheInvalidation.registerCallback(key, cacheInvalidationCallback);
    }

    @Override
    public List<String> getTenantCatalogs(final InternalTenantContext tenantContext) {
        return tenantDao.getTenantValueForKey(TenantKey.CATALOG.toString(), tenantContext);
    }

    @Override
    public String getTenantOverdueConfig(final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(TenantKey.OVERDUE_CONFIG.toString(), tenantContext);
        return getUniqueValue(values, "overdue config", tenantContext);
    }

    @Override
    public String getTenantConfig(final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(TenantKey.PER_TENANT_CONFIG.toString(), tenantContext);
        return getUniqueValue(values, "per tenant config", tenantContext);
    }

    @Override
    public String getInvoiceTemplate(final Locale locale, final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(TenantKey.INVOICE_TEMPLATE.toString(), tenantContext);
        return getUniqueValue(values, "invoice template", tenantContext);
    }

    @Override
    public String getManualPayInvoiceTemplate(final Locale locale, final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(TenantKey.INVOICE_MP_TEMPLATE.toString(), tenantContext);
        return getUniqueValue(values, "manual pay invoice template", tenantContext);
    }

    @Override
    public String getInvoiceTranslation(final Locale locale, final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(LocaleUtils.localeString(locale, TenantKey.INVOICE_TRANSLATION_.toString()), tenantContext);
        return getUniqueValue(values, "invoice translation", tenantContext);
    }

    @Override
    public String getCatalogTranslation(final Locale locale, final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(LocaleUtils.localeString(locale, TenantKey.CATALOG_TRANSLATION_.toString()), tenantContext);
        return getUniqueValue(values, "catalog translation", tenantContext);
    }

    @Override
    public String getPluginConfig(final String pluginName, final InternalTenantContext tenantContext) {
        final String pluginConfigKey = TenantKey.PLUGIN_CONFIG_ + pluginName;
        final List<String> values = tenantDao.getTenantValueForKey(pluginConfigKey, tenantContext);
        return getUniqueValue(values, "config for plugin " + pluginConfigKey, tenantContext);
    }

    @Override
    public String getPluginPaymentStateMachineConfig(final String pluginName, final InternalTenantContext tenantContext) {
        final String pluginConfigKey = TenantKey.PLUGIN_PAYMENT_STATE_MACHINE_ + pluginName;
        final List<String> values = tenantDao.getTenantValueForKey(pluginConfigKey, tenantContext);
        return getUniqueValue(values, "payment state machine for plugin " + pluginConfigKey, tenantContext);
    }

    @Override
    public List<String> getTenantValuesForKey(final String key, final InternalTenantContext tenantContext) {
        return tenantDao.getTenantValueForKey(key, tenantContext);
    }

    @Override
    public Tenant getTenantByApiKey(final String key) throws TenantApiException {
        final TenantModelDao tenant = tenantDao.getTenantByApiKey(key);
        if (tenant == null) {
            throw new TenantApiException(ErrorCode.TENANT_DOES_NOT_EXIST_FOR_API_KEY, key);
        }
        return new DefaultTenant(tenant);
    }

    private String getUniqueValue(final List<String> values, final String msg, final InternalTenantContext tenantContext) {
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalStateException(String.format("Unexpected number of values %d for %s and tenant %d",
                                                          values.size(), msg, tenantContext.getTenantRecordId()));
        }
        return values.get(0);
    }
}
