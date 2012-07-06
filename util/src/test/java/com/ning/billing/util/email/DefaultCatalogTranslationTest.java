package com.ning.billing.util.email;/*
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

import java.util.Locale;
import java.util.Map;

import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.ning.billing.util.template.translation.DefaultCatalogTranslator;
import com.ning.billing.util.template.translation.Translator;
import com.ning.billing.util.template.translation.TranslatorConfig;

import static org.testng.Assert.assertEquals;

public class DefaultCatalogTranslationTest {
    private Translator translation;

    @BeforeClass(groups = {"fast", "email"})
    public void setup() {
        final ConfigSource configSource = new ConfigSource() {
            private final Map<String, String> properties = ImmutableMap.<String, String>of("killbill.template.invoiceFormatterFactoryClass",
                                                                                           "com.ning.billing.mock.MockInvoiceFormatterFactory");

            @Override
            public String getString(final String propertyName) {
                return properties.get(propertyName);
            }
        };

        final TranslatorConfig config = new ConfigurationObjectFactory(configSource).build(TranslatorConfig.class);
        translation = new DefaultCatalogTranslator(config);
    }

    @Test(groups = "fast")
    public void testInitialization() {
        final String shotgunMonthly = "shotgun-monthly";
        final String shotgunAnnual = "shotgun-annual";
        final String badText = "Bad text";

        assertEquals(translation.getTranslation(Locale.US, shotgunMonthly), "Monthly shotgun plan");
        assertEquals(translation.getTranslation(Locale.US, shotgunAnnual), "Annual shotgun plan");
        assertEquals(translation.getTranslation(Locale.US, badText), badText);

        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, shotgunMonthly), "Fusil de chasse mensuel");
        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, shotgunAnnual), "Fusil de chasse annuel");
        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, badText), badText);

        assertEquals(translation.getTranslation(Locale.CHINA, shotgunMonthly), "Monthly shotgun plan");
        assertEquals(translation.getTranslation(Locale.CHINA, shotgunAnnual), "Annual shotgun plan");
        assertEquals(translation.getTranslation(Locale.CHINA, badText), badText);
    }

    @Test(groups = "fast")
    public void testExistingTranslation() {
        // If the translation exists, return the translation
        final String originalText = "shotgun-monthly";
        assertEquals(translation.getTranslation(Locale.US, originalText), "Monthly shotgun plan");
    }

    @Test(groups = "fast")
    public void testMissingTranslation() {
        // If the translation is missing from the file, return the original text
        final String originalText = "missing translation";
        assertEquals(translation.getTranslation(Locale.US, originalText), originalText);
    }

    @Test(groups = "fast")
    public void testMissingTranslationFileWithEnglishText() {
        // If the translation file doesn't exist, return the "English" translation
        final String originalText = "shotgun-monthly";
        assertEquals(translation.getTranslation(Locale.CHINA, originalText), "Monthly shotgun plan");
    }

    @Test(groups = "fast")
    public void testMissingFileAndText() {
        // If the file is missing, and the "English" translation is missing, return the original text
        final String originalText = "missing translation";
        assertEquals(translation.getTranslation(Locale.CHINA, originalText), originalText);
    }
}
