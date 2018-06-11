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

package org.killbill.billing.jaxrs.resources;

public interface JaxrsResource {

    String API_PREFIX = "";
    String API_VERSION = "/1.0";
    String API_POSTFIX = "/kb";

    String PREFIX = API_PREFIX + API_VERSION + API_POSTFIX;

    String TIMELINE = "timeline";
    String REGISTER_NOTIFICATION_CALLBACK = "registerNotificationCallback";
    String UPLOAD_PLUGIN_CONFIG = "uploadPluginConfig";
    String UPLOAD_PER_TENANT_CONFIG = "uploadPerTenantConfig";
    String UPLOAD_PLUGIN_PAYMENT_STATE_MACHINE_CONFIG = "uploadPluginPaymentStateMachineConfig";
    String USER_KEY_VALUE = "userKeyValue";
    String SEARCH = "search";

    /*
     * Multi-Tenancy headers
     */
    String HDR_API_KEY = "X-Killbill-ApiKey";
    String HDR_API_SECRET = "X-Killbill-ApiSecret";

    /*
     * Metadata Additional headers
     */
    String HDR_CREATED_BY = "X-Killbill-CreatedBy";
    String HDR_REASON = "X-Killbill-Reason";
    String HDR_COMMENT = "X-Killbill-Comment";
    String HDR_PAGINATION_CURRENT_OFFSET = "X-Killbill-Pagination-CurrentOffset";
    String HDR_PAGINATION_NEXT_OFFSET = "X-Killbill-Pagination-NextOffset";
    String HDR_PAGINATION_TOTAL_NB_RECORDS = "X-Killbill-Pagination-TotalNbRecords";
    String HDR_PAGINATION_MAX_NB_RECORDS = "X-Killbill-Pagination-MaxNbRecords";
    String HDR_PAGINATION_NEXT_PAGE_URI = "X-Killbill-Pagination-NextPageUri";

    /*
     * Patterns
     */
    String STRING_PATTERN = "\\w+";
    String UUID_PATTERN = "\\w+-\\w+-\\w+-\\w+-\\w+";
    String NUMBER_PATTERN = "[0-9]+";
    String ANYTHING_PATTERN = ".*";

    String PATH_PAYMENT_PLUGIN_NAME = "pluginName";

    /*
     * Query parameters
     */
    String QUERY_LOCAL_NODE_ONLY = "localNodeOnly";
    String QUERY_EXTERNAL_KEY = "externalKey";
    String QUERY_API_KEY = "apiKey";
    String QUERY_REQUESTED_DT = "requestedDate";
    String QUERY_PAYMENT_EXTERNAL_KEY = "paymentExternalKey";
    String QUERY_TRANSACTION_EXTERNAL_KEY = "transactionExternalKey";
    String QUERY_ENTITLEMENT_REQUESTED_DT = "entitlementDate";
    String QUERY_BILLING_REQUESTED_DT = "billingDate";
    String QUERY_CALL_COMPLETION = "callCompletion";
    String QUERY_USE_REQUESTED_DATE_FOR_BILLING = "useRequestedDateForBilling";
    String QUERY_CALL_TIMEOUT = "callTimeoutSec";
    String QUERY_TARGET_DATE = "targetDate";
    String QUERY_BILLING_POLICY = "billingPolicy";
    String QUERY_MIGRATED = "migrated";
    String QUERY_ENTITLEMENT_POLICY = "entitlementPolicy";
    String QUERY_SEARCH_OFFSET = "offset";
    String QUERY_SEARCH_LIMIT = "limit";
    String QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT = "effectiveFromDate";
    String QUERY_FORCE_NEW_BCD_WITH_PAST_EFFECTIVE_DATE = "forceNewBcdWithPastEffectiveDate";

    String QUERY_ACCOUNT_WITH_BALANCE = "accountWithBalance";
    String QUERY_ACCOUNT_WITH_BALANCE_AND_CBA = "accountWithBalanceAndCBA";
    String QUERY_ACCOUNT_TREAT_NULL_AS_RESET = "treatNullAsReset";

    String QUERY_ACCOUNT_ID = "accountId";

    String QUERY_CANCEL_ALL_SUBSCRIPTIONS = "cancelAllSubscriptions";
    String QUERY_WRITE_OFF_UNPAID_INVOICES = "writeOffUnpaidInvoices";
    String QUERY_ITEM_ADJUST_UNPAID_INVOICES = "itemAdjustUnpaidInvoices";
    String QUERY_REMOVE_FUTURE_NOTIFICATIONS = "removeFutureNotifications";

    String QUERY_BLOCKING_STATE_TYPES = "blockingStateTypes";
    String QUERY_BLOCKING_STATE_SVCS = "blockingStateSvcs";

    String QUERY_INVOICE_WITH_ITEMS = "withItems";
    String QUERY_WITH_MIGRATION_INVOICES = "withMigrationInvoices";
    String QUERY_UNPAID_INVOICES_ONLY = "unpaidInvoicesOnly";
    String QUERY_INCLUDE_VOIDED_INVOICES = "includeVoidedInvoices";
    String QUERY_INVOICE_WITH_CHILDREN_ITEMS = "withChildrenItems";

    String QUERY_PAYMENT_EXTERNAL = "externalPayment";
    String QUERY_PAYMENT_AMOUNT = "paymentAmount";
    String QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS = "withRefundsAndChargebacks";
    String QUERY_PAYMENT_PLUGIN_NAME = "pluginName";
    String QUERY_PAYMENT_METHOD_ID = "paymentMethodId";
    String QUERY_PAYMENT_CONTROL_PLUGIN_NAME = "controlPluginName";

    String QUERY_TENANT_USE_GLOBAL_DEFAULT = "useGlobalDefault";
    String QUERY_TAGS_INCLUDED_DELETED = "includedDeleted";

    String QUERY_TAG = "tagDef";
    String QUERY_CUSTOM_FIELD = "customField";

    String QUERY_OBJECT_TYPE = "objectType";

    String QUERY_PAYMENT_METHOD_PLUGIN_NAME = "pluginName";
    String QUERY_WITH_PLUGIN_INFO = "withPluginInfo";
    String QUERY_WITH_ATTEMPTS = "withAttempts";
    String QUERY_PAYMENT_METHOD_IS_DEFAULT = "isDefault";

    String QUERY_PAY_ALL_UNPAID_INVOICES = "payAllUnpaidInvoices";
    String QUERY_PAY_INVOICE = "payInvoice";

    String QUERY_PLUGIN_PROPERTY = "pluginProperty";

    String QUERY_START_DATE = "startDate";
    String QUERY_END_DATE = "endDate";

    String QUERY_DELETE_IF_EXISTS = "deleteIfExists";

    String QUERY_BUNDLES_FILTER = "bundlesFilter";

    String QUERY_BUNDLES_RENAME_KEY_IF_EXIST_UNUSED = "renameKeyIfExistsAndUnused";

    String QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF = "deleteDefaultPmWithAutoPayOff";

    String QUERY_FORCE_DEFAULT_PM_DELETION = "forceDefaultPmDeletion";

    String QUERY_AUDIT = "audit";

    String QUERY_BCD = "bcd";

    String QUERY_PARALLEL = "parallel";

    String QUERY_AUTO_COMMIT = "autoCommit";

    String QUERY_NOTIFICATION_CALLBACK = "cb";

    String PAGINATION = "pagination";

    String ADMIN = "admin";
    String ADMIN_PATH = PREFIX + "/" + ADMIN;

    String ACCOUNTS = "accounts";
    String ACCOUNTS_PATH = PREFIX + "/" + ACCOUNTS;

    String ANALYTICS = "analytics";
    String ANALYTICS_PATH = PREFIX + "/" + ANALYTICS;

    String BUNDLES = "bundles";
    String BUNDLES_PATH = PREFIX + "/" + BUNDLES;

    String SECURITY = "security";
    String SECURITY_PATH = PREFIX + "/" + SECURITY;

    String SUBSCRIPTIONS = "subscriptions";
    String SUBSCRIPTIONS_PATH = PREFIX + "/" + SUBSCRIPTIONS;

    String ENTITLEMENTS = "entitlements";
    String ENTITLEMENTS_PATH = PREFIX + "/" + ENTITLEMENTS;

    String TAG_DEFINITIONS = "tagDefinitions";
    String TAG_DEFINITIONS_PATH = PREFIX + "/" + TAG_DEFINITIONS;

    String INVOICES = "invoices";
    String INVOICES_PATH = PREFIX + "/" + INVOICES;

    String INVOICE_ITEMS = "invoiceItems";
    String INVOICES_ITEMS_PATH = PREFIX + "/" + INVOICE_ITEMS;

    String CHARGES = "charges";
    String CHARGES_PATH = PREFIX + "/" + INVOICES + "/" + CHARGES;

    String TAXES = "taxes";

    String PAYMENTS = "payments";
    String PAYMENTS_PATH = PREFIX + "/" + PAYMENTS;

    String PAYMENT_TRANSACTIONS = "paymentTransactions";
    String PAYMENT_TRANSACTIONS_PATH = PREFIX + "/" + PAYMENT_TRANSACTIONS;
    String ATTEMPTS = "attempts";

    String PAYMENT_GATEWAYS = "paymentGateways";
    String PAYMENT_GATEWAYS_PATH = PREFIX + "/" + PAYMENT_GATEWAYS;

    String REFUNDS = "refunds";

    String PAYMENT_METHODS = "paymentMethods";
    String PAYMENT_METHODS_PATH = PREFIX + "/" + PAYMENT_METHODS;
    String PAYMENT_METHODS_DEFAULT_PATH_POSTFIX = "setDefault";

    String CREDITS = "credits";
    String CREDITS_PATH = PREFIX + "/" + CREDITS;

    String INVOICE_PAYMENTS = "invoicePayments";
    String INVOICE_PAYMENTS_PATH = PREFIX + "/" + INVOICE_PAYMENTS;
    String DRY_RUN = "dryRun";

    String CHARGEBACKS = "chargebacks";
    String CHARGEBACKS_PATH = PREFIX + "/" + CHARGEBACKS;

    String CHARGEBACK_REVERSALS = "chargebackReversals";
    String CHARGEBACK_REVERSALS_PATH = PREFIX + "/" + CHARGEBACK_REVERSALS;

    String ALL_TAGS = "allTags";
    String TAGS = "tags";
    String TAGS_PATH = PREFIX + "/" + TAGS;

    String ALL_CUSTOM_FIELDS = "allCustomFields";
    String CUSTOM_FIELDS = "customFields";
    String CUSTOM_FIELDS_PATH = PREFIX + "/" + CUSTOM_FIELDS;

    String EMAILS = "emails";
    String EMAIL_NOTIFICATIONS = "emailNotifications";

    String CATALOG = "catalog";
    String CATALOG_PATH = PREFIX + "/" + CATALOG;

    String OVERDUE = "overdue";
    String OVERDUE_PATH = PREFIX + "/" + OVERDUE;

    String TENANTS = "tenants";
    String TENANTS_PATH = PREFIX + "/" + TENANTS;

    String USAGES = "usages";
    String USAGES_PATH = PREFIX + "/" + USAGES;

    String EXPORT = "export";
    String EXPORT_PATH = PREFIX + "/" + EXPORT;

    String PLUGINS_INFO = "pluginsInfo";
    String PLUGINS_INFO_PATH = PREFIX + "/" + PLUGINS_INFO;

    String NODES_INFO = "nodesInfo";
    String NODES_INFO_PATH = PREFIX + "/" + NODES_INFO;

    // No PREFIX here!
    String PLUGINS = "plugins";
    String PLUGINS_PATH = "/" + PLUGINS;

    String TEST = "test";
    String TEST_PATH = PREFIX + "/" + TEST;

    String CBA_REBALANCING = "cbaRebalancing";

    String UNDO_CHANGE_PLAN = "undoChangePlan";
    String UNDO_CANCEL = "uncancel";

    String PAUSE = "pause";
    String RESUME = "resume";
    String BLOCK = "block";
    String RENAME_KEY = "renameKey";

    String AUTHORIZATION = "authorization";
    String CAPTURE = "capture";

    String HOSTED = "hosted";
    String FORM = "form";
    String NOTIFICATION = "notification";

    String CANCEL_SCHEDULED_PAYMENT_TRANSACTION = "cancelScheduledPaymentTransaction";

    String INVOICE_TEMPLATE = "template";
    String INVOICE_MP_TEMPLATE = "manualPayTemplate";
    String INVOICE_TRANSLATION = "translation";
    String INVOICE_CATALOG_TRANSLATION = "catalogTranslation";
    String COMMIT_INVOICE = "commitInvoice";
    String VOID_INVOICE = "voidInvoice";

    String COMBO = "combo";
    String MIGRATION = "migration";

    String CHILDREN = "children";
    String BCD = "bcd";
    String TRANSFER_CREDIT = "transferCredit";

    String CACHE = "cache";
    String HEALTHCHECK = "healthcheck";

    String QUERY_INCLUDED_DELETED = "includedDeleted";
    String AUDIT_LOG = "auditLogs";
    String AUDIT_LOG_WITH_HISTORY = "auditLogsWithHistory";
}
