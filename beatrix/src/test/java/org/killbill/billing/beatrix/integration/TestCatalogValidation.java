/*
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
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

package org.killbill.billing.beatrix.integration;

import java.io.IOException;

import org.joda.time.DateTime;
import org.killbill.billing.ErrorCode;
import org.killbill.billing.account.api.Account;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;

public class TestCatalogValidation extends TestIntegrationBase {

    private CallContext testCallContext;
    private Account account;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        if (hasFailed()) {
            return;
        }

        super.beforeMethod();

        // Setup tenant
        clock.setTime(new DateTime("2021-10-24T12:56:02"));
        testCallContext = setupTenant();

        // Setup account in right tenant
        account = setupAccount(testCallContext);
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1481")
    public void testUploadCatalog() throws Exception {
        uploadCatalog("CatalogValidation-v1.xml");
        try {
            uploadCatalog("CatalogValidation-v1-invalid.xml");
            Assert.fail("Catalog upload expected to fail");
        } catch (CatalogApiException cApiException) {
            assertEquals(cApiException.getCode(), ErrorCode.CAT_INVALID_FOR_TENANT.getCode());
        }
    }

    @Test(groups = "slow", description = "https://github.com/killbill/killbill/issues/1465")
    public void testUploadCatalogPlanValidation() throws Exception {

        try {
            // standard2-monthly: FIXEDTERM phase types must have a non-UNLIMITED Duration specified
            uploadCatalog("CatalogValidation-v2-invalid.xml");
            Assert.fail("Catalog upload expected to fail");
        } catch (CatalogApiException cApiException) {
            assertEquals(cApiException.getCode(), ErrorCode.CAT_INVALID_FOR_TENANT.getCode());
        }

        try {
            // standard3-monthly: EVERGREEN phase types must have an UNLIMITED Duration specified
            uploadCatalog("CatalogValidation-v3-invalid.xml");
            Assert.fail("Catalog upload expected to fail");
        } catch (CatalogApiException cApiException) {
            assertEquals(cApiException.getCode(), ErrorCode.CAT_INVALID_FOR_TENANT.getCode());
        }
    }

    private void uploadCatalog(final String name) throws CatalogApiException, IOException {
        catalogUserApi.uploadCatalog(Resources.asCharSource(Resources.getResource("catalogs/testCatalogValidation/" + name), Charsets.UTF_8).read(), testCallContext);
    }

}
