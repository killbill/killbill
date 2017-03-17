/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
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

import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.model.Catalog;
import org.killbill.billing.client.model.Plan;
import org.killbill.billing.client.model.PlanDetail;
import org.killbill.billing.client.model.Product;
import org.killbill.billing.client.model.SimplePlan;
import org.killbill.billing.client.model.Tenant;
import org.killbill.billing.client.model.Usage;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TestCatalog extends TestJaxrsBase {

    @Test(groups = "slow", description = "Upload and retrieve a per tenant catalog")
    public void testMultiTenantCatalog() throws Exception {
        final String versionPath1 = Resources.getResource("SpyCarBasic.xml").getPath();
        killBillClient.uploadXMLCatalog(versionPath1, requestOptions);
        String catalog = killBillClient.getXMLCatalog(requestOptions);
        Assert.assertNotNull(catalog);
        //
        // We can't deserialize the VersionedCatalog using our JAXB models because it contains several
        // Standalone catalog and ids (JAXB name) are not unique across the various catalogs so deserialization would fail
        //
    }

    @Test(groups = "slow")
    public void testUploadAndFetchUsageCatlog() throws Exception {
        final String versionPath1 = Resources.getResource("UsageExperimental.xml").getPath();
        killBillClient.uploadXMLCatalog(versionPath1, requestOptions);
        String catalog = killBillClient.getXMLCatalog(requestOptions);
        Assert.assertNotNull(catalog);
    }


    @Test(groups = "slow")
    public void testUploadWithErrors() throws Exception {
        final String versionPath1 = Resources.getResource("SpyCarBasic.xml").getPath();
        killBillClient.uploadXMLCatalog(versionPath1, requestOptions);

        // Retry to upload same version
        try {
            killBillClient.uploadXMLCatalog(versionPath1, requestOptions);
            Assert.fail("Uploading same version should fail");
        } catch (KillBillClientException e) {
            Assert.assertTrue(e.getMessage().startsWith("Invalid catalog for tenant : "));
        }

        // Try to upload another version with an invalid name (different than orignal name)
        try {
            final String versionPath2 = Resources.getResource("SpyCarBasicInvalidName.xml").getPath();
            killBillClient.uploadXMLCatalog(versionPath2, requestOptions);
            Assert.fail("Uploading same version should fail");
        } catch (KillBillClientException e) {
            Assert.assertTrue(e.getMessage().startsWith("Invalid catalog for tenant : "));
        }

        String catalog = killBillClient.getXMLCatalog(requestOptions);
        Assert.assertNotNull(catalog);
    }

    @Test(groups = "slow", description = "Can retrieve a json version of the catalog")
    public void testCatalog() throws Exception {
        final Set<String> allBasePlans = new HashSet<String>();

        final List<Catalog> catalogsJson = killBillClient.getJSONCatalog(requestOptions);

        Assert.assertEquals(catalogsJson.get(0).getName(), "Firearms");
        Assert.assertEquals(catalogsJson.get(0).getEffectiveDate(), Date.valueOf("2011-01-01"));
        Assert.assertEquals(catalogsJson.get(0).getCurrencies().size(), 3);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 11);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 6);

        for (final Product productJson : catalogsJson.get(0).getProducts()) {
            if (!"BASE".equals(productJson.getType())) {
                Assert.assertEquals(productJson.getIncluded().size(), 0);
                Assert.assertEquals(productJson.getAvailable().size(), 0);
                continue;
            }

            // Save all plans for later (see below)
            for (final Plan planJson : productJson.getPlans()) {
                allBasePlans.add(planJson.getName());
            }

            // Verify Usage info in json
            if (productJson.getName().equals("Bullets")) {
                Assert.assertEquals(productJson.getPlans().get(0).getName(), "bullets-usage-in-arrear");
                Assert.assertEquals(productJson.getPlans().get(0).getPhases().get(0).getType(), "EVERGREEN");
                List<Usage> usages = productJson.getPlans().get(0).getPhases().get(0).getUsages();
                Assert.assertEquals(usages.get(0).getBillingPeriod(), "MONTHLY");
                Assert.assertEquals(usages.get(0).getTiers().get(0).getBlocks().get(0).getUnit(), "bullets");
                Assert.assertEquals(usages.get(0).getTiers().get(0).getBlocks().get(0).getSize(), "100.0");
                Assert.assertEquals(usages.get(0).getTiers().get(0).getBlocks().get(0).getPrices().get(0).getCurrency(), "USD");
                Assert.assertEquals(usages.get(0).getTiers().get(0).getBlocks().get(0).getPrices().get(0).getValue(), 2.95);
            }

            // Retrieve available products (addons) for that base product
            final List<PlanDetail> availableAddons = killBillClient.getAvailableAddons(productJson.getName(), requestOptions);
            final Set<String> availableAddonsNames = new HashSet<String>();
            for (final PlanDetail planDetailJson : availableAddons) {
                availableAddonsNames.add(planDetailJson.getProduct());
            }
            Assert.assertEquals(availableAddonsNames, new HashSet<String>(productJson.getAvailable()));
        }

        // Verify base plans endpoint
        final List<PlanDetail> basePlans = killBillClient.getBasePlans(requestOptions);
        final Set<String> foundBasePlans = new HashSet<String>();
        for (final PlanDetail planDetailJson : basePlans) {
            foundBasePlans.add(planDetailJson.getPlan());
        }
        Assert.assertEquals(foundBasePlans, allBasePlans);
    }

    @Test(groups = "slow", description = "Try to retrieve a json version of the catalog with an invalid date",
            expectedExceptions = KillBillClientException.class,
            expectedExceptionsMessageRegExp = "There is no catalog version that applies for the given date.*")
    public void testCatalogInvalidDate() throws Exception {
        final List<Catalog> catalogsJson = killBillClient.getJSONCatalog(DateTime.parse("2008-01-01"), requestOptions);
        Assert.fail();
    }

    @Test(groups = "slow", description = "Can create a simple Plan into a per-tenant catalog")
    public void testAddSimplePlan() throws Exception {

        killBillClient.addSimplePan(new SimplePlan("foo-monthly", "Foo", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);
        List<Catalog> catalogsJson = killBillClient.getJSONCatalog(requestOptions);
        Assert.assertEquals(catalogsJson.size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(),"Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().get(0), "foo-monthly");


        killBillClient.addSimplePan(new SimplePlan("foo-annual", "Foo", ProductCategory.BASE, Currency.USD, new BigDecimal("100.00"), BillingPeriod.ANNUAL, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);

        catalogsJson = killBillClient.getJSONCatalog(requestOptions);
        Assert.assertEquals(catalogsJson.size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(),"Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 2);

    }

    @Test(groups = "slow", description = "Upload and retrieve a per plugin payment state machine config")
    public void testAddSimplePlanWithoutKBDefault() throws Exception {
        // Create another tenant initialized with no default catalog,...
        final Tenant otherTenantNoKBDefault = new Tenant();
        otherTenantNoKBDefault.setApiKey(UUID.randomUUID().toString());
        otherTenantNoKBDefault.setApiSecret(UUID.randomUUID().toString());

        killBillClient.createTenant(otherTenantNoKBDefault, false, requestOptions);

        final RequestOptions requestOptionsOtherTenant = requestOptions.extend()
                                                                       .withTenantApiKey(otherTenantNoKBDefault.getApiKey())
                                                                       .withTenantApiSecret(otherTenantNoKBDefault.getApiSecret())
                                                                       .build();
        // Verify the template catalog is not returned
        List<Catalog> catalogsJson = killBillClient.getJSONCatalog(requestOptionsOtherTenant);
        Assert.assertEquals(catalogsJson.size(), 0);

        killBillClient.addSimplePan(new SimplePlan("foo-monthly", "Foo", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptionsOtherTenant);
        catalogsJson = killBillClient.getJSONCatalog(requestOptionsOtherTenant);
        Assert.assertEquals(catalogsJson.size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(),"Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().get(0), "foo-monthly");


        killBillClient.addSimplePan(new SimplePlan("foo-annual", "Foo", ProductCategory.BASE, Currency.USD, new BigDecimal("100.00"), BillingPeriod.ANNUAL, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptionsOtherTenant);

        catalogsJson = killBillClient.getJSONCatalog(requestOptionsOtherTenant);
        Assert.assertEquals(catalogsJson.size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(),"Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(),1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 2);
    }



}
