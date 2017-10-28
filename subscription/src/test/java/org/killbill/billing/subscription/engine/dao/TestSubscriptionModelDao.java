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

package org.killbill.billing.subscription.engine.dao;

import org.killbill.billing.subscription.SubscriptionTestSuiteNoDB;
import org.killbill.billing.subscription.engine.dao.model.SubscriptionBundleModelDao;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestSubscriptionModelDao extends SubscriptionTestSuiteNoDB {

    @Test(groups = "fast")
    public void testBundleExternalKeyPattern1() throws Exception {
        final SubscriptionBundleModelDao b = new SubscriptionBundleModelDao();
        b.setExternalKey("1235");

        assertEquals(SubscriptionBundleModelDao.toSubscriptionBundle(b).getExternalKey(), "1235");
    }


    @Test(groups = "fast")
    public void testBundleExternalKeyPattern2() throws Exception {
        final SubscriptionBundleModelDao b = new SubscriptionBundleModelDao();
        b.setExternalKey("kbtsf-343453:1235");
        assertEquals(SubscriptionBundleModelDao.toSubscriptionBundle(b).getExternalKey(), "1235");
    }

    @Test(groups = "fast")
    public void testBundleExternalKeyPattern3() throws Exception {
        final SubscriptionBundleModelDao b = new SubscriptionBundleModelDao();
        b.setExternalKey("kbXXXX-343453:1235");
        assertEquals(SubscriptionBundleModelDao.toSubscriptionBundle(b).getExternalKey(), "1235");
    }

}
