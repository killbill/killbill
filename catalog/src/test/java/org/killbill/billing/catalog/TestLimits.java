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

package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.PlanPhase;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.io.Resources;

public class TestLimits extends CatalogTestSuiteNoDB {

    private VersionedCatalog catalog;

    @BeforeClass(groups = "fast")
    public void beforeClass() throws Exception {
        super.beforeClass();
        catalog = loader.loadDefaultCatalog(Resources.getResource("WeaponsHireSmall.xml").toString());
    }

    @Test(groups = "fast")
    public void testLimits() throws Exception {
        PlanPhase phase = catalog.findCurrentPhase("pistol-monthly-evergreen");
        Assert.assertNotNull(phase);


        /*
                     <usages>
                    <usage>
                        <billingPeriod>NO_BILLING_PERIOD</billingPeriod>
                        <limits>
                            <limit>
                                <unit>targets</unit>
                                <min>3</min>
                            </limit>
                            <limit>
                                <unit>misfires</unit>
                                <max>20</max>
                            </limit>
                        </limits>
                    </usage>
                </usages>
         */

        Assert.assertTrue(catalog.compliesWithLimits("pistol-monthly-evergreen", "targets", 3));
        Assert.assertTrue(catalog.compliesWithLimits("pistol-monthly-evergreen", "targets", 2000));
        Assert.assertFalse(catalog.compliesWithLimits("pistol-monthly-evergreen", "targets", 2));

        Assert.assertTrue(catalog.compliesWithLimits("pistol-monthly-evergreen", "misfires", 3));
        Assert.assertFalse(catalog.compliesWithLimits("pistol-monthly-evergreen", "misfires", 21));
        Assert.assertTrue(catalog.compliesWithLimits("pistol-monthly-evergreen", "misfires", -1));


        /*
            <product name="Shotgun">
              <category>BASE</category>
              <limits>
                <limit>
                    <unit>shells</unit>
                    <max>300</max>
                </limit>
              </limits>
            </product>
        */
        Assert.assertTrue(catalog.compliesWithLimits("shotgun-monthly-evergreen", "shells", 100));
        Assert.assertFalse(catalog.compliesWithLimits("shotgun-monthly-evergreen", "shells", 400));
        Assert.assertTrue(catalog.compliesWithLimits("shotgun-monthly-evergreen", "shells", 250));

       /*
                   <!-- shotgun-annual-evergreen -->
                   <usages>
                    <usage>
                        <billingPeriod>ANNUAL</billingPeriod>
                        <limits>
                            <limit>
                                <unit>shells</unit>
                                <max>200</max>
                            </limit>
                        </limits>
                    </usage>
                </usages>
         */
        Assert.assertTrue(catalog.compliesWithLimits("shotgun-annual-evergreen", "shells", 100));
        Assert.assertFalse(catalog.compliesWithLimits("shotgun-annual-evergreen", "shells", 400));
        Assert.assertFalse(catalog.compliesWithLimits("shotgun-annual-evergreen", "shells", 250));

    }
}
