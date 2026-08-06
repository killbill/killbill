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

package org.killbill.billing.invoice.api;

public class BrandInfo {

    private String textColor;
    private String tableBorderColor;
    private String tableHeadingBgColor;
    private String tableHeadingTextColor;

    public BrandInfo() {
    }

    public BrandInfo(final String textColor, final String tableBorderColor, final String tableHeadingTextColor, final String tableHeadingBgColor) {
        this.textColor = textColor;
        this.tableBorderColor = tableBorderColor;
        this.tableHeadingTextColor = tableHeadingTextColor;
        this.tableHeadingBgColor = tableHeadingBgColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public String getTableBorderColor() {
        return tableBorderColor;
    }

    public String getTableHeadingBgColor() {
        return tableHeadingBgColor;
    }

    public String getTableHeadingTextColor() {
        return tableHeadingTextColor;
    }

}
