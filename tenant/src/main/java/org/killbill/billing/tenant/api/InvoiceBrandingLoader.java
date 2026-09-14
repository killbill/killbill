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

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.invoice.api.BrandInfo;
import org.killbill.billing.invoice.api.CompanyInfo;
import org.killbill.billing.invoice.api.LogoInfo;
import org.killbill.billing.tenant.dao.TenantDao;
import org.killbill.billing.util.jackson.ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;

class InvoiceBrandingLoader {

    private static final Map<String, String> DEFAULT_PROPERTIES = Map.of(
            "companyName", "Killbill, Inc.",
            "companyAddress", "P.O. Box 1234",
            "companyCityProvincePostalCode", "Springfield",
            "companyCountry", "USA",
            "companyUrl", "https://killbill.io",
            "textColor", "#555555",
            "tableBorderColor", "#d4bdd6",
            "tableHeadingBgColor", "#f0f0f0",
            "tableHeadingTextColor", "#444444",
            "logo", "data:image/svg+xml;base64,PHN2ZwogIHdpZHRoPSIxMDAiCiAgaGVpZ2h0PSIxMDAiCiAgdmlld0JveD0iMCAwIDM2IDM2IgogIGZpbGw9Im5vbmUiCiAgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIgo+CiAgPHBhdGggZD0iTTguODU2NyAxMy42MjQ0TDE3LjA4OCAxNi4yMTc1TDE5Ljc5OTkgMjAuN0w3LjEwOTg0IDIwLjI3NjVMNS43NDk2OSAxNy43MTIyQzQuNTc0NTYgMTUuNDk2OCA2LjU0OTk4IDEyLjg5NzggOC44NTY3IDEzLjYyNDRaIiBmaWxsPSJ1cmwoI3BhaW50MCkiLz4KICA8cGF0aCBkPSJNMi43NDI0IDE4LjkwMDlMMjMuMTYxMSAxOS40NDAzTDMxLjE3NTcgMzIuNjU1NEMzMi4wMzYgMzQuMDczOCAzMS4wODQzIDM1Ljk0NTIgMjkuNTAwMiAzNS45NTA1TDE0LjcwMjcgMzUuOTk5OUMxMi44ODEzIDM2LjAwNTkgMTEuMTM2OCAzNS4yMTgzIDkuODczMjkgMzMuODE5NkwwLjc1Mjg0OCAyMy43MjI1Qy0wLjkwMjg3NCAyMS44ODk1IDAuMzU2MzI5IDE4LjgzNzggMi43NDI0IDE4LjkwMDlaIiBmaWxsPSJ1cmwoI3BhaW50MSkiLz4KICA8cGF0aCBkPSJNMjAuNDUzOCAwLjkxOTQzNEwxNS42NDA2IDcuNzQ5M0wyMy4xMzU5IDExLjM2MzFMMjQuODQ1MyA0Ljg5MTkxTDIwLjQ1MzggMC45MTk0MzRaIiBmaWxsPSIjRkZCRDMxIi8+CiAgPHBhdGggZD0iTTI3LjQ3OTIgMzQuODQ2M0wxNS4zNzI2IDEzLjM5NzdDMTQuMzQ4MiAxMS41ODMgMTQuNDc2NSA5LjM0Mzk2IDE1LjcwMTUgNy42NTU5N0MxNi40MDgyIDYuNjgyMTggMTcuODEyNyA2LjUzNjc5IDE4LjcwNjkgNy4zNDQ4N0wzNC4xNjQ1IDIxLjMxMzhDMzYuMTU3NiAyMy4xMTQ5IDM2LjU4MDUgMjYuMDY2IDM1LjE3MjUgMjguMzQ2NkwzMS4xMTYyIDM0LjkxNjhDMzAuMjc0OSAzNi4yNzk0IDI4LjI2NjEgMzYuMjQwNSAyNy40NzkyIDM0Ljg0NjNaIiBmaWxsPSJ1cmwoI3BhaW50MikiLz4KICA8cGF0aCBkPSJNMzIuMDU5IDExLjA3MDRMMjEuMDM2IDMuODM4ODJDMTkuMTI3NiAyLjU4Njg3IDIwLjMxNDggLTAuMzYwNDgxIDIyLjU2NzYgMC4wMzY0Njc4TDI3Ljg0OTEgMC45NjcxMTVDMjkuMTI2OSAxLjE5MjI3IDMwLjE3NTQgMi4wOTc3NCAzMC41NzYyIDMuMzIyMDNMMzIuOTAzNyAxMC40MzMyQzMzLjA2OCAxMC45MzUxIDMyLjUwMjcgMTEuMzYxNSAzMi4wNTkgMTEuMDcwNFoiIGZpbGw9InVybCgjcGFpbnQzKSIvPgoKICA8ZGVmcz4KICAgIDxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQwIiB4MT0iNi4wODk4MyIgeTE9IjE0LjU0MjkiIHgyPSIxOC42MDQ2IiB5Mj0iMjAuOTU0MiIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiPgogICAgICA8c3RvcCBzdG9wLWNvbG9yPSIjNDAwRjgyIi8+CiAgICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzY0MjZCNyIvPgogICAgPC9saW5lYXJHcmFkaWVudD4KICAgIDxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQxIiB4MT0iMCIgeTE9IjIwLjY4OTEiIHgyPSIzMC4xMDc0IiB5Mj0iMzQuNDQ0NSIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiPgogICAgICA8c3RvcCBzdG9wLWNvbG9yPSIjNjEyMkI1Ii8+CiAgICAgIDxzdG9wIG9mZnNldD0iMC45MjQ2ODQiIHN0b3AtY29sb3I9IiM5RDY2RDkiLz4KICAgIDwvbGluZWFyR3JhZGllbnQ+CiAgICA8bGluZWFyR3JhZGllbnQgaWQ9InBhaW50MiIgeDE9IjE2LjMwODQiIHkxPSI2LjgyMTI5IiB4Mj0iMjUuMjM0NSIgeTI9IjMxLjEwNzUiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIj4KICAgICAgPHN0b3Agb2Zmc2V0PSIwLjA4IiBzdG9wLWNvbG9yPSIjRkZCRDMxIi8+CiAgICAgIDxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzlCNjNEOCIvPgogICAgPC9saW5lYXJHcmFkaWVudD4KICAgIDxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQzIiB4MT0iMjAuNzcwMiIgeTE9IjEuNDA3MyIgeDI9IjMyLjY4OTIiIHkyPSI5LjQ3NzMyIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+CiAgICAgIDxzdG9wIHN0b3AtY29sb3I9IiNGQUM0MTUiLz4KICAgICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjRkRFMTcyIi8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KPC9zdmc+");
    private final TenantDao tenantDao;

    @Inject
    InvoiceBrandingLoader(final TenantDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    @SafeVarargs
    private static <T> T firstNonNull(final T... values) {
        for (final T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    CompanyInfo getInvoiceTemplateCompanyInfo(final InternalTenantContext tenantContext) {
        String value = getValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_COMPANY_INFO, "invoice template company info", tenantContext);
        if (value == null) {
            value = getValue(InvoiceBrandingTenantKey.COMPANY_INFO, "company info", tenantContext);
        }
        return deserializeJson(value, CompanyInfo.class, new CompanyInfo(DEFAULT_PROPERTIES.get("companyName"), DEFAULT_PROPERTIES.get("companyAddress"), DEFAULT_PROPERTIES.get("companyCityProvincePostalCode"), DEFAULT_PROPERTIES.get("companyCountry"), DEFAULT_PROPERTIES.get("companyUrl")));
    }

    LogoInfo getInvoiceTemplateLogoInfo(final InternalTenantContext tenantContext) {
        String value = getValue(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_LOGO_INFO, "invoice template logo", tenantContext);
        if (value == null) {
            value = getValue(InvoiceBrandingTenantKey.LOGO_INFO, "logo", tenantContext);
        }
        return deserializeJson(value, LogoInfo.class, new LogoInfo(DEFAULT_PROPERTIES.get("logo")));
    }

    BrandInfo getInvoiceTemplateBrandInfo(final InternalTenantContext tenantContext) {
        final BrandInfo invoiceTemplate = loadBrandInfo(InvoiceBrandingTenantKey.INVOICE_TEMPLATE_BRAND_INFO, "invoice template brand info", tenantContext);
        final BrandInfo global = loadBrandInfo(InvoiceBrandingTenantKey.BRAND_INFO, "brand info", tenantContext);
        return mergeBrandInfo(invoiceTemplate, global);
    }

    private String getValue(final InvoiceBrandingTenantKey key,
                            final String description,
                            final InternalTenantContext tenantContext) {
        final List<String> values = tenantDao.getTenantValueForKey(key.toString(), tenantContext);
        return getUniqueValue(values, description, tenantContext);
    }

    private BrandInfo loadBrandInfo(final InvoiceBrandingTenantKey key,
                                    final String description,
                                    final InternalTenantContext tenantContext) {
        return deserializeJson(getValue(key, description, tenantContext), BrandInfo.class, null);
    }

    private BrandInfo mergeBrandInfo(final BrandInfo invoiceTemplate,
                                     final BrandInfo global) {
        return new BrandInfo(
                firstNonNull(
                        invoiceTemplate == null ? null : invoiceTemplate.getTextColor(),
                        global == null ? null : global.getTextColor(),
                        DEFAULT_PROPERTIES.get("textColor")),

                firstNonNull(
                        invoiceTemplate == null ? null : invoiceTemplate.getTableBorderColor(),
                        global == null ? null : global.getTableBorderColor(),
                        DEFAULT_PROPERTIES.get("tableBorderColor")),

                firstNonNull(
                        invoiceTemplate == null ? null : invoiceTemplate.getTableHeadingTextColor(),
                        global == null ? null : global.getTableHeadingTextColor(),
                        DEFAULT_PROPERTIES.get("tableHeadingTextColor")),

                firstNonNull(
                        invoiceTemplate == null ? null : invoiceTemplate.getTableHeadingBgColor(),
                        global == null ? null : global.getTableHeadingBgColor(),
                        DEFAULT_PROPERTIES.get("tableHeadingBgColor")));
    }

    private String getUniqueValue(final List<String> values,
                                  final String msg,
                                  final InternalTenantContext tenantContext) {
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalStateException(String.format(
                    "Unexpected number of values %d for %s and tenant %d",
                    values.size(), msg, tenantContext.getTenantRecordId()));
        }
        return values.get(0);
    }

    private <T> T deserializeJson(final String value,
                                  final Class<T> clazz,
                                  final T defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        final ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(value, clazz);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to deserialize JSON into " + clazz.getSimpleName(), e);
        }
    }
}