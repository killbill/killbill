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

import javax.annotation.Nullable;

public enum TableName {
    ACCOUNT("accounts", ObjectType.ACCOUNT),
    ACCOUNT_HISTORY("account_history", null),
    ACCOUNT_EMAIL_HISTORY("account_email_history", ObjectType.ACCOUNT_EMAIL),
    BUNDLES("bundles", ObjectType.BUNDLE),
    CUSTOM_FIELD_HISTORY("custom_field_history", null),
    INVOICE_ITEMS("invoice_items", ObjectType.INVOICE_ITEM),
    INVOICE_PAYMENTS("invoice_payments", ObjectType.INVOICE_PAYMENT),
    INVOICES("invoices", ObjectType.INVOICE),
    PAYMENT_ATTEMPTS("payment_attempts", null),
    PAYMENT_HISTORY("payment_history", null),
    PAYMENTS("payments", ObjectType.PAYMENT),
    PAYMENT_METHODS("payment_methods", ObjectType.PAYMENT_METHOD),
    SUBSCRIPTIONS("subscriptions", ObjectType.SUBSCRIPTION),
    SUBSCRIPTION_EVENTS("subscription_events", null),
    REFUNDS("refunds", ObjectType.REFUND),
    TAG_DEFINITIONS("tag_definitions", ObjectType.TAG_DEFINITION),
    TAG_HISTORY("tag_history", null);

    private final String tableName;
    private final ObjectType objectType;

    TableName(final String tableName, @Nullable final ObjectType objectType) {
        this.tableName = tableName;
        this.objectType = objectType;
    }

    public String getTableName() {
        return tableName;
    }

    public ObjectType getObjectType() {
        return objectType;
    }
}
