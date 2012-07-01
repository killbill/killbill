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

import javax.xml.bind.JAXBException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;

import org.testng.annotations.Test;

import com.ning.billing.util.io.IOUtils;

public class TestXMLSchemaGenerator {
    @Test(groups = "fast", enabled = false)
    public void test() throws IOException, TransformerException, JAXBException {
        final InputStream stream = XMLSchemaGenerator.xmlSchema(XmlTestClass.class);
        System.out.println(IOUtils.toString(stream));
    }
}
