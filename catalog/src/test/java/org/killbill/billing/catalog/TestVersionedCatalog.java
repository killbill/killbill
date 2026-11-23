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

package org.killbill.billing.catalog;

import org.killbill.xmlloader.ValidationErrors;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestVersionedCatalog extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testPlanProductCannotChangeAcrossVersions() throws Exception {
        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog();
        versionedCatalog.add(getCatalog("versionedCatalog/PlanProductChangeV1.xml"));
        versionedCatalog.add(getCatalog("versionedCatalog/PlanProductChangeV2.xml"));

        final ValidationErrors errors = new ValidationErrors();
        versionedCatalog.validate(null, errors);

        Assert.assertEquals(errors.size(), 1);
        Assert.assertTrue(errors.get(0).getDescription().contains("Product for plan 'standard-weekly' differs"));
    }

    @Test(groups = "fast")
    public void testPlanProductUnchangedAcrossVersions() throws Exception {
        final DefaultVersionedCatalog versionedCatalog = new DefaultVersionedCatalog();
        versionedCatalog.add(getCatalog("versionedCatalog/PlanProductChangeV1.xml"));
        versionedCatalog.add(getCatalog("versionedCatalog/PlanProductChangeV2SameProduct.xml"));

        final ValidationErrors errors = new ValidationErrors();
        versionedCatalog.validate(null, errors);

        Assert.assertEquals(errors.size(), 0);
    }
}
