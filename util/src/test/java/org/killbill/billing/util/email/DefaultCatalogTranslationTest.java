package org.killbill.billing.util.email;/*
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.killbill.billing.invoice.api.formatters.ResourceBundleFactory;
import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.killbill.billing.util.template.translation.DefaultCatalogTranslator;
import org.killbill.billing.util.template.translation.Translator;
import org.killbill.billing.util.template.translation.TranslatorConfig;
import org.killbill.xmlloader.UriAccessor;
import org.skife.config.ConfigSource;
import org.skife.config.ConfigurationObjectFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import static org.testng.Assert.assertEquals;

public class DefaultCatalogTranslationTest extends UtilTestSuiteNoDB {

    private ResourceBundle getBundle(final Locale locale) throws IOException, URISyntaxException {
        final String propertiesFileNameWithCountry = "org/killbill/billing/util/template/translation/CatalogTranslation" + "_" + locale.getLanguage() + "_" + locale.getCountry() + ".properties";
        final InputStream inputStream = UriAccessor.accessUri(propertiesFileNameWithCountry);
        if (inputStream == null) {
            return null;
        } else {
            return new PropertyResourceBundle(inputStream);
        }
    }

    @Test(groups = "fast")
    public void testBundle_us() throws IOException, URISyntaxException {
        final String shotgunMonthly = "shotgun-monthly";
        final String shotgunAnnual = "shotgun-annual";
        final String badText = "Bad text";

        final ResourceBundle bundle_en_US = getBundle(Locale.US);
        final DefaultCatalogTranslator translation = new DefaultCatalogTranslator(bundle_en_US, null);

        assertEquals(translation.getTranslation(shotgunMonthly), "Monthly shotgun plan");
        assertEquals(translation.getTranslation(shotgunAnnual), "Annual shotgun plan");
        assertEquals(translation.getTranslation(badText), badText);
    }

    @Test(groups = "fast")
    public void testBundle_ca_fr() throws IOException, URISyntaxException {
        final String shotgunMonthly = "shotgun-monthly";
        final String shotgunAnnual = "shotgun-annual";
        final String badText = "Bad text";

        final ResourceBundle bundle_ca_fr = getBundle(Locale.CANADA_FRENCH);
        final DefaultCatalogTranslator translation = new DefaultCatalogTranslator(bundle_ca_fr, null);

        assertEquals(translation.getTranslation(shotgunMonthly), "Fusil de chasse mensuel");
        assertEquals(translation.getTranslation(shotgunAnnual), "Fusil de chasse annuel");
        assertEquals(translation.getTranslation(badText), badText);
    }

    @Test(groups = "fast")
    public void testBundle_ch() throws IOException, URISyntaxException {
        final String shotgunMonthly = "shotgun-monthly";
        final String shotgunAnnual = "shotgun-annual";
        final String badText = "Bad text";

        final DefaultCatalogTranslator translation = new DefaultCatalogTranslator(null, null);

        assertEquals(translation.getTranslation(shotgunMonthly), shotgunMonthly);
        assertEquals(translation.getTranslation(shotgunAnnual), shotgunAnnual);
        assertEquals(translation.getTranslation(badText), badText);
    }

}
