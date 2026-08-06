/*
 * Copyright 2020-2026 Equinix, Inc
 * Copyright 2014-2026 The Billing Project, LLC
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

package org.killbill.billing.tenant.api;

import org.killbill.billing.invoice.api.BrandInfo;
import org.killbill.billing.invoice.api.CompanyInfo;
import org.killbill.billing.tenant.TenantTestSuiteWithEmbeddedDb;
import org.killbill.billing.util.jackson.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestInvoiceBrandingLoader extends TenantTestSuiteWithEmbeddedDb {

    private InvoiceBrandingLoader loader;
    private ObjectMapper objectMapper;

    @BeforeMethod(groups = "slow")
    public void beforeMethod() throws Exception {
        super.beforeMethod();
        loader = new InvoiceBrandingLoader(tenantDao);
        objectMapper = new ObjectMapper();
    }

    @Test(groups = "slow")
    public void testDefaultsReturnedWhenNoConfigurationExists() {

        final BrandInfo result = loader.getInvoiceTemplateBrandInfo(internalCallContext);

        Assert.assertEquals(result.getTextColor(), "#555555");
        Assert.assertEquals(result.getTableBorderColor(), "#d4bdd6");
        Assert.assertEquals(result.getTableHeadingTextColor(), "#444444");
        Assert.assertEquals(result.getTableHeadingBgColor(), "#f0f0f0");
    }

    @Test(groups = "slow")
    public void testGetInvoiceTemplateCompanyInfoFallsBackToGlobal() throws Exception {

        final CompanyInfo companyInfo = new CompanyInfo("Kill Bill", "123 Main St", "Palo Alto, CA", "USA", "https://killbill.io");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.COMPANY_INFO.toString(), objectMapper.writeValueAsString(companyInfo), true, internalCallContext);

        final CompanyInfo result = loader.getInvoiceTemplateCompanyInfo(internalCallContext);

        Assert.assertEquals(result.getCompanyName(), companyInfo.getCompanyName());
        Assert.assertEquals(result.getCompanyAddress(), companyInfo.getCompanyAddress());
        Assert.assertEquals(result.getCompanyCityProvincePostalCode(), companyInfo.getCompanyCityProvincePostalCode());
        Assert.assertEquals(result.getCompanyCountry(), companyInfo.getCompanyCountry());
        Assert.assertEquals(result.getCompanyUrl(), companyInfo.getCompanyUrl());
    }

    @Test(groups = "slow")
    public void testInvoiceTemplateCompanyInfoOverridesGlobal() throws Exception {

        final CompanyInfo globalCompanyInfo = new CompanyInfo("Global", "addr", "city", "country", "url");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.COMPANY_INFO.toString(), objectMapper.writeValueAsString(globalCompanyInfo), true, internalCallContext);

        final CompanyInfo invoiceTemplateCompanyInfo = new CompanyInfo("Invoice", "addr2", "city2", "country2", "url2");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_COMPANY_INFO.toString(), objectMapper.writeValueAsString(invoiceTemplateCompanyInfo), true, internalCallContext);

        final CompanyInfo result = loader.getInvoiceTemplateCompanyInfo(internalCallContext);

        Assert.assertEquals(result.getCompanyName(), invoiceTemplateCompanyInfo.getCompanyName());
        Assert.assertEquals(result.getCompanyAddress(), invoiceTemplateCompanyInfo.getCompanyAddress());
        Assert.assertEquals(result.getCompanyCityProvincePostalCode(), invoiceTemplateCompanyInfo.getCompanyCityProvincePostalCode());
        Assert.assertEquals(result.getCompanyCountry(), invoiceTemplateCompanyInfo.getCompanyCountry());
        Assert.assertEquals(result.getCompanyUrl(), invoiceTemplateCompanyInfo.getCompanyUrl());

    }

    @Test(groups = "slow")
    public void testBrandInfoMergesInvoiceTemplateGlobalAndDefaults() throws Exception {

        final BrandInfo globalBrandInfo = new BrandInfo(null, null, null, "#111111");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.BRAND_INFO.toString(), objectMapper.writeValueAsString(globalBrandInfo), true, internalCallContext);

        // override all the properties except tableHeadingBgColor
        final BrandInfo invoiceTemplateBrandInfo = new BrandInfo("#aaaaaa", "#bbbbbb", "#cccccc", null);
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_BRAND_INFO.toString(), objectMapper.writeValueAsString(invoiceTemplateBrandInfo), true, internalCallContext);

        final BrandInfo result = loader.getInvoiceTemplateBrandInfo(internalCallContext);

        Assert.assertEquals(result.getTextColor(), invoiceTemplateBrandInfo.getTextColor());
        Assert.assertEquals(result.getTableBorderColor(), invoiceTemplateBrandInfo.getTableBorderColor());
        Assert.assertEquals(result.getTableHeadingTextColor(), invoiceTemplateBrandInfo.getTableHeadingTextColor());
        Assert.assertEquals(result.getTableHeadingBgColor(), globalBrandInfo.getTableHeadingBgColor());
    }

    @Test(groups = "slow")
    public void testBrandInfoUsesDefaultsWhenPartialInvoiceTemplateOverride() throws Exception {

        final BrandInfo globalBrandInfo = new BrandInfo(null, null, null, "#111111");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.BRAND_INFO.toString(), objectMapper.writeValueAsString(globalBrandInfo), true, internalCallContext);

        final BrandInfo invoiceTemplateBrandInfo = new BrandInfo("#aaaaaa", null, null, null);
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_BRAND_INFO.toString(), objectMapper.writeValueAsString(invoiceTemplateBrandInfo), true, internalCallContext);

        final BrandInfo result = loader.getInvoiceTemplateBrandInfo(internalCallContext);

        Assert.assertEquals(result.getTextColor(), invoiceTemplateBrandInfo.getTextColor());
        Assert.assertEquals(result.getTableBorderColor(), "#d4bdd6"); //default
        Assert.assertEquals(result.getTableHeadingTextColor(), "#444444"); //default
        Assert.assertEquals(result.getTableHeadingBgColor(), globalBrandInfo.getTableHeadingBgColor());
    }

    @Test(groups = "slow")
    public void testBrandInfoFallsBackToGlobalAndDefaultsWhenInvoiceTemplateBrandInfoIsMissing() throws Exception {

        final BrandInfo globalBrandInfo = new BrandInfo(null, null, null, "#111111");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.BRAND_INFO.toString(), objectMapper.writeValueAsString(globalBrandInfo), true, internalCallContext);

        final BrandInfo result = loader.getInvoiceTemplateBrandInfo(internalCallContext);

        Assert.assertEquals(result.getTextColor(), "#555555"); //default
        Assert.assertEquals(result.getTableBorderColor(), "#d4bdd6"); //default
        Assert.assertEquals(result.getTableHeadingTextColor(), "#444444"); //default
        Assert.assertEquals(result.getTableHeadingBgColor(), globalBrandInfo.getTableHeadingBgColor());
    }

    @Test(groups = "slow")
    public void testBrandInfoInvoiceTemplateValuesOverrideGlobalValues() throws Exception {

        final BrandInfo globalBrandInfo = new BrandInfo(null, null, null, "#111111");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.BRAND_INFO.toString(), objectMapper.writeValueAsString(globalBrandInfo), true, internalCallContext);

        final BrandInfo invoiceTemplateBrandInfo = new BrandInfo("#aaaaaa", "#bbbbbb", "#cccccc", "#dddddd");
        tenantDao.addTenantKeyValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_BRAND_INFO.toString(), objectMapper.writeValueAsString(invoiceTemplateBrandInfo), true, internalCallContext);

        final BrandInfo result = loader.getInvoiceTemplateBrandInfo(internalCallContext);

        Assert.assertEquals(result.getTextColor(), invoiceTemplateBrandInfo.getTextColor());
        Assert.assertEquals(result.getTableBorderColor(), invoiceTemplateBrandInfo.getTableBorderColor());
        Assert.assertEquals(result.getTableHeadingTextColor(), invoiceTemplateBrandInfo.getTableHeadingTextColor());
        Assert.assertEquals(result.getTableHeadingBgColor(), invoiceTemplateBrandInfo.getTableHeadingBgColor());
    }

}
