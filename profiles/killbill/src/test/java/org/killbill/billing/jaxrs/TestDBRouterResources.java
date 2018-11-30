/*
 * Copyright 2014-2018 Groupon, Inc
 * Copyright 2014-2018 The Billing Project, LLC
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

package org.killbill.billing.jaxrs;

import javax.inject.Inject;

import org.killbill.billing.beatrix.integration.db.TestDBRouterAPI;
import org.killbill.billing.jaxrs.resources.TestDBRouterResource;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDBRouterResources extends TestJaxrsBase {

    @Inject
    private TestDBRouterAPI testDBRouterAPI;

    @Inject
    private TestDBRouterResource testDBRouterResource;

    @Test(groups = "slow")
    public void testJaxRSAoPRouting() throws Exception {
        testDBRouterResource.doChainedROROCalls();
        assertNbCalls(0, 2);

        testDBRouterResource.doChainedRWROCalls();
        assertNbCalls(2, 0);

        testDBRouterResource.doChainedRORWROCalls();
        assertNbCalls(2, 1);
    }

    private void assertNbCalls(final int expectedNbRWCalls, final int expectedNbROCalls) {
        assertEquals(testDBRouterAPI.getNbRWCalls(), expectedNbRWCalls);
        assertEquals(testDBRouterAPI.getNbRoCalls(), expectedNbROCalls);
    }
}
