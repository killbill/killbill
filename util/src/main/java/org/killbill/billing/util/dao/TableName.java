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

package org.killbill.billing.util.dao;

import javax.annotation.Nullable;

import org.killbill.billing.ObjectType;

/**
 * Map table names to entity object types and classes, and history tables (if exists)
 */
public enum TableName {
    ACCOUNT_HISTORY("account_history"),
    ACCOUNT("accounts", ObjectType.ACCOUNT, ACCOUNT_HISTORY),
    ACCOUNT_EMAIL_HISTORY("account_email_history"),
    ACCOUNT_EMAIL("account_emails", ObjectType.ACCOUNT_EMAIL, ACCOUNT_EMAIL_HISTORY),
    BUNDLES("bundles", ObjectType.BUNDLE),
    BLOCKING_STATES("blocking_states", ObjectType.BLOCKING_STATES),
    CUSTOM_FIELD_HISTORY("custom_field_history"),
    CUSTOM_FIELD("custom_fields", ObjectType.CUSTOM_FIELD, CUSTOM_FIELD_HISTORY),
    INVOICE_ITEMS("invoice_items", ObjectType.INVOICE_ITEM),
    INVOICE_PAYMENTS("invoice_payments", ObjectType.INVOICE_PAYMENT),
    INVOICES("invoices", ObjectType.INVOICE),
    INVOICE_PARENT_CHILDREN("invoice_parent_children"),
    NODE_INFOS("node_infos"),
    PAYMENT_ATTEMPT_HISTORY("payment_attempt_history"),
    PAYMENT_ATTEMPTS("payment_attempts", ObjectType.PAYMENT_ATTEMPT, PAYMENT_ATTEMPT_HISTORY),
    PAYMENT_HISTORY("payment_history"),
    PAYMENTS("payments", ObjectType.PAYMENT, PAYMENT_HISTORY),
    PAYMENT_METHOD_HISTORY("payment_method_history"),
    PAYMENT_METHODS("payment_methods", ObjectType.PAYMENT_METHOD, PAYMENT_METHOD_HISTORY),
    PAYMENT_TRANSACTION_HISTORY("payment_transaction_history"),
    PAYMENT_TRANSACTIONS("payment_transactions", ObjectType.TRANSACTION, PAYMENT_TRANSACTION_HISTORY),
    SERVICE_BRODCASTS("service_broadcasts", ObjectType.SERVICE_BROADCAST),
    SUBSCRIPTIONS("subscriptions", ObjectType.SUBSCRIPTION),
    SUBSCRIPTION_EVENTS("subscription_events", ObjectType.SUBSCRIPTION_EVENT),
    TAG_DEFINITION_HISTORY("tag_definition_history"),
    TAG_DEFINITIONS("tag_definitions", ObjectType.TAG_DEFINITION, TAG_DEFINITION_HISTORY),
    TAG_HISTORY("tag_history"),
    TENANT("tenants", ObjectType.TENANT),
    TENANT_KVS("tenant_kvs", ObjectType.TENANT_KVS),
    TENANT_BROADCASTS("tenant_broadcasts"),
    TAG("tags", ObjectType.TAG, TAG_HISTORY),
    ROLLED_UP_USAGE("rolled_up_usage");

    private final String tableName;
    private final ObjectType objectType;
    private final TableName historyTableName;

    TableName(final String tableName, @Nullable final ObjectType objectType, @Nullable final TableName historyTableName) {
        this.tableName = tableName;
        this.objectType = objectType;
        this.historyTableName = historyTableName;
    }

    TableName(final String tableName, final ObjectType objectType) {
        this(tableName, objectType, null);
    }

    TableName(final String tableName) {
        this(tableName, null, null);
    }

    public static TableName fromObjectType(final ObjectType objectType) {
        for (final TableName tableName : values()) {
            if (tableName.getObjectType() != null && tableName.getObjectType().equals(objectType)) {
                return tableName;
            }
        }
        return null;
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
