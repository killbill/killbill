/*
 * Copyright 2010-2013 Ning, Inc.
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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.catalog.api.TimeUnit;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.model.Catalogs;
import org.killbill.billing.client.model.gen.Catalog;
import org.killbill.billing.client.model.gen.Plan;
import org.killbill.billing.client.model.gen.PlanDetail;
import org.killbill.billing.client.model.gen.Product;
import org.killbill.billing.client.model.gen.SimplePlan;
import org.killbill.billing.client.model.gen.Usage;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class TestCatalog extends TestJaxrsBase {


    @Test(groups = "slow", description = "Upload and retrieve a per tenant catalog")
    public void testMultiTenantCatalog() throws Exception {
        String catalog = uploadTenantCatalog("SpyCarBasic.xml", true);
        Assert.assertNotNull(catalog);
        //
        // We can't deserialize the VersionedCatalog using our JAXB models because it contains several
        // Standalone catalog and ids (JAXB name) are not unique across the various catalogs so deserialization would fail
        //
    }

    @Test(groups = "slow")
    public void testUploadAndFetchUsageCatlog() throws Exception {
        String catalog = uploadTenantCatalog("UsageExperimental.xml", true);
        Assert.assertNotNull(catalog);
    }

    @Test(groups = "slow")
    public void testUploadWithErrors() throws Exception {
        uploadTenantCatalog("SpyCarBasic.xml", false);

        // Retry to upload same version
        try {
            uploadTenantCatalog("SpyCarBasic.xml", false);
            Assert.fail("Uploading same version should fail");
        } catch (KillBillClientException e) {
            Assert.assertTrue(e.getMessage().startsWith("Invalid catalog for tenant : "));
        }

        // Try to upload another version with an invalid name (different than orignal name)
        try {
            uploadTenantCatalog("SpyCarBasicInvalidName.xml", false);
            Assert.fail("Uploading same version should fail");
        } catch (KillBillClientException e) {
            Assert.assertTrue(e.getMessage().startsWith("Invalid catalog for tenant : "));
        }

        String catalog = catalogApi.getCatalogXml(null, null, requestOptions);
        Assert.assertNotNull(catalog);
    }

    @Test(groups = "slow", description = "Can retrieve a json version of the catalog")
    public void testCatalog() throws Exception {
        final Set<String> allBasePlans = new HashSet<String>();

        final Catalogs catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);

        Assert.assertEquals(catalogsJson.get(0).getName(), "Firearms");
        Assert.assertEquals(catalogsJson.get(0).getEffectiveDate().toLocalDate(), new LocalDate("2011-01-01"));
        Assert.assertEquals(catalogsJson.get(0).getCurrencies().size(), 3);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 13);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 7);

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
            final List<PlanDetail> availableAddons = catalogApi.getAvailableAddons(productJson.getName(), null, null, requestOptions);
            final Set<String> availableAddonsNames = new HashSet<String>();
            for (final PlanDetail planDetailJson : availableAddons) {
                availableAddonsNames.add(planDetailJson.getProduct());
            }
            Assert.assertEquals(availableAddonsNames, new HashSet<String>(productJson.getAvailable()));
        }

        // Verify base plans endpoint
        final List<PlanDetail> basePlans = catalogApi.getAvailableBasePlans(null, requestOptions);
        final Set<String> foundBasePlans = new HashSet<String>();
        for (final PlanDetail planDetailJson : basePlans) {
            foundBasePlans.add(planDetailJson.getPlan());
        }
        Assert.assertEquals(foundBasePlans, allBasePlans);
    }

    @Test(groups = "slow", description = "Try to retrieve catalog with an effective date in the past")
    public void testCatalogWithEffectiveDateInThePast() throws Exception {
        final List<Catalog> catalogsJson = catalogApi.getCatalogJson(DateTime.parse("2008-01-01"), null, requestOptions);
        // We expect to see our catalogTest.xml (date in the past returns the first version. See #760
        Assert.assertEquals(catalogsJson.size(), 1);
    }

    @Test(groups = "slow", description = "Can create a simple Plan into a per-tenant catalog")
    public void testAddSimplePlan() throws Exception {

        catalogApi.addSimplePlan(new SimplePlan("foo-monthly", "Foo", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);
        List<Catalog> catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(), "Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().get(0), "foo-monthly");

        catalogApi.addSimplePlan(new SimplePlan("foo-annual", "Foo", ProductCategory.BASE, Currency.USD, new BigDecimal("100.00"), BillingPeriod.ANNUAL, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);

        catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(), "Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 2);

    }

    @Test(groups = "slow", description = "Upload and retrieve a per plugin payment state machine config")
    public void testAddSimplePlanWithoutKBDefault() throws Exception {
        // Create another tenant initialized with no default catalog,...
        createTenant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), false);

        // Verify the template catalog is not returned
        List<Catalog> catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 0);

        catalogApi.addSimplePlan(new SimplePlan("foo-monthly", "Foo", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);
        catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(), "Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().get(0), "foo-monthly");

        catalogApi.addSimplePlan(new SimplePlan("foo-annual", "Foo", ProductCategory.BASE, Currency.USD, new BigDecimal("100.00"), BillingPeriod.ANNUAL, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);

        catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getProducts().get(0).getName(), "Foo");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().size(), 1);
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getName(), "DEFAULT");
        Assert.assertEquals(catalogsJson.get(0).getPriceLists().get(0).getPlans().size(), 2);
    }

    @Test(groups = "slow")
    public void testCatalogDeletionInTestMode() throws Exception {

        catalogApi.addSimplePlan(new SimplePlan("something-monthly", "Something", ProductCategory.BASE, Currency.USD, BigDecimal.TEN, BillingPeriod.MONTHLY, 0, TimeUnit.UNLIMITED, ImmutableList.<String>of()), requestOptions);
        List<Catalog> catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 1);

        catalogApi.deleteCatalog(requestOptions);

        // Verify that we see no catalog -- and in particular not the KB default catalog
        catalogsJson = catalogApi.getCatalogJson(null, null, requestOptions);
        Assert.assertEquals(catalogsJson.size(), 0);

    }

    @Test(groups = "slow")
    public  void testGetCatalogVersions() throws Exception {
        uploadTenantCatalog("SpyCarBasic.xml", false);
        List<DateTime> versions = catalogApi.getCatalogVersions(null, requestOptions);
        Assert.assertEquals(1, versions.size());
        Assert.assertEquals(versions.get(0).compareTo(DateTime.parse("2013-02-08T00:00:00+00:00")), 0);
    }
}
