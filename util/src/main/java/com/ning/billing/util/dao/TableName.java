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
    ACCOUNT_HISTORY("account_history"),
    ACCOUNT("accounts", ObjectType.ACCOUNT, ACCOUNT_HISTORY),
    ACCOUNT_EMAIL_HISTORY("account_email_history"),
    ACCOUNT_EMAIL("account_emails", ObjectType.ACCOUNT_EMAIL, ACCOUNT_EMAIL_HISTORY),
    BUNDLES("bundles", ObjectType.BUNDLE),
    CUSTOM_FIELD_HISTORY("custom_field_history"),
    CUSTOM_FIELD("custom_fields", CUSTOM_FIELD_HISTORY),
    INVOICE_ITEMS("invoice_items", ObjectType.INVOICE_ITEM),
    INVOICE_PAYMENTS("invoice_payments", ObjectType.INVOICE_PAYMENT),
    INVOICES("invoices", ObjectType.INVOICE),
    PAYMENT_ATTEMPT_HISTORY("payment_attempt_history"),
    PAYMENT_ATTEMPTS("payment_attempts", PAYMENT_ATTEMPT_HISTORY),
    PAYMENT_HISTORY("payment_history"),
    PAYMENTS("payments", ObjectType.PAYMENT, PAYMENT_HISTORY),
    PAYMENT_METHOD_HISTORY("payment_method_history"),
    PAYMENT_METHODS("payment_methods", ObjectType.PAYMENT_METHOD, PAYMENT_METHOD_HISTORY),
    SUBSCRIPTIONS("subscriptions", ObjectType.SUBSCRIPTION),
    SUBSCRIPTION_EVENTS("subscription_events", ObjectType.SUBSCRIPTION_EVENT),
    REFUND_HISTORY("refund_history"),
    REFUNDS("refunds", ObjectType.REFUND, REFUND_HISTORY),
    TAG_DEFINITIONS("tag_definitions", ObjectType.TAG_DEFINITION),
    TAG_HISTORY("tag_history"),
    TENANT("tenants", ObjectType.TENANT),
    TAG("tags", TAG_HISTORY);

    private final String tableName;
    private final ObjectType objectType;
    private final TableName historyTableName;

    TableName(final String tableName, @Nullable final ObjectType objectType, @Nullable final TableName historyTableName) {
        this.tableName = tableName;
        this.objectType = objectType;
        this.historyTableName = historyTableName;
    }

    TableName(final String tableName, @Nullable final ObjectType objectType) {
        this(tableName, objectType, null);
    }

    TableName(final String tableName, @Nullable final TableName historyTableName) {
        this(tableName, null, historyTableName);
    }

    TableName(final String tableName) {
        this(tableName, null, null);
    }

    public String getTableName() {
        return tableName;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public TableName getHistoryTableName() {
        return historyTableName;
    }

    public boolean hasHistoryTable() {
        return historyTableName != null;
    }
}
