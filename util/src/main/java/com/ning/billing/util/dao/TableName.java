/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License")), you may not use this file except in compliance with the
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

package com.ning.billing.util.dao;

public enum TableName {
    ACCOUNT("accounts"),
    ACCOUNT_HISTORY("account_history"),
    ACCOUNT_EMAIL_HISTORY("account_email_history"),
    BUNDLES("bundles"),
    CUSTOM_FIELD_HISTORY("custom_field_history"),
    ENTITLEMENT_EVENTS("entitlement_events"),
    INVOICE_PAYMENTS("invoice_payments"),
    INVOICES("invoices"),
    PAYMENT_ATTEMPTS("payment_attempts"),
    PAYMENT_HISTORY("payment_history"),
    PAYMENTS("payments"),
    SUBSCRIPTIONS("subscriptions"),
    TAG_HISTORY("tag_history");
    
    private final String tableName;
    
    TableName(String tableName) {
        this.tableName = tableName;
    }
    
    public String getTableName() {
        return tableName;
    }
}
