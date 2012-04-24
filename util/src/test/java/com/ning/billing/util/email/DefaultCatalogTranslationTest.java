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

import com.ning.billing.util.email.translation.DefaultCatalogTranslator;
import com.ning.billing.util.email.translation.Translator;
import com.ning.billing.util.email.translation.TranslatorConfig;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Locale;

import static org.testng.Assert.assertEquals;

@Test(groups = {"fast", "email"})
public class DefaultCatalogTranslationTest {
    private Translator translation;

    @BeforeClass(groups={"fast", "email"})
    public void setup() {
        final TranslatorConfig config = new ConfigurationObjectFactory(System.getProperties()).build(TranslatorConfig.class);
        translation = new DefaultCatalogTranslator(config);
    }

    @Test(groups = {"fast", "email"})
    public void testInitialization() {
        String ningPlusText = "ning-plus";
        String ningProText = "ning-pro";
        String badText = "Bad text";

        assertEquals(translation.getTranslation(Locale.US, ningPlusText), "Plus");
        assertEquals(translation.getTranslation(Locale.US, ningProText), "Pro");
        assertEquals(translation.getTranslation(Locale.US, badText), badText);

        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, ningPlusText), "Plus en francais");
        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, ningProText), "Pro");
        assertEquals(translation.getTranslation(Locale.CANADA_FRENCH, badText), badText);

        assertEquals(translation.getTranslation(Locale.CHINA, ningPlusText), "Plus");
        assertEquals(translation.getTranslation(Locale.CHINA, ningProText), "Pro");
        assertEquals(translation.getTranslation(Locale.CHINA, badText), badText);
    }

    @Test
    public void testExistingTranslation() {
        // if the translation exists, return the translation
        String originalText = "ning-plus";
        assertEquals(translation.getTranslation(Locale.US,  originalText), "Plus");
    }

    @Test
    public void testMissingTranslation() {
        // if the translation is missing from the file, return the original text
        String originalText = "missing translation";
        assertEquals(translation.getTranslation(Locale.US, originalText), originalText);
    }

    @Test
    public void testMissingTranslationFileWithEnglishText() {
        // if the translation file doesn't exist, return the "English" translation
        String originalText = "ning-plus";
        assertEquals(translation.getTranslation(Locale.CHINA, originalText), "Plus");
    }

    @Test
    public void testMissingFileAndText() {
        // if the file is missing, and the "English" translation is missing, return the original text
        String originalText = "missing translation";
        assertEquals(translation.getTranslation(Locale.CHINA, originalText), originalText);
    }
}
