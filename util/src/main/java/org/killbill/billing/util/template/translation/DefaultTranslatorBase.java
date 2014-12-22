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

package org.killbill.billing.util.template.translation;

import java.util.ResourceBundle;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DefaultTranslatorBase implements Translator {

    protected final Logger log = LoggerFactory.getLogger(DefaultTranslatorBase.class);

    private final ResourceBundle bundle;
    private final ResourceBundle defaultBundle;

    public DefaultTranslatorBase(@Nullable final ResourceBundle bundle,
                                 @Nullable final ResourceBundle defaultBundle) {
        this.bundle = bundle;
        this.defaultBundle = defaultBundle;
    }

    @Override
    public String getTranslation(final String originalText) {
        if (originalText == null) {
            return null;
        }
        if ((bundle != null) && (bundle.containsKey(originalText))) {
            return bundle.getString(originalText);
        } else {
            if ((defaultBundle != null) && (defaultBundle.containsKey(originalText))) {
                return defaultBundle.getString(originalText);
            } else {
                return originalText;
            }
        }
    }
}
