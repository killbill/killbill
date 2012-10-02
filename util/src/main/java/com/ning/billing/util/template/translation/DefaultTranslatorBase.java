/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.util.template.translation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.ErrorCode;
import com.ning.billing.util.LocaleUtils;
import com.ning.billing.util.config.UriAccessor;

import com.google.inject.Inject;

public abstract class DefaultTranslatorBase implements Translator {
    protected final TranslatorConfig config;
    protected final Logger log = LoggerFactory.getLogger(DefaultTranslatorBase.class);

    @Inject
    public DefaultTranslatorBase(final TranslatorConfig config) {
        this.config = config;
    }

    protected abstract String getBundlePath();

    /*
     * string used for exception handling
     */
    protected abstract String getTranslationType();

    @Override
    public String getTranslation(final Locale locale, final String originalText) {
        final String bundlePath = getBundlePath();
        ResourceBundle bundle = getBundle(locale, bundlePath);

        if ((bundle != null) && (bundle.containsKey(originalText))) {
            return bundle.getString(originalText);
        } else {
            if (config.getDefaultLocale() == null) {
                log.warn(String.format(ErrorCode.MISSING_DEFAULT_TRANSLATION_RESOURCE.toString(), getTranslationType()));
                return originalText;
            }

            final Locale defaultLocale = LocaleUtils.toLocale(config.getDefaultLocale());
            try {
                bundle = getBundle(defaultLocale, bundlePath);

                if ((bundle != null) && (bundle.containsKey(originalText))) {
                    return bundle.getString(originalText);
                } else {
                    return originalText;
                }
            } catch (MissingResourceException mrex) {
                log.warn(String.format(ErrorCode.MISSING_TRANSLATION_RESOURCE.toString(), getTranslationType()));
                return originalText;
            }
        }
    }

    private ResourceBundle getBundle(final Locale locale, final String bundlePath) {
        try {
            // Try to load the bundle from the classpath first
            return ResourceBundle.getBundle(bundlePath, locale);
        } catch (MissingResourceException ignored) {
        }

        // Try to load it from a properties file
        final String propertiesFileNameWithCountry = bundlePath + "_" + locale.getLanguage() + "_" + locale.getCountry() + ".properties";
        ResourceBundle bundle = getBundleFromPropertiesFile(propertiesFileNameWithCountry);
        if (bundle != null) {
            return bundle;
        } else {
            final String propertiesFileName = bundlePath + "_" + locale.getLanguage() + ".properties";
            bundle = getBundleFromPropertiesFile(propertiesFileName);
        }

        if (bundle == null) {
            log.warn(String.format(ErrorCode.MISSING_TRANSLATION_RESOURCE.toString(), getTranslationType()));
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
