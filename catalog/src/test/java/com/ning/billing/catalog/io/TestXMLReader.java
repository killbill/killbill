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

package com.ning.billing.catalog.io;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.ning.billing.catalog.CatalogTestSuiteNoDB;
import com.ning.billing.catalog.StandaloneCatalog;
import com.ning.billing.util.config.catalog.XMLLoader;

import com.google.common.io.Resources;

public class TestXMLReader extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCatalogLoad() {
        try {
            XMLLoader.getObjectFromString(Resources.getResource("WeaponsHire.xml").toExternalForm(), StandaloneCatalog.class);
            XMLLoader.getObjectFromString(Resources.getResource("WeaponsHireSmall.xml").toExternalForm(), StandaloneCatalog.class);
        } catch (Exception e) {
            Assert.fail(e.toString());
        }
    }
}
