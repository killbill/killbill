/*
 * Copyright 2014-2015 Groupon, Inc
 * Copyright 2014-2015 The Billing Project, LLC
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

package org.killbill.billing.server.log.obfuscators;

import java.util.List;

import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.google.common.collect.ImmutableList;

public class TestObfuscatorConverter extends ServerTestSuiteNoDB {

    private final ObfuscatorConverter converter = new ObfuscatorConverter();

    @Test(groups = "fast")
    public void testLogNonSensitiveData() throws Exception {
        verify("Starting purchase call: \n" +
               "<gateway>\n" +
               "<card>tokenized</card>\n" +
               "<bankAccountNumber></bankAccountNumber>\n" +
               "<password></password>\n" +
               "</gateway>",
               "Starting purchase call: \n" +
               "<gateway>\n" +
               "<card>tokenized</card>\n" +
               "<bankAccountNumber></bankAccountNumber>\n" +
               "<password></password>\n" +
               "</gateway>");
    }

    @Test(groups = "fast")
    public void testLogSensitiveData() throws Exception {
        verify("Starting purchase call: \n" +
               "<gateway>\n" +
               "<card>4111111111111111</card>\n" +
               "<bankAccountNumber>482391823</bankAccountNumber>\n" +
               "<password>supersecret</password>\n" +
               "</gateway>",
               "Starting purchase call: \n" +
               "<gateway>\n" +
               "<card>411111******1111</card>\n" +
               "<bankAccountNumber>*********</bankAccountNumber>\n" +
               "<password>***********</password>\n" +
               "</gateway>");
    }

    @Test(groups = "fast")
    public void testLogSensitiveDataWithExtraKeywords() throws Exception {
        verifyWithExtendedPatternObfuscator("Starting purchase call: \n" +
                                            "<gateway>\n" +
                                            "<card>4111111111111111</card>\n" +
                                            "<address1>790 test blvd</address1>\n" +
                                            "<bankAccountNumber>482391823</bankAccountNumber>\n" +
                                            "<password>supersecret</password>\n" +
                                            "</gateway>",
                                            "Starting purchase call: \n" +
                                            "<gateway>\n" +
                                            "<card>411111******1111</card>\n" +
                                            "<address1>*************</address1>\n" +
                                            "<bankAccountNumber>*********</bankAccountNumber>\n" +
                                            "<password>***********</password>\n" +
                                            "</gateway>");
    }

    private void verify(final String input, final String output) {
        final ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getFormattedMessage()).thenReturn(input);

        converter.start();
        final String obfuscated = converter.convert(event);
        Assert.assertEquals(obfuscated, output, obfuscated);
    }

    private void verifyWithExtendedPatternObfuscator(final String input, final String output) {
        final ExtendedObfuscatorConverter extendedConverter = new ExtendedObfuscatorConverter();
        final ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getFormattedMessage()).thenReturn(input);

        extendedConverter.start();
        final String obfuscated = extendedConverter.convert(event);
        Assert.assertEquals(obfuscated, output, obfuscated);
    }

    class ExtendedObfuscatorConverter extends ObfuscatorConverter {
        @Override
        public void start() {
            super.start();
        }

        @Override
        public List<String> getOptionList() {
            return ImmutableList.of("address1");
        }
    }
}
