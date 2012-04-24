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

package com.ning.billing.util.email.translation;

import com.google.inject.Inject;
import com.ning.billing.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public abstract class DefaultTranslatorBase implements Translator {
    protected final TranslatorConfig config;
    protected final Logger log = LoggerFactory.getLogger(DefaultTranslatorBase.class);

    @Inject
    public DefaultTranslatorBase(TranslatorConfig config) {
        this.config = config;
    }

    protected abstract String getBundlePath();

    /*
     * string used for exception handling
     */
    protected abstract String getTranslationType();

    @Override
    public String getTranslation(Locale locale, String originalText) {
        ResourceBundle bundle = null;
        try {
            bundle = ResourceBundle.getBundle(getBundlePath(), locale);
        } catch (MissingResourceException mrex) {
            log.warn(String.format(ErrorCode.MISSING_TRANSLATION_RESOURCE.toString(), getTranslationType()));
        }

        if ((bundle != null) && (bundle.containsKey(originalText))) {
            return bundle.getString(originalText);
        } else {
            try {
                Locale defaultLocale = new Locale(config.getDefaultLocale());
                bundle = ResourceBundle.getBundle(getBundlePath(), defaultLocale);

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
}
