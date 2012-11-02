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

import com.ning.billing.ObjectType;
import com.ning.billing.account.api.Account;
import com.ning.billing.account.api.AccountEmail;
import com.ning.billing.entitlement.api.user.Subscription;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.payment.api.Payment;
import com.ning.billing.payment.api.Payment.PaymentAttempt;
import com.ning.billing.payment.api.PaymentMethod;
import com.ning.billing.payment.api.Refund;
import com.ning.billing.tenant.api.Tenant;
import com.ning.billing.util.customfield.CustomField;
import com.ning.billing.util.entity.Entity;
import com.ning.billing.util.tag.Tag;
import com.ning.billing.util.tag.TagDefinition;

/**
 * Map table names to entity object types and classes, and history tables (if exists)
 */
public enum TableName {
    ACCOUNT_HISTORY("account_history"),
    ACCOUNT("accounts", ObjectType.ACCOUNT, Account.class, ACCOUNT_HISTORY),
    ACCOUNT_EMAIL_HISTORY("account_email_history"),
    ACCOUNT_EMAIL("account_emails", ObjectType.ACCOUNT_EMAIL, AccountEmail.class, ACCOUNT_EMAIL_HISTORY),
    BUNDLES("bundles", ObjectType.BUNDLE, SubscriptionBundle.class),
    CUSTOM_FIELD_HISTORY("custom_field_history"),
    CUSTOM_FIELD("custom_fields", ObjectType.CUSTOM_FIELD, CustomField.class, CUSTOM_FIELD_HISTORY),
    INVOICE_ITEMS("invoice_items", ObjectType.INVOICE_ITEM, InvoiceItem.class),
    INVOICE_PAYMENTS("invoice_payments", ObjectType.INVOICE_PAYMENT, InvoicePayment.class),
    INVOICES("invoices", ObjectType.INVOICE, Invoice.class),
    PAYMENT_ATTEMPT_HISTORY("payment_attempt_history"),
    PAYMENT_ATTEMPTS("payment_attempts", ObjectType.PAYMENT_ATTEMPT, PaymentAttempt.class, PAYMENT_ATTEMPT_HISTORY),
    PAYMENT_HISTORY("payment_history"),
    PAYMENTS("payments", ObjectType.PAYMENT, Payment.class, PAYMENT_HISTORY),
    PAYMENT_METHOD_HISTORY("payment_method_history"),
    PAYMENT_METHODS("payment_methods", ObjectType.PAYMENT_METHOD, PaymentMethod.class, PAYMENT_METHOD_HISTORY),
    SUBSCRIPTIONS("subscriptions", ObjectType.SUBSCRIPTION, Subscription.class),
    // TODO - entity class?
    SUBSCRIPTION_EVENTS("subscription_events", ObjectType.SUBSCRIPTION_EVENT, null),
    REFUND_HISTORY("refund_history"),
    REFUNDS("refunds", ObjectType.REFUND, Refund.class, REFUND_HISTORY),
    TAG_DEFINITION_HISTORY("tag_definition_history"),
    TAG_DEFINITIONS("tag_definitions", ObjectType.TAG_DEFINITION, TagDefinition.class, TAG_DEFINITION_HISTORY),
    TAG_HISTORY("tag_history"),
    TENANT("tenants", ObjectType.TENANT, Tenant.class),
    TAG("tags", ObjectType.TAG, Tag.class, TAG_HISTORY);

    private final String tableName;
    private final ObjectType objectType;
    private final Class<? extends Entity> entityClass;
    private final TableName historyTableName;

    TableName(final String tableName, @Nullable final ObjectType objectType, @Nullable final Class<? extends Entity> entityClass, @Nullable final TableName historyTableName) {
        this.tableName = tableName;
        this.objectType = objectType;
        this.entityClass = entityClass;
        this.historyTableName = historyTableName;
    }

    TableName(final String tableName, final ObjectType objectType, @Nullable final Class<? extends Entity> entityClass) {
        this(tableName, objectType, entityClass, null);
    }

    TableName(final String tableName) {
        this(tableName, null, null, null);
    }

    public static TableName fromObjectType(final ObjectType objectType) {
        for (final TableName tableName : values()) {
            if (tableName.getObjectType() != null && tableName.getObjectType().equals(objectType)) {
                return tableName;
            }
        }
        return null;
    }

    public static TableName fromEntityClass(final Class<? extends Entity> entityClass) {
        for (final TableName tableName : values()) {
            if (tableName.getEntityClass() != null && tableName.getEntityClass().equals(entityClass)) {
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

    public Class<? extends Entity> getEntityClass() {
        return entityClass;
    }

    public boolean hasHistoryTable() {
        return historyTableName != null;
    }
}
