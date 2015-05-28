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
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestPatternObfuscator extends ServerTestSuiteNoDB {

    private final PatternObfuscator obfuscator = new PatternObfuscator();

    @Test(groups = "fast")
    public void testAdyen() throws Exception {
        verify("<ns:expiryMonth>04</expiryMonth>\n" +
               "<ns:expiryYear>2015</expiryYear>\n" +
               "<ns:holderName>  test  </holderName>\n" +
               "<ns:number>5123456789012346</number>\n" +
               "<ns2:shopperEmail>Bob@example.org</ns2:shopperEmail>\n" +
               "<ns2:shopperIP>127.0.0.1</ns2:shopperIP>\n" +
               "<ns2:shopperInteraction xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "<ns2:shopperName>\n" +
               "    <firstName>Bob</firstName>\n" +
               "    <gender xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <infix xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <lastName>Smith</lastName>\n" +
               "</ns2:shopperName>\n",
               "<ns:expiryMonth>04</expiryMonth>\n" +
               "<ns:expiryYear>2015</expiryYear>\n" +
               "<ns:holderName>*MASKED*</holderName>\n" +
               "<ns:number>*****MASKED*****</number>\n" +
               "<ns2:shopperEmail>****MASKED*****</ns2:shopperEmail>\n" +
               "<ns2:shopperIP>127.0.0.1</ns2:shopperIP>\n" +
               "<ns2:shopperInteraction xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "<ns2:shopperName>\n" +
               "    <firstName>MASKED</firstName>\n" +
               "    <gender xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <infix xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <lastName>MASKED</lastName>\n" +
               "</ns2:shopperName>\n");
    }

    @Test(groups = "fast")
    public void testCyberSource() throws Exception {
        verify("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
               "  <s:Header>\n" +
               "    <wsse:Security s:mustUnderstand=\"1\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n" +
               "      <wsse:UsernameToken>\n" +
               "        <wsse:Username/>\n" +
               "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\"/>\n" +
               "      </wsse:UsernameToken>\n" +
               "    </wsse:Security>\n" +
               "  </s:Header>\n" +
               "  <s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
               "    <requestMessage xmlns=\"urn:schemas-cybersource-com:transaction-data-1.109\">\n" +
               "      <merchantID/>\n" +
               "      <merchantReferenceCode>e92a3bfd-0713-4396-a1e2-ff46cb051f8c</merchantReferenceCode>\n" +
               "      <clientLibrary>Ruby Active Merchant</clientLibrary>\n" +
               "      <clientLibraryVersion>1.47.0</clientLibraryVersion>\n" +
               "      <clientEnvironment>java</clientEnvironment>\n" +
               "<billTo>\n" +
               "  <firstName>John</firstName>\n" +
               "  <lastName>Doe</lastName>\n" +
               "  <street1>5, oakriu road</street1>\n" +
               "  <street2>apt. 298</street2>\n" +
               "  <city>Gdio Foia</city>\n" +
               "  <state>FL</state>\n" +
               "  <postalCode>49302</postalCode>\n" +
               "  <country>US</country>\n" +
               "  <email>1428324461-test@tester.com</email>\n" +
               "</billTo>\n" +
               "<purchaseTotals>\n" +
               "  <currency>USD</currency>\n" +
               "  <grandTotalAmount>0.00</grandTotalAmount>\n" +
               "</purchaseTotals>\n" +
               "<card>\n" +
               "  <accountNumber>4242424242424242</accountNumber>\n" +
               "  <expirationMonth>12</expirationMonth>\n" +
               "  <expirationYear>2017</expirationYear>\n" +
               "  <cvNumber>1234</cvNumber>\n" +
               "  <cardType>001</cardType>\n" +
               "</card>\n" +
               "<subscription>\n" +
               "  <paymentMethod>credit card</paymentMethod>\n" +
               "</subscription>\n" +
               "<recurringSubscriptionInfo>\n" +
               "  <amount>0.00</amount>\n" +
               "  <frequency>on-demand</frequency>\n" +
               "  <approvalRequired>false</approvalRequired>\n" +
               "</recurringSubscriptionInfo>\n" +
               "<paySubscriptionCreateService run=\"true\"/>\n" +
               "<businessRules>\n" +
               "</businessRules>\n" +
               "    </requestMessage>\n" +
               "  </s:Body>\n" +
               "</s:Envelope>",
               "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">\n" +
               "  <s:Header>\n" +
               "    <wsse:Security s:mustUnderstand=\"1\" xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\">\n" +
               "      <wsse:UsernameToken>\n" +
               "        <wsse:Username/>\n" +
               "        <wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText\"/>\n" +
               "      </wsse:UsernameToken>\n" +
               "    </wsse:Security>\n" +
               "  </s:Header>\n" +
               "  <s:Body xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\">\n" +
               "    <requestMessage xmlns=\"urn:schemas-cybersource-com:transaction-data-1.109\">\n" +
               "      <merchantID/>\n" +
               "      <merchantReferenceCode>e92a3bfd-0713-4396-a1e2-ff46cb051f8c</merchantReferenceCode>\n" +
               "      <clientLibrary>Ruby Active Merchant</clientLibrary>\n" +
               "      <clientLibraryVersion>1.47.0</clientLibraryVersion>\n" +
               "      <clientEnvironment>java</clientEnvironment>\n" +
               "<billTo>\n" +
               "  <firstName>MASKED</firstName>\n" +
               "  <lastName>MASKED</lastName>\n" +
               "  <street1>5, oakriu road</street1>\n" +
               "  <street2>apt. 298</street2>\n" +
               "  <city>Gdio Foia</city>\n" +
               "  <state>FL</state>\n" +
               "  <postalCode>49302</postalCode>\n" +
               "  <country>US</country>\n" +
               "  <email>**********MASKED**********</email>\n" +
               "</billTo>\n" +
               "<purchaseTotals>\n" +
               "  <currency>USD</currency>\n" +
               "  <grandTotalAmount>0.00</grandTotalAmount>\n" +
               "</purchaseTotals>\n" +
               "<card>\n" +
               "  <accountNumber>*****MASKED*****</accountNumber>\n" +
               "  <expirationMonth>12</expirationMonth>\n" +
               "  <expirationYear>2017</expirationYear>\n" +
               "  <cvNumber>MASKED</cvNumber>\n" +
               "  <cardType>001</cardType>\n" +
               "</card>\n" +
               "<subscription>\n" +
               "  <paymentMethod>credit card</paymentMethod>\n" +
               "</subscription>\n" +
               "<recurringSubscriptionInfo>\n" +
               "  <amount>0.00</amount>\n" +
               "  <frequency>on-demand</frequency>\n" +
               "  <approvalRequired>false</approvalRequired>\n" +
               "</recurringSubscriptionInfo>\n" +
               "<paySubscriptionCreateService run=\"true\"/>\n" +
               "<businessRules>\n" +
               "</businessRules>\n" +
               "    </requestMessage>\n" +
               "  </s:Body>\n" +
               "</s:Envelope>");
    }

    @Test(groups = "fast")
    public void testLitle() throws Exception {
        verify("<litleOnlineRequest merchantId=\\\"merchant_id\\\" version=\\\"8.18\\\" xmlns=\\\"http://www.litle.com/schema\\\"><authentication><user>login</user><password>password</password></authentication><sale id=\\\"615b9cb3-8580-4f57-bf69-9\\\" reportGroup=\\\"Default Report Group\\\"><orderId>615b9cb3-8580-4f57-bf69-9</orderId><amount>10000</amount><orderSource>ecommerce</orderSource><billToAddress><name>John Doe</name><email>1428325948-test@tester.com</email><addressLine1>5, oakriu road</addressLine1><addressLine2>apt. 298</addressLine2><city>Gdio Foia</city><state>FL</state><zip>49302</zip><country>US</country></billToAddress><shipToAddress/><card><type>VI</type><number>4242424242424242</number><expDate>1217</expDate><cardValidationNum>1234</cardValidationNum></card></sale></litleOnlineRequest>",
               "<litleOnlineRequest merchantId=\\\"merchant_id\\\" version=\\\"8.18\\\" xmlns=\\\"http://www.litle.com/schema\\\"><authentication><user>login</user><password>*MASKED*</password></authentication><sale id=\\\"615b9cb3-8580-4f57-bf69-9\\\" reportGroup=\\\"Default Report Group\\\"><orderId>615b9cb3-8580-4f57-bf69-9</orderId><amount>10000</amount><orderSource>ecommerce</orderSource><billToAddress><name>*MASKED*</name><email>**********MASKED**********</email><addressLine1>5, oakriu road</addressLine1><addressLine2>apt. 298</addressLine2><city>Gdio Foia</city><state>FL</state><zip>49302</zip><country>US</country></billToAddress><shipToAddress/><card><type>VI</type><number>*****MASKED*****</number><expDate>1217</expDate><cardValidationNum>MASKED</cardValidationNum></card></sale></litleOnlineRequest>");
    }

    @Test(groups = "fast")
    public void testJSON() throws Exception {
        verify("{\n" +
               "  \"card\": {\n" +
               "    \"id\": \"card_483etw4er9fg4vF3sQdrt3FG\",\n" +
               "    \"object\": \"card\",\n" +
               "    \"banknumber\": 4111111111111111,\n" +
               "    \"last4\": \"0000\",\n" +
               "    \"brand\": \"Visa\",\n" +
               "    \"funding\": \"credit\",\n" +
               "    \"exp_month\": 6,\n" +
               "    \"exp_year\": 2019,\n" +
               "    \"fingerprint\": \"HOh74kZU387WlUvy\",\n" +
               "    \"country\": \"US\",\n" +
               "    \"name\": \"Bob Smith\",\n" +
               "    \"address_line1\": null,\n" +
               "    \"address_line2\": null,\n" +
               "    \"address_city\": null,\n" +
               "    \"address_state\": null,\n" +
               "    \"address_zip\": null,\n" +
               "    \"address_country\": null,\n" +
               "    \"dynamic_last4\": \"4242\",\n" +
               "    \"customer\": null,\n" +
               "    \"type\": \"Visa\"}\n" +
               "}",
               "{\n" +
               "  \"card\": {\n" +
               "    \"id\": \"card_483etw4er9fg4vF3sQdrt3FG\",\n" +
               "    \"object\": \"card\",\n" +
               "    \"banknumber\": *****MASKED*****,\n" +
               "    \"last4\": \"0000\",\n" +
               "    \"brand\": \"Visa\",\n" +
               "    \"funding\": \"credit\",\n" +
               "    \"exp_month\": 6,\n" +
               "    \"exp_year\": 2019,\n" +
               "    \"fingerprint\": \"HOh74kZU387WlUvy\",\n" +
               "    \"country\": \"US\",\n" +
               "    \"name\": **MASKED***,\n" +
               "    \"address_line1\": null,\n" +
               "    \"address_line2\": null,\n" +
               "    \"address_city\": null,\n" +
               "    \"address_state\": null,\n" +
               "    \"address_zip\": null,\n" +
               "    \"address_country\": null,\n" +
               "    \"dynamic_last4\": \"4242\",\n" +
               "    \"customer\": null,\n" +
               "    \"type\": \"Visa\"}\n" +
               "}");

    }

    @Test(groups = "fast")
    public void testPayU() throws Exception {
        verify("<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccvv</key>\n" +
               "  <value xsi:type=\"xsd:string\">1234</value>\n" +
               "</entry>\n" +
               "<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccnum</key>\n" +
               "  <value xsi:type=\"xsd:string\">4111111111111111</value>\n" +
               "</entry>\n" +
               "<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccexpmon</key>\n" +
               "  <value xsi:type=\"xsd:string\">12</value>\n" +
               "</entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccexpyear</key>\n" +
               "  <value xsi:type=\"xsd:string\">2018</value>\n" +
               "</entry>\n",
               "<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccvv</key>\n" +
               "  <value xsi:type=\"xsd:string\">MASKED</value>\n" +
               "</entry>\n" +
               "<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccnum</key>\n" +
               "  <value xsi:type=\"xsd:string\">4111111111111111</value>\n" +
               "</entry>\n" +
               "<entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccexpmon</key>\n" +
               "  <value xsi:type=\"xsd:string\">12</value>\n" +
               "</entry>\n" +
               "  <key xsi:type=\"xsd:string\">PayU.ccexpyear</key>\n" +
               "  <value xsi:type=\"xsd:string\">2018</value>\n" +
               "</entry>\n"
              );
    }

    @Test(groups = "fast", description = "Test for ActiveMerchant wiredump_device logging")
    public void testWithQuotedNewLines() throws Exception {
        verify("[cybersource-plugin] \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><accountNumber>4111111111111111</accountNumber>\\n  <expirationMonth>09</expirationMonth>\\n  \"",
               "[cybersource-plugin] \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><accountNumber>*****MASKED*****</accountNumber>\\n  <expirationMonth>09</expirationMonth>\\n  \"");
    }

    private void verify(final String input, final String output) {
        final String obfuscated = obfuscator.obfuscate(input);
        Assert.assertEquals(obfuscated, output, obfuscated);
    }
}
