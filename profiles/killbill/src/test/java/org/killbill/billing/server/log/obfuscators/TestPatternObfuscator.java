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

import java.util.Collection;
import java.util.List;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

// Test does not extend base class on purpose so as to not pull the static Logger that would be initialized too early
public class TestPatternObfuscator { /* extends ServerTestSuiteNoDB */

    @AfterMethod(groups = "fast")
    public void afterMethod() {
        System.clearProperty("killbill.server.log.obfuscate.keywords");
        System.clearProperty("killbill.server.log.obfuscate.patterns.separator");
        System.clearProperty("killbill.server.log.obfuscate.patterns");
    }

    @Test(groups = "fast")
    public void testLoadKeywords() {
        //without setting the killbill.server.log.obfuscate.patterns property
        PatternObfuscator obfuscator = new PatternObfuscator();
        Collection<String> keywords = obfuscator.loadKeywords();
        Assert.assertEquals(keywords.size(), 22);
        Collection<String> actual = List.of(
                "accountnumber",
                "authenticationdata",
                "bankaccountnumber",
                "banknumber",
                "bic",
                "cardvalidationnum",
                "cavv",
                "ccFirstName",
                "ccLastName",
                "ccNumber",
                "ccTrackData",
                "ccVerificationValue",
                "ccvv",
                "cvNumber",
                "cvc",
                "cvv",
                "email",
                "iban",
                "name",
                "number",
                "password",
                "xid");
        Assert.assertEquals(actual, keywords);

        //after setting the the killbill.server.log.obfuscate.patterns property
        System.setProperty("killbill.server.log.obfuscate.keywords", "accountnumber,authenticationdata");
        keywords = obfuscator.loadKeywords();
        Assert.assertEquals(keywords.size(), 2);
        actual = List.of(
                "accountnumber",
                "authenticationdata");
        Assert.assertEquals(actual, keywords);

    }

    @Test(groups = "fast")
    public void testLoadPatterns() {
        //without setting any properties
        PatternObfuscator obfuscator = new PatternObfuscator();
        Collection<String> patterns = obfuscator.loadPatterns();
        Assert.assertEquals(patterns.size(), 2);
        Collection<String> actual = List.of("\\s*=\\s*'([^']+)'",
                                            "\\s*=\\s*\"([^\"]+)\"");
        Assert.assertEquals(actual, patterns);

        //without setting the killbill.server.log.obfuscate.patterns.separator property
        System.setProperty("killbill.server.log.obfuscate.patterns", "\\s*=\\s*'([^']+)',\\s*=\\s*\"([^\"]+)\"");
        patterns = obfuscator.loadPatterns();
        Assert.assertEquals(patterns.size(), 2);
        actual = List.of("\\s*=\\s*'([^']+)'",
                         "\\s*=\\s*\"([^\"]+)\"");
        Assert.assertEquals(actual, patterns);

        // after setting the killbill.server.log.obfuscate.patterns property and killbill.server.log.obfuscate.patterns.separator property
        System.setProperty("killbill.server.log.obfuscate.patterns.separator", "~");
        System.setProperty("killbill.server.log.obfuscate.patterns", "\\s*=\\s*'([^']+)'~\\s*=\\s*\"([^\"]+)~\\s*=\\s*([^ '\",{}]+)~\\s*:\\s*'([^'\",{}]+)'");
        patterns = obfuscator.loadPatterns();
        Assert.assertEquals(patterns.size(), 4);
        actual = List.of("\\s*=\\s*'([^']+)'",
                         "\\s*=\\s*\"([^\"]+)",
                         "\\s*=\\s*([^ '\",{}]+)",
                         "\\s*:\\s*'([^'\",{}]+)'");
        Assert.assertEquals(actual, patterns);
    }

    @Test(groups = "fast")
    public void testLoadPatternSeparator() {
        //without setting the killbill.server.log.obfuscate.patterns.separator property
        PatternObfuscator obfuscator = new PatternObfuscator();
        String patternSeparator = obfuscator.loadPatternSeparator();
        Assert.assertEquals(patternSeparator, ",");
        // after setting the killbill.server.log.obfuscate.patterns.separator property
        System.setProperty("killbill.server.log.obfuscate.patterns.separator", "#");
        patternSeparator = obfuscator.loadPatternSeparator();
        Assert.assertEquals(patternSeparator, "#");
    }

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
               "<ns:holderName>********</holderName>\n" +
               "<ns:number>****************</number>\n" +
               "<ns2:shopperEmail>***************</ns2:shopperEmail>\n" +
               "<ns2:shopperIP>127.0.0.1</ns2:shopperIP>\n" +
               "<ns2:shopperInteraction xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "<ns2:shopperName>\n" +
               "    <firstName>***</firstName>\n" +
               "    <gender xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <infix xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>\n" +
               "    <lastName>*****</lastName>\n" +
               "</ns2:shopperName>\n");
    }

    @Test(groups = "fast")
    public void testXmlWithAttributesOnTheKey() throws Exception {
        verify("<PayerInfo xsi:type=\"ebl:PayerInfoType\">\n" +
               "<accountnumber xsi:type=\"ebl:EmailAddressType\">michaelhk@gmail.com</accountnumber>\n" +
               "<PayerID xsi:type=\"ebl:UserIDType\">ZZS5TS7FD7MRA</PayerID>\n" +
               "<PayerStatus xsi:type=\"ebl:PayPalUserStatusCodeType\">verified</PayerStatus>\n" +
               "<PayerName xsi:type=\"ebl:PersonNameType\">\n" +
               "<Salutation xmlns=\"urn:ebay:apis:eBLBaseComponents\"></Salutation>\n" +
               "<ccFirstName xmlns=\"urn:ebay:apis:eBLBaseComponents\">Michael</ccFirstName>\n" +
               "<MiddleName xmlns=\"urn:ebay:apis:eBLBaseComponents\"></MiddleName>\n" +
               "<ccLastName xmlns=\"urn:ebay:apis:eBLBaseComponents\">Henrick</ccLastName>",

               "<PayerInfo xsi:type=\"ebl:PayerInfoType\">\n" +
               "<accountnumber xsi:type=\"ebl:EmailAddressType\">*******************</accountnumber>\n" +
               "<PayerID xsi:type=\"ebl:UserIDType\">ZZS5TS7FD7MRA</PayerID>\n" +
               "<PayerStatus xsi:type=\"ebl:PayPalUserStatusCodeType\">verified</PayerStatus>\n" +
               "<PayerName xsi:type=\"ebl:PersonNameType\">\n" +
               "<Salutation xmlns=\"urn:ebay:apis:eBLBaseComponents\"></Salutation>\n" +
               "<ccFirstName xmlns=\"urn:ebay:apis:eBLBaseComponents\">*******</ccFirstName>\n" +
               "<MiddleName xmlns=\"urn:ebay:apis:eBLBaseComponents\"></MiddleName>\n" +
               "<ccLastName xmlns=\"urn:ebay:apis:eBLBaseComponents\">*******</ccLastName>");
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
               "  <firstName>****</firstName>\n" +
               "  <lastName>***</lastName>\n" +
               "  <street1>5, oakriu road</street1>\n" +
               "  <street2>apt. 298</street2>\n" +
               "  <city>Gdio Foia</city>\n" +
               "  <state>FL</state>\n" +
               "  <postalCode>49302</postalCode>\n" +
               "  <country>US</country>\n" +
               "  <email>**************************</email>\n" +
               "</billTo>\n" +
               "<purchaseTotals>\n" +
               "  <currency>USD</currency>\n" +
               "  <grandTotalAmount>0.00</grandTotalAmount>\n" +
               "</purchaseTotals>\n" +
               "<card>\n" +
               "  <accountNumber>****************</accountNumber>\n" +
               "  <expirationMonth>12</expirationMonth>\n" +
               "  <expirationYear>2017</expirationYear>\n" +
               "  <cvNumber>****</cvNumber>\n" +
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
               "<litleOnlineRequest merchantId=\\\"merchant_id\\\" version=\\\"8.18\\\" xmlns=\\\"http://www.litle.com/schema\\\"><authentication><user>login</user><password>********</password></authentication><sale id=\\\"615b9cb3-8580-4f57-bf69-9\\\" reportGroup=\\\"Default Report Group\\\"><orderId>615b9cb3-8580-4f57-bf69-9</orderId><amount>10000</amount><orderSource>ecommerce</orderSource><billToAddress><name>********</name><email>**************************</email><addressLine1>5, oakriu road</addressLine1><addressLine2>apt. 298</addressLine2><city>Gdio Foia</city><state>FL</state><zip>49302</zip><country>US</country></billToAddress><shipToAddress/><card><type>VI</type><number>****************</number><expDate>1217</expDate><cardValidationNum>****</cardValidationNum></card></sale></litleOnlineRequest>");
    }

    @Test(groups = "fast")
    public void testJSON() throws Exception {
        verify("{\n" +
               "  \"card\": {\n" +
               "    \"id\": \"card_483etw4er9fg4vF3sQdrt3FG\",\n" +
               "    \"object\": \"card\",\n" +
               "    \"banknumber\": 4111111111111111,\n" +
               "    \"cvv\" : 111,\n" +
               "    \"cvv\": 111,\n" +
               "    \"cvv\": \"111\",\n" +
               "    \"data\": {\"cvv\" : 111 },\n" +
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
               "    \"banknumber\": ****************,\n" +
               "    \"cvv\" : ***,\n" +
               "    \"cvv\": ***,\n" +
               "    \"cvv\": *****,\n" +
               "    \"data\": {\"cvv\" : ****},\n" +
               "    \"last4\": \"0000\",\n" +
               "    \"brand\": \"Visa\",\n" +
               "    \"funding\": \"credit\",\n" +
               "    \"exp_month\": 6,\n" +
               "    \"exp_year\": 2019,\n" +
               "    \"fingerprint\": \"HOh74kZU387WlUvy\",\n" +
               "    \"country\": \"US\",\n" +
               "    \"name\": ***********,\n" +
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

    @Test(groups = "fast", description = "test for custom keyword (cell_phone) in JSON")
    public void testJSONWithCustomKeyWord() throws Exception {
        System.setProperty("killbill.server.log.obfuscate.keywords","accountnumber,authenticationdata,banknumber,bic,cardvalidationnum,cavv,ccFirstName,ccLastName,ccNumber,ccTrackData,ccVerificationValue,ccvv,cvNumber,cvc,cvv,email,iban,name,number,password,xid,cell_phone");
        PatternObfuscator obfuscator = new PatternObfuscator();
        verify(obfuscator,"{\n" +
               "  \"card\": {\n" +
               "    \"id\": \"card_483etw4er9fg4vF3sQdrt3FG\",\n" +
               "    \"object\": \"card\",\n" +
               "    \"banknumber\": 4111111111111111,\n" +
               "    \"cvv\" : 111,\n" +
               "    \"cvv\": 111,\n" +
               "    \"cvv\": \"111\",\n" +
               "    \"data\": {\"cvv\" : 111 },\n" +
               "    \"last4\": \"0000\",\n" +
               "    \"brand\": \"Visa\",\n" +
               "    \"funding\": \"credit\",\n" +
               "    \"exp_month\": 6,\n" +
               "    \"exp_year\": 2019,\n" +
               "    \"fingerprint\": \"HOh74kZU387WlUvy\",\n" +
               "    \"country\": \"US\",\n" +
               "    \"name\": \"Bob Smith\",\n" +
               "    \"cell_phone\": \"212-421-1278\",\n" +
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
               "    \"banknumber\": ****************,\n" +
               "    \"cvv\" : ***,\n" +
               "    \"cvv\": ***,\n" +
               "    \"cvv\": *****,\n" +
               "    \"data\": {\"cvv\" : ****},\n" +
               "    \"last4\": \"0000\",\n" +
               "    \"brand\": \"Visa\",\n" +
               "    \"funding\": \"credit\",\n" +
               "    \"exp_month\": 6,\n" +
               "    \"exp_year\": 2019,\n" +
               "    \"fingerprint\": \"HOh74kZU387WlUvy\",\n" +
               "    \"country\": \"US\",\n" +
               "    \"name\": ***********,\n" +
               "    \"cell_phone\": **************,\n" +
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
               "  <value xsi:type=\"xsd:string\">****</value>\n" +
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
               "[cybersource-plugin] \"<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><accountNumber>****************</accountNumber>\\n  <expirationMonth>09</expirationMonth>\\n  \"");
    }

    @Test(groups = "fast")
    public void testProfilingHeaderIsNotObfuscated() throws Exception {
        final ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        Mockito.when(event.getLoggerName()).thenReturn(Obfuscator.LOGGING_FILTER_NAME);

        verify("1 * Server out-bound response\n" +
               "1 < 500\n" +
               "1 < Content-Type: application/json\n" +
               "1 < X-Killbill-Profiling-Resp: {\"rawData\":[{\"name\":\"DAO:AccountSqlDao:getById\",\"durationUsec\":14873},{\"name\":\"DAO:PaymentMethodSqlDao:getById\",\"durationUsec\":10438},{\"name\":\"DAO:PaymentSqlDao:create\",\"durationUsec\":31750},{\"name\":\"DAO:TransactionSqlDao:create\",\"durationUsec\":23121},{\"name\":\"DAO:PaymentSqlDao:getById\",\"durationUsec\":2541},{\"name\":\"DAO:TransactionSqlDao:getByPaymentId\",\"durationUsec\":3574},{\"name\":\"DAO:PaymentMethodSqlDao:getPaymentMethodIncludedDelete\",\"durationUsec\":1763},{\"name\":\"DAO:TransactionSqlDao:updateTransactionStatus\",\"durationUsec\":13994},{\"name\":\"DAO:PaymentSqlDao:updatePaymentStateName\",\"durationUsec\":11929},{\"name\":\"DAO:TransactionSqlDao:getById\",\"durationUsec\":5245}]}",
               event);
    }

    @Test(groups = "fast", description="test for key='value', key=\"value\"")
    public void testKeyValuePattern1() throws Exception { // works with default keywords/patterns
        verify("ENTERING onSuccessCall paymentMethodId='e92a3bfd-0713-4396-a1e2-ff46cb051f8c' ccVerificationValue='123' ccNumber = '4111111111111111' ccTrackData=\"XXX\" ccFirstName = \"John\" ccLastName=\"'Smith'\"",
               "ENTERING onSuccessCall paymentMethodId='e92a3bfd-0713-4396-a1e2-ff46cb051f8c' ccVerificationValue='***' ccNumber = '****************' ccTrackData=\"***\" ccFirstName = \"****\" ccLastName=\"*******\"");
    }

    @Test(groups = "fast", description = "test for key=value")
    //Requires -Dkillbill.server.log.obfuscate.patterns.separator=# -Dkillbill.server.log.obfuscate.patterns="\s*=\s*'([^']+)'#\s*=\s*\"([^\"]+)#\s*=\s*([^ '\",]+)"
    public void testKeyValuePattern2() throws Exception {
        System.setProperty("killbill.server.log.obfuscate.patterns.separator","#");
        System.setProperty("killbill.server.log.obfuscate.patterns","\\s*=\\s*'([^']+)'#\\s*=\\s*\\\"([^\\\"]+)#\\s*=\\s*([^ '\\\",]+)");
        PatternObfuscator obfuscator = new PatternObfuscator();
        verify(obfuscator, "ENTERING onSuccessCall password=e92a3bfd password = ff46cb051f8c, ccNumber = '4111111111111111' ccTrackData=\"XXX\" ccFirstName = \"John\" ccLastName=\"'Smith'\"",
               "ENTERING onSuccessCall password=******** password = ************, ccNumber = '****************' ccTrackData=\"***\" ccFirstName = \"****\" ccLastName=\"*******\"");
    }

    @Test(groups = "fast", description = "testing key:value")
    //Requires -Dkillbill.server.log.obfuscate.patterns.separator=# -Dkillbill.server.log.obfuscate.patterns=\s*=\s*'([^']+)'#\s*=\s*\"([^\"]+)#\s*:\s*'([^'\",{}]+)'
    public void testKeyValuePattern3() throws Exception {
        System.setProperty("killbill.server.log.obfuscate.patterns.separator","#");
        System.setProperty("killbill.server.log.obfuscate.patterns","\\s*=\\s*'([^']+)'#\\s*=\\s*\\\"([^\\\"]+)#\\s*:\\s*'([^'\\\",{}]+)");
        PatternObfuscator obfuscator = new PatternObfuscator();

        verify(obfuscator, "paymentMethodId:UUIDArgument{value=null},currency:USD,id:UUIDArgument{value=ae373eb5-0863-4707-b3d2-8c62d4516b72},reasonCode:'Create Account',migrated:null,class:class org.killbill.billing.callcontext.InternalCallContext,email:'aaabbbcccdddd@gmail.com',callOrigin:INTERNAL,tenantRecordId:1,comments:'Create Account by plugin',updatedBy:'user',address2:'',address1:'508 e Prospect st'",
               "paymentMethodId:UUIDArgument{value=null},currency:USD,id:UUIDArgument{value=ae373eb5-0863-4707-b3d2-8c62d4516b72},reasonCode:'Create Account',migrated:null,class:class org.killbill.billing.callcontext.InternalCallContext,email:'***********************',callOrigin:INTERNAL,tenantRecordId:1,comments:'Create Account by plugin',updatedBy:'user',address2:'',address1:'508 e Prospect st'");
    }

    @Test(groups = "fast", description = "test key=\"value\" for firstName")
    //Requires -Dkillbill.server.log.obfuscate.patterns.separator=# -Dkillbill.server.log.obfuscate.patterns="\s*=\s*'([^']+)'#\s*=\s*\"([^\"]+)#\s*=\s*([^ '\",]+)"
    public void testKeyValuePattern4() throws Exception {
        System.setProperty("killbill.server.log.obfuscate.patterns.separator","#");
        System.setProperty("killbill.server.log.obfuscate.patterns","\\s*=\\s*'([^']+)'#\\s*=\\s*\\\"([^\\\"]+)#\\s*=\\s*([^ '\\\",]+)");
        PatternObfuscator obfuscator = new PatternObfuscator();

        verify(obfuscator,"ENTERING onSuccessCall password=e92a3bfd password = ff46cb051f8c, ccNumber = '4111111111111111' ccTrackData=\"XXX\" firstName = \"john.doe@gmail.com\" lastName=\"John Doe\"",
               "ENTERING onSuccessCall password=******** password = ************, ccNumber = '****************' ccTrackData=\"***\" firstName = \"******************\" lastName=\"********\"");
    }

    @Test(groups = "fast", description = "test req.requestURI keyword and key=value pattern")
    // Requires -Dkillbill.server.log.obfuscate.patterns.separator=# -Dkillbill.server.log.obfuscate.keywords=req.requestURI -Dkillbill.server.log.obfuscate.patterns="\s*=\s*'([^']+)'#\s*=\s*\"([^\"]+)#\s*=\s*([^ '\",]+)"
    public void testCustomKeywordAndKeyValuePattern() throws Exception {
        System.setProperty("killbill.server.log.obfuscate.keywords", "req.requestURI");
        System.setProperty("killbill.server.log.obfuscate.patterns.separator","#");
        System.setProperty("killbill.server.log.obfuscate.patterns","\\s*=\\s*'([^']+)'#\\s*=\\s*\\\"([^\\\"]+)#\\s*=\\s*([^ '\\\",]+)");
        PatternObfuscator obfuscator = new PatternObfuscator();
        verify(obfuscator,"req.requestURI= /strommerce/1.0/kb/accounts/search/grucaunicegroi-4387@yopmail.com",
               "req.requestURI= ******************************************************************");
    }

    private void verify(final String input, final ILoggingEvent event) {
        verify(input, input, event);
    }

    private void verify(final String input, final String output) {
        verify(input, output, Mockito.mock(ILoggingEvent.class));
    }

    private void verify(final String input, final String output, final ILoggingEvent event) {
        PatternObfuscator obfuscator = new PatternObfuscator();
        verify(obfuscator, input, output, event);
    }

    private void verify(PatternObfuscator obfuscator, final String input, final String output) {
        verify(obfuscator, input, output, Mockito.mock(ILoggingEvent.class));
    }

    private void verify(PatternObfuscator obfuscator, final String input, final String output, final ILoggingEvent event) {
        final String obfuscated = obfuscator.obfuscate(input, event);
        Assert.assertEquals(obfuscated, output, obfuscated);
    }
}
