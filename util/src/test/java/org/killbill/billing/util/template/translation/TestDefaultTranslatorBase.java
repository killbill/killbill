/*
 * Copyright 2010-2012 Ning, Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.UUID;

import org.killbill.billing.util.UtilTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.samskivert.mustache.Mustache;

public class TestDefaultTranslatorBase extends UtilTestSuiteNoDB {

    private final class TestTranslatorBase extends DefaultTranslatorBase {

        public TestTranslatorBase(final TranslatorConfig config, final ResourceBundle bundle) {
            super(bundle, bundle);
        }

    }

    @Test(groups = "fast")
    public void testResourceDoesNotExist() throws Exception {
        final TestTranslatorBase translator = new TestTranslatorBase(Mockito.mock(TranslatorConfig.class), Mockito.mock(ResourceBundle.class));
        final String originalText = UUID.randomUUID().toString();
        Assert.assertEquals(translator.getTranslation(originalText), originalText);
    }

    @Test(groups = "fast")
    public void testPropertyResourceBundle() throws Exception {
        String propStr = "invoiceDate=Date\ncustomKey=Anything";
	    InputStream propStrStream = new ByteArrayInputStream(propStr.getBytes());
        final TestTranslatorBase translator = new TestTranslatorBase(Mockito.mock(TranslatorConfig.class), new PropertyResourceBundle(propStrStream));
        Map<String, Object> ctx = new HashMap<String, Object>();
        ctx.put("translator", translator);
        final String originalText = UUID.randomUUID().toString();
        
        test("Date", "{{#translator.properties}}invoiceDate{{/translator.properties}}", ctx);
        test("Anything", "{{#translator.properties}}customKey{{/translator.properties}}", ctx);
        Assert.assertEquals(translator.getTranslation(originalText), originalText);
    }

    protected void test (String expected, String template, Object ctx) {
        test(Mustache.compiler(), expected, template, ctx);
    }

    protected void test (Mustache.Compiler compiler, String expected, String template, Object ctx) {
        check(expected, compiler.compile(template).execute(ctx));
    }

    protected void check (String expected, String output) {
        Assert.assertEquals(uncrlf(expected), uncrlf(output));
    }

    protected static String uncrlf (String text) {
        return text == null ? null : text.replace("\r", "\\r").replace("\n", "\\n");
    }
}
