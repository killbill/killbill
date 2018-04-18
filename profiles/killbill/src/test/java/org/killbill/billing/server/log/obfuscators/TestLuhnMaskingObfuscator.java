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

import org.killbill.billing.server.log.ServerTestSuiteNoDB;
import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

public class TestLuhnMaskingObfuscator extends ServerTestSuiteNoDB {

    private final LuhnMaskingObfuscator obfuscator = new LuhnMaskingObfuscator();

    @Test(groups = "fast")
    public void testStripSeparatorsSpaces() {
        Assert.assertEquals(obfuscator.stripSeparators("5137 0049 8639 6403"), "5137004986396403");
    }

    @Test(groups = "fast")
    public void testStripSeparatorsDashes() {
        Assert.assertEquals(obfuscator.stripSeparators("5137-0049-8639-6403"), "5137004986396403");
    }

    @Test(groups = "fast")
    public void testLuhnGood() {
        Assert.assertTrue(obfuscator.luhnCheck("5137004986396403"));
    }

    @Test(groups = "fast")
    public void testLuhnBad() {
        Assert.assertFalse(obfuscator.luhnCheck("5137004986396404"));
    }

    @Test(groups = "fast")
    public void testConvert() {
        verify("try 5137 0049 8639 6404 and 5137 0049 8639 6403", "try 5137 0049 8639 6404 and 513700********6403");
    }

    @Test(groups = "fast")
    public void testConvertCcNumberAtStartNonCcNumberAtEnd() {
        verify("5137 0049 8639 6403 and 5137 0049 8639 6404", "513700********6403 and 5137 0049 8639 6404");
    }

    @Test(groups = "fast")
    public void testConvertMultiple() {
        verify("try 5137 0049 8639 6403 multiple 5137 0049 8639 6404 possible 4111-1111-1111 1111 card 4111111111111112 numbers", "try 513700********6403 multiple 5137 0049 8639 6404 possible 411111********1111 card 4111111111111112 numbers");
    }

    @Test(groups = "fast")
    public void testLotsOfCcNumbers() {
        verify("American Express"
               + "378282246310005"
               + "American Express"
               + "371449635398431"
               + "American Express Corporate"
               + "378734493671000"
               + "Australian BankCard"
               + "5610591081018250"
               + "Diners Club"
               + "30569309025904"
               + "Diners Club"
               + "38520000023237"
               + "Discover"
               + "6011111111111117"
               + "Discover"
               + "6011000990139424"
               + "JCB"
               + "3530111333300000"
               + "JCB"
               + "3566002020360505"
               + "MasterCard"
               + "5555555555554444"
               + "MasterCard"
               + "5105105105105100"
               + "Visa"
               + "4111111111111111"
               + "Visa"
               + "4012888888881881"
               + "Visa"
               + "4222222222222"
               + "Note : Even though this number has a different character count than the other test numbers, it is the correct and functional number."
               + "Processor-specific Cards"
               + "Dankort (PBS)"
               + "5019717010103742"
               + "Switch/Solo (Paymentech)"
               + "6331101999990016",
               "American Express"
               + "378282*****0005"
               + "American Express"
               + "371449*****8431"
               + "American Express Corporate"
               + "378734*****1000"
               + "Australian BankCard"
               + "561059******8250"
               + "Diners Club"
               + "305693****5904"
               + "Diners Club"
               + "385200****3237"
               + "Discover"
               + "601111******1117"
               + "Discover"
               + "601100******9424"
               + "JCB"
               + "353011******0000"
               + "JCB"
               + "356600******0505"
               + "MasterCard"
               + "555555******4444"
               + "MasterCard"
               + "510510******5100"
               + "Visa"
               + "411111******1111"
               + "Visa"
               + "401288******1881"
               + "Visa"
               + "422222***2222"
               + "Note : Even though this number has a different character count than the other test numbers, it is the correct and functional number."
               + "Processor-specific Cards"
               + "Dankort (PBS)"
               + "501971******3742"
               + "Switch/Solo (Paymentech)"
               + "633110******0016");
    }

    @Test(groups = "fast")
    public void testLotsOfNonCcNumbers() {
        verify("American Express"
               + "378282246310006"
               + "American Express"
               + "371449635398432"
               + "American Express Corporate"
               + "378734493671001"
               + "Australian BankCard"
               + "5610591081018251"
               + "Diners Club"
               + "30569309025905"
               + "Diners Club"
               + "38520000023238"
               + "Discover"
               + "6011111111111118"
               + "Discover"
               + "6011000990139425"
               + "JCB"
               + "3530111333300001"
               + "JCB"
               + "3566002020360506"
               + "MasterCard"
               + "5555555555554445"
               + "MasterCard"
               + "5105105105105102"
               + "Visa"
               + "4111111111111112"
               + "Visa"
               + "4012888888881882"
               + "Visa"
               + "4222222222223"
               + "Note : Even though this number has a different character count than the other test numbers, it is the correct and functional number."
               + "Processor-specific Cards"
               + "Dankort (PBS)"
               + "5019717010103743"
               + "Switch/Solo (Paymentech)"
               + "6331101999990017",
               "American Express"
               + "378282246310006"
               + "American Express"
               + "371449635398432"
               + "American Express Corporate"
               + "378734493671001"
               + "Australian BankCard"
               + "5610591081018251"
               + "Diners Club"
               + "30569309025905"
               + "Diners Club"
               + "38520000023238"
               + "Discover"
               + "6011111111111118"
               + "Discover"
               + "6011000990139425"
               + "JCB"
               + "3530111333300001"
               + "JCB"
               + "3566002020360506"
               + "MasterCard"
               + "5555555555554445"
               + "MasterCard"
               + "5105105105105102"
               + "Visa"
               + "4111111111111112"
               + "Visa"
               + "4012888888881882"
               + "Visa"
               + "4222222222223"
               + "Note : Even though this number has a different character count than the other test numbers, it is the correct and functional number."
               + "Processor-specific Cards"
               + "Dankort (PBS)"
               + "5019717010103743"
               + "Switch/Solo (Paymentech)"
               + "6331101999990017");
    }

    @Test(groups = "fast")
    public void testQueryParametersAreObfuscated() throws Exception {
        final ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getLoggerName()).thenReturn(Obfuscator.LOGGING_FILTER_NAME);

        // Check the query parameters are obfuscated (see TestPatternObfuscator#testProfilingHeaderIsNotObfuscated)
        // TODO Pierre: Obfuscate the Authorization and X-Killbill-ApiSecret headers?
        verify("1 * Server in-bound request\n" +
               "1 > POST http://127.0.0.1:8080/1.0/kb/accounts/2a55045a-ce1d-4344-942d-b825536328f9/payments?pluginProperty=cc_number=4111111111111111\n" +
               "1 > X-Killbill-ApiSecret: lazar\n" +
               "1 > Authorization: Basic YWRtaW46cGFzc3dvcmQ=\n" +
               "1 > X-Killbill-CreatedBy: admin\n" +
               "1 > Host: 127.0.0.1:8080\n" +
               "1 > Content-Length: 62\n" +
               "1 > User-Agent: curl/7.30.0\n" +
               "1 > X-Killbill-Profiling-Req: DAO\n" +
               "1 > X-Killbill-ApiKey: bob\n" +
               "1 > Content-Type: application/json\n" +
               "1 > Accept: */*",
               "1 * Server in-bound request\n" +
               "1 > POST http://127.0.0.1:8080/1.0/kb/accounts/2a55045a-ce1d-4344-942d-b825536328f9/payments?pluginProperty=cc_number=411111******1111\n" +
               "1 > X-Killbill-ApiSecret: lazar\n" +
               "1 > Authorization: Basic YWRtaW46cGFzc3dvcmQ=\n" +
               "1 > X-Killbill-CreatedBy: admin\n" +
               "1 > Host: 127.0.0.1:8080\n" +
               "1 > Content-Length: 62\n" +
               "1 > User-Agent: curl/7.30.0\n" +
               "1 > X-Killbill-Profiling-Req: DAO\n" +
               "1 > X-Killbill-ApiKey: bob\n" +
               "1 > Content-Type: application/json\n" +
               "1 > Accept: */*",
               event);
    }

    @Test(groups = "fast")
    public void testIgnoreUUIDs() {
        Assert.assertTrue(obfuscator.luhnCheck("197760025906126"));
        verify("169fe72a-eba0-11e1-9776-0025906126aa", "169fe72a-eba0-11e1-9776-0025906126aa");
    }

    private void verify(final String input, final String output) {
        verify(input, output, Mockito.mock(ILoggingEvent.class));
    }

    private void verify(final String input, final String output, final ILoggingEvent event) {
        final String obfuscated = obfuscator.obfuscate(input, event);
        Assert.assertEquals(obfuscated, output, obfuscated);
    }
}
