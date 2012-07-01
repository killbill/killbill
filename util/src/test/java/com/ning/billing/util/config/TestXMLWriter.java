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

package com.ning.billing.util.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestXMLWriter {
    public static final String TEST_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<xmlTestClass>" +
                    "<foo>foo</foo>" +
                    "<bar>1.0</bar>" +
                    "<lala>42</lala>" +
                    "</xmlTestClass>";

    @Test
    public void test() throws Exception {
        final InputStream is = new ByteArrayInputStream(TEST_XML.getBytes());
        final XmlTestClass test = XMLLoader.getObjectFromStream(new URI("internal:/"), is, XmlTestClass.class);
        assertEquals(test.getFoo(), "foo");
        assertEquals(test.getBar(), 1.0);
        assertEquals(test.getLala(), 42);

        final String output = XMLWriter.writeXML(test, XmlTestClass.class);

        System.out.println(output);
        assertEquals(output.replaceAll("\\s", ""), TEST_XML.replaceAll("\\s", ""));

    }


}
