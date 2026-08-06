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

public enum InvoiceBrandingTenantKey { //TODO_2283 - this is temporary for 0.24.x, on 0.26.x move this to TenantKV in killbill-api
    COMPANY_INFO,
    BRAND_INFO,
    LOGO_INFO,
    INVOICE_TEMPLATE_COMPANY_INFO,
    INVOICE_TEMPLATE_BRAND_INFO,
    INVOICE_TEMPLATE_LOGO_INFO,
    INVOICE_TEMPLATE_WITH_BRANDING
}
