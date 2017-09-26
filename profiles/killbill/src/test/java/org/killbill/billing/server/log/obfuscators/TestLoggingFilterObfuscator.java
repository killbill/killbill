/*
 * Copyright 2014-2017 Groupon, Inc
 * Copyright 2014-2017 The Billing Project, LLC
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

import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class TestLoggingFilterObfuscator extends ServerTestSuiteNoDB {

    private final LoggingFilterObfuscator obfuscator = new LoggingFilterObfuscator();

    @Test(groups = "fast")
    public void testAuthorization() throws Exception {
        verify("2017-08-26T10:28:21,959+0000 lvl='INFO', log='LoggingFilter', th='qtp1071550332-34', xff='', rId='70394abe-7ab6-4b7c-aaf5-17abfcdb9622', aRId='', tRId='', 1 * Server in-bound request\n" +
               "1 > GET http://127.0.0.1:8080/1.0/kb/security/permissions\n" +
               "1 > User-Agent: killbill/1.9.0; jruby 9.1.12.0 (2.3.3) 2017-06-15 33c6439 Java HotSpot(TM) 64-Bit Server VM 25.121-b13 on 1.8.0_121-b13 +jit [darwin-x86_64]\n" +
               "1 > Authorization: Basic YWRtaW46cGFzc3dvcmQ=\n" +
               "1 > Host: 127.0.0.1:8080\n" +
               "1 > Accept-Encoding: gzip;q=1.0,deflate;q=0.6,identity;q=0.3\n" +
               "1 > Accept: application/json\n" +
               "1 >",
               "2017-08-26T10:28:21,959+0000 lvl='INFO', log='LoggingFilter', th='qtp1071550332-34', xff='', rId='70394abe-7ab6-4b7c-aaf5-17abfcdb9622', aRId='', tRId='', 1 * Server in-bound request\n" +
               "1 > GET http://127.0.0.1:8080/1.0/kb/security/permissions\n" +
               "1 > User-Agent: killbill/1.9.0; jruby 9.1.12.0 (2.3.3) 2017-06-15 33c6439 Java HotSpot(TM) 64-Bit Server VM 25.121-b13 on 1.8.0_121-b13 +jit [darwin-x86_64]\n" +
               "1 > Authorization: **************************\n" +
               "1 > Host: 127.0.0.1:8080\n" +
               "1 > Accept-Encoding: gzip;q=1.0,deflate;q=0.6,identity;q=0.3\n" +
               "1 > Accept: application/json\n" +
               "1 >");
    }

    @Test(groups = "fast")
    public void testApiSecret() throws Exception {
        verify("2017-08-25T15:28:34,331+0000 lvl='INFO', log='LoggingFilter', th='qtp288887829-1845', xff='', rId='59c40009-ea68-4d87-9580-fe95e9a82c23', aRId='', tRId='11', 3896 * Server in-bound request\n" +
               "3896 > GET http://127.0.0.1:8080/1.0/kb/paymentMethods/069a4daa-e752-486c-8e40-c9c4f9a732c4?withPluginInfo=true\n" +
               "3896 > Cookie: JSESSIONID=64faafa1-da74-4ac7-afc7-947cc9871fe5\n" +
               "3896 > X-Killbill-Apikey: bob\n" +
               "3896 > Accept: application/json\n" +
               "3896 > X-Request-Id: 59c40009-ea68-4d87-9580-fe95e9a82c23\n" +
               "3896 > X-Killbill-Apisecret: lazar\n" +
               "3896 > User-Agent: killbill/1.9.0; ruby 2.3.1p112 (2016-04-26 revision 54768) [x86_64-darwin16]\n" +
               "3896 > Host: 127.0.0.1:8080\n" +
               "3896 > Accept-Encoding: gzip;q=1.0,deflate;q=0.6,identity;q=0.3\n" +
               "3896 >",
               "2017-08-25T15:28:34,331+0000 lvl='INFO', log='LoggingFilter', th='qtp288887829-1845', xff='', rId='59c40009-ea68-4d87-9580-fe95e9a82c23', aRId='', tRId='11', 3896 * Server in-bound request\n" +
               "3896 > GET http://127.0.0.1:8080/1.0/kb/paymentMethods/069a4daa-e752-486c-8e40-c9c4f9a732c4?withPluginInfo=true\n" +
               "3896 > Cookie: JSESSIONID=64faafa1-da74-4ac7-afc7-947cc9871fe5\n" +
               "3896 > X-Killbill-Apikey: bob\n" +
               "3896 > Accept: application/json\n" +
               "3896 > X-Request-Id: 59c40009-ea68-4d87-9580-fe95e9a82c23\n" +
               "3896 > X-Killbill-Apisecret: *****\n" +
               "3896 > User-Agent: killbill/1.9.0; ruby 2.3.1p112 (2016-04-26 revision 54768) [x86_64-darwin16]\n" +
               "3896 > Host: 127.0.0.1:8080\n" +
               "3896 > Accept-Encoding: gzip;q=1.0,deflate;q=0.6,identity;q=0.3\n" +
               "3896 >");
    }

    private void verify(final String input, final String output) {
        final String obfuscated = obfuscator.obfuscate(input, Mockito.mock(ILoggingEvent.class));
        Assert.assertEquals(obfuscated, output, obfuscated);
    }
}
