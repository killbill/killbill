/*
 * Copyright 2010-2013 Ning, Inc.
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

package org.killbill.billing.overdue.config.io;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.Charset;

import org.killbill.billing.overdue.config.DefaultOverdueConfig;
import org.killbill.xmlloader.XMLWriter;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.overdue.OverdueTestSuiteNoDB;
import org.killbill.xmlloader.XMLLoader;

import com.google.common.io.Resources;

public class TestConfig extends OverdueTestSuiteNoDB {

    @Test(groups = "fast")
    public void testConfigLoad() throws Exception {
        XMLLoader.getObjectFromString(Resources.getResource("OverdueConfig.xml").toExternalForm(), DefaultOverdueConfig.class);
    }

    @Test(groups = "fast")
    public void testMarshallUnmarshall() throws Exception {
        final DefaultOverdueConfig overdueConfig = XMLLoader.getObjectFromString(Resources.getResource("OverdueConfig3.xml").toExternalForm(), DefaultOverdueConfig.class);
        final String overdueConfigStr = XMLWriter.writeXML(overdueConfig, DefaultOverdueConfig.class);

        //System.err.println(overdueConfigStr);
        final DefaultOverdueConfig overdueConfig2 = XMLLoader.getObjectFromStream(new URI("dummy"), new ByteArrayInputStream(overdueConfigStr.getBytes(Charset.forName("UTF-8"))), DefaultOverdueConfig.class);
        final String overdueConfigStr2 = XMLWriter.writeXML(overdueConfig2, DefaultOverdueConfig.class);
        Assert.assertEquals(overdueConfigStr, overdueConfigStr2);
    }
}
