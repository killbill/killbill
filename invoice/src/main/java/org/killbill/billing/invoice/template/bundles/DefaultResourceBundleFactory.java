/*
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

package org.killbill.billing.invoice.template.bundles;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.tenant.api.TenantInternalApi;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.xmlloader.UriAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

public class DefaultResourceBundleFactory implements ResourceBundleFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceBundleFactory.class);

    private final TenantInternalApi tenantApi;

    @Inject
    public DefaultResourceBundleFactory(final TenantInternalApi tenantApi) {
        this.tenantApi = tenantApi;
    }

    @Override
    public ResourceBundle createBundle(final Locale locale, final String bundlePath, final ResourceBundleType type, final InternalTenantContext tenantContext) {
        if (InternalCallContextFactory.INTERNAL_TENANT_RECORD_ID.equals(tenantContext.getTenantRecordId())) {
            return getGlobalBundle(locale, bundlePath);
        }
        final String bundle = getTenantBundleForType(locale, type, tenantContext);
        if (bundle != null) {
            try {
                return new PropertyResourceBundle(new ByteArrayInputStream(bundle.getBytes(Charsets.UTF_8)));
            } catch (IOException e) {
                logger.warn("Failed to de-serialize the property bundle for tenant {} and locale {}", tenantContext.getTenantRecordId(), locale);
                // Fall through...
            }
        }
        return getGlobalBundle(locale, bundlePath);
    }

    private String getTenantBundleForType(final Locale locale, final ResourceBundleType type, final InternalTenantContext tenantContext) {
        switch (type) {
            case CATALOG_TRANSLATION:
                return tenantApi.getCatalogTranslation(locale, tenantContext);

            case INVOICE_TRANSLATION:
                return tenantApi.getInvoiceTranslation(locale, tenantContext);

            default:
                logger.warn("Unexpected bundle type {} ", type);
                return null;
        }
    }

    private ResourceBundle getGlobalBundle(final Locale locale, final String bundlePath) {
        try {
            // Try to loadDefaultCatalog the bundle from the classpath first
            return ResourceBundle.getBundle(bundlePath, locale);
        } catch (MissingResourceException ignored) {
        }
        // Try to loadDefaultCatalog it from a properties file
        final String propertiesFileNameWithCountry = bundlePath + "_" + locale.getLanguage() + "_" + locale.getCountry() + ".properties";
        ResourceBundle bundle = getBundleFromPropertiesFile(propertiesFileNameWithCountry);
        if (bundle != null) {
            return bundle;
        } else {
            final String propertiesFileName = bundlePath + "_" + locale.getLanguage() + ".properties";
            bundle = getBundleFromPropertiesFile(propertiesFileName);
        }

        return bundle;
    }

    private ResourceBundle getBundleFromPropertiesFile(final String propertiesFileName) {
        try {
            final InputStream inputStream = UriAccessor.accessUri(propertiesFileName);
            if (inputStream == null) {
                return null;
            } else {
                return new PropertyResourceBundle(inputStream);
            }
        } catch (IllegalArgumentException iae) {
            return null;
        } catch (MissingResourceException mrex) {
            return null;
        } catch (URISyntaxException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
