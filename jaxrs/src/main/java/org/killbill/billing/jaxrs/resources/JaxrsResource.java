/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
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

    public static final String API_PREFIX = "";
    public static final String API_VERSION = "/1.0";
    public static final String API_POSTFIX = "/kb";

    public static final String PREFIX = API_PREFIX + API_VERSION + API_POSTFIX;

    public static final String TIMELINE = "timeline";
    public static final String REGISTER_NOTIFICATION_CALLBACK = "registerNotificationCallback";
    public static final String UPLOAD_PLUGIN_CONFIG = "uploadPluginConfig";
    public static final String UPLOAD_PER_TENANT_CONFIG = "uploadPerTenantConfig";
    public static final String UPLOAD_PLUGIN_PAYMENT_STATE_MACHINE_CONFIG = "uploadPluginPaymentStateMachineConfig";
    public static final String USER_KEY_VALUE = "userKeyValue";
    public static final String SEARCH = "search";

    public static final String PLUGIN_CONFIG = "pluginConfig";

    /*
     * Multi-Tenancy headers
     */
    public static String HDR_API_KEY = "X-Killbill-ApiKey";
    public static String HDR_API_SECRET = "X-Killbill-ApiSecret";

    /*
     * Metadata Additional headers
     */
    public static String HDR_CREATED_BY = "X-Killbill-CreatedBy";
    public static String HDR_REASON = "X-Killbill-Reason";
    public static String HDR_COMMENT = "X-Killbill-Comment";
    public static String HDR_PAGINATION_CURRENT_OFFSET = "X-Killbill-Pagination-CurrentOffset";
    public static String HDR_PAGINATION_NEXT_OFFSET = "X-Killbill-Pagination-NextOffset";
    public static String HDR_PAGINATION_TOTAL_NB_RECORDS = "X-Killbill-Pagination-TotalNbRecords";
    public static String HDR_PAGINATION_MAX_NB_RECORDS = "X-Killbill-Pagination-MaxNbRecords";
    public static String HDR_PAGINATION_NEXT_PAGE_URI = "X-Killbill-Pagination-NextPageUri";

    /*
     * Patterns
     */
    public static String STRING_PATTERN = "\\w+";
    public static String UUID_PATTERN = "\\w+-\\w+-\\w+-\\w+-\\w+";
    public static String NUMBER_PATTERN = "[0-9]+";
    public static String ANYTHING_PATTERN = ".*";

    /*
     * Query parameters
     */


    public static final String QUERY_LOCAL_NODE_ONLY = "localNodeOnly";
    public static final String QUERY_EXTERNAL_KEY = "externalKey";
    public static final String QUERY_API_KEY = "apiKey";
    public static final String QUERY_REQUESTED_DT = "requestedDate";
    public static final String QUERY_PAYMENT_EXTERNAL_KEY = "paymentExternalKey";
    public static final String QUERY_TRANSACTION_EXTERNAL_KEY = "transactionExternalKey";
    public static final String QUERY_ENTITLEMENT_REQUESTED_DT = "entitlementDate";
    public static final String QUERY_BILLING_REQUESTED_DT = "billingDate";
    public static final String QUERY_CALL_COMPLETION = "callCompletion";
    public static final String QUERY_USE_REQUESTED_DATE_FOR_BILLING = "useRequestedDateForBilling";
    public static final String QUERY_CALL_TIMEOUT = "callTimeoutSec";
    public static final String QUERY_TARGET_DATE = "targetDate";
    public static final String QUERY_BILLING_POLICY = "billingPolicy";
    public static final String QUERY_MIGRATED = "migrated";
    public static final String QUERY_ENTITLEMENT_POLICY = "entitlementPolicy";
    public static final String QUERY_SEARCH_OFFSET = "offset";
    public static final String QUERY_SEARCH_LIMIT = "limit";
    public static final String QUERY_ENTITLEMENT_EFFECTIVE_FROM_DT = "effectiveFromDate";
    public static final String QUERY_FORCE_NEW_BCD_WITH_PAST_EFFECTIVE_DATE = "forceNewBcdWithPastEffectiveDate";

    public static final String QUERY_ACCOUNT_WITH_BALANCE = "accountWithBalance";
    public static final String QUERY_ACCOUNT_WITH_BALANCE_AND_CBA = "accountWithBalanceAndCBA";
    public static final String QUERY_ACCOUNT_TREAT_NULL_AS_RESET = "treatNullAsReset";

    public static final String QUERY_ACCOUNT_ID = "accountId";

    public static final String QUERY_BLOCKING_STATE_TYPES = "blockingStateTypes";
    public static final String QUERY_BLOCKING_STATE_SVCS = "blockingStateSvcs";


    public static final String QUERY_INVOICE_WITH_ITEMS = "withItems";
    public static final String QUERY_WITH_MIGRATION_INVOICES = "withMigrationInvoices";
    public static final String QUERY_UNPAID_INVOICES_ONLY = "unpaidInvoicesOnly";
    public static final String QUERY_INVOICE_WITH_CHILDREN_ITEMS = "withChildrenItems";

    public static final String QUERY_PAYMENT_EXTERNAL = "externalPayment";
    public static final String QUERY_PAYMENT_AMOUNT = "paymentAmount";
    public static final String QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS = "withRefundsAndChargebacks";
    public static final String QUERY_PAYMENT_PLUGIN_NAME = "pluginName";
    public static final String QUERY_PAYMENT_METHOD_ID = "paymentMethodId";
    public static final String QUERY_PAYMENT_CONTROL_PLUGIN_NAME = "controlPluginName";

    public static final String QUERY_TENANT_USE_GLOBAL_DEFAULT = "useGlobalDefault";
    public static final String QUERY_TAGS_INCLUDED_DELETED = "includedDeleted";

    public static final String QUERY_TAGS = "tagList";
    public static final String QUERY_CUSTOM_FIELDS = "customFieldList";

    public static final String QUERY_OBJECT_TYPE = "objectType";

    public static final String QUERY_PAYMENT_METHOD_PLUGIN_NAME = "pluginName";
    public static final String QUERY_WITH_PLUGIN_INFO = "withPluginInfo";
    public static final String QUERY_WITH_ATTEMPTS = "withAttempts";
    public static final String QUERY_PAYMENT_METHOD_IS_DEFAULT = "isDefault";

    public static final String QUERY_PAY_ALL_UNPAID_INVOICES = "payAllUnpaidInvoices";
    public static final String QUERY_PAY_INVOICE = "payInvoice";

    public static final String QUERY_PLUGIN_PROPERTY = "pluginProperty";

    public static final String QUERY_START_DATE = "startDate";
    public static final String QUERY_END_DATE = "endDate";

    public static final String QUERY_DELETE_IF_EXISTS = "deleteIfExists";

    public static final String QUERY_BUNDLE_TRANSFER_ADDON = "transferAddOn";
    public static final String QUERY_BUNDLE_TRANSFER_CANCEL_IMM = "cancelImmediately";
    public static final String QUERY_BUNDLES_FILTER = "bundlesFilter";

    public static final String QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF = "deleteDefaultPmWithAutoPayOff";

    public static final String QUERY_FORCE_DEFAULT_PM_DELETION = "forceDefaultPmDeletion";

    public static final String QUERY_AUDIT = "audit";

    public static final String QUERY_BCD = "bcd";

    public static final String QUERY_PARALLEL = "parallel";

    public static final String QUERY_AUTO_COMMIT = "autoCommit";

    public static final String QUERY_NOTIFICATION_CALLBACK = "cb";

    public static final String PAGINATION = "pagination";

    public static final String ADMIN = "admin";
    public static final String ADMIN_PATH = PREFIX + "/" + ADMIN;

    public static final String ACCOUNTS = "accounts";
    public static final String ACCOUNTS_PATH = PREFIX + "/" + ACCOUNTS;

    public static final String ANALYTICS = "analytics";
    public static final String ANALYTICS_PATH = PREFIX + "/" + ANALYTICS;

    public static final String BUNDLES = "bundles";
    public static final String BUNDLES_PATH = PREFIX + "/" + BUNDLES;

    public static final String SECURITY = "security";
    public static final String SECURITY_PATH = PREFIX + "/" + SECURITY;

    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String SUBSCRIPTIONS_PATH = PREFIX + "/" + SUBSCRIPTIONS;

    public static final String ENTITLEMENTS = "entitlements";
    public static final String ENTITLEMENTS_PATH = PREFIX + "/" + ENTITLEMENTS;

    public static final String TAG_DEFINITIONS = "tagDefinitions";
    public static final String TAG_DEFINITIONS_PATH = PREFIX + "/" + TAG_DEFINITIONS;

    public static final String INVOICES = "invoices";
    public static final String INVOICES_PATH = PREFIX + "/" + INVOICES;

    public static final String CHARGES = "charges";
    public static final String CHARGES_PATH = PREFIX + "/" + INVOICES + "/" + CHARGES;

    public static final String PAYMENTS = "payments";
    public static final String PAYMENTS_PATH = PREFIX + "/" + PAYMENTS;

    public static final String PAYMENT_TRANSACTIONS = "paymentTransactions";
    public static final String PAYMENT_TRANSACTIONS_PATH = PREFIX + "/" + PAYMENT_TRANSACTIONS;

    public static final String PAYMENT_GATEWAYS = "paymentGateways";
    public static final String PAYMENT_GATEWAYS_PATH = PREFIX + "/" + PAYMENT_GATEWAYS;

    public static final String REFUNDS = "refunds";

    public static final String PAYMENT_METHODS = "paymentMethods";
    public static final String PAYMENT_METHODS_PATH = PREFIX + "/" + PAYMENT_METHODS;
    public static final String PAYMENT_METHODS_DEFAULT_PATH_POSTFIX = "setDefault";

    public static final String CREDITS = "credits";
    public static final String CREDITS_PATH = PREFIX + "/" + CREDITS;

    public static final String INVOICE_PAYMENTS = "invoicePayments";
    public static final String INVOICE_PAYMENTS_PATH = PREFIX + "/" + INVOICE_PAYMENTS;
    public static final String DRY_RUN = "dryRun";

    public static final String CHARGEBACKS = "chargebacks";
    public static final String CHARGEBACKS_PATH = PREFIX + "/" + CHARGEBACKS;

    public static final String CHARGEBACK_REVERSALS = "chargebackReversals";
    public static final String CHARGEBACK_REVERSALS_PATH = PREFIX + "/" + CHARGEBACK_REVERSALS;

    public static final String ALL_TAGS = "allTags";
    public static final String TAGS = "tags";
    public static final String TAGS_PATH = PREFIX + "/" + TAGS;

    public static final String CUSTOM_FIELDS = "customFields";
    public static final String CUSTOM_FIELDS_PATH = PREFIX + "/" + CUSTOM_FIELDS;

    public static final String EMAILS = "emails";
    public static final String EMAIL_NOTIFICATIONS = "emailNotifications";

    public static final String CATALOG = "catalog";
    public static final String CATALOG_PATH = PREFIX + "/" + CATALOG;

    public static final String OVERDUE = "overdue";
    public static final String OVERDUE_PATH = PREFIX + "/" + OVERDUE;

    public static final String TENANTS = "tenants";
    public static final String TENANTS_PATH = PREFIX + "/" + TENANTS;

    public static final String USAGES = "usages";
    public static final String USAGES_PATH = PREFIX + "/" + USAGES;

    public static final String EXPORT = "export";
    public static final String EXPORT_PATH = PREFIX + "/" + EXPORT;

    public static final String PLUGINS_INFO = "pluginsInfo";
    public static final String PLUGINS_INFO_PATH = PREFIX + "/" + PLUGINS_INFO;

    public static final String NODES_INFO = "nodesInfo";
    public static final String NODES_INFO_PATH = PREFIX + "/" + NODES_INFO;

    // No PREFIX here!
    public static final String PLUGINS = "plugins";
    public static final String PLUGINS_PATH = "/" + PLUGINS;

    public static final String TEST = "test";
    public static final String TEST_PATH = PREFIX + "/" + TEST;

    public static final String CBA_REBALANCING = "cbaRebalancing";

    public static final String PAUSE = "pause";
    public static final String RESUME = "resume";
    public static final String BLOCK = "block";

    public static final String AUTHORIZATION = "authorization";
    public static final String CAPTURE = "capture";

    public static final String HOSTED = "hosted";
    public static final String FORM = "form";
    public static final String NOTIFICATION = "notification";

    public static final String CANCEL_SCHEDULED_PAYMENT_TRANSACTION = "cancelScheduledPaymentTransaction";


    public static final String INVOICE_TEMPLATE = "template";
    public static final String INVOICE_MP_TEMPLATE = "manualPayTemplate";
    public static final String INVOICE_TRANSLATION = "translation";
    public static final String INVOICE_CATALOG_TRANSLATION = "catalogTranslation";
    public static final String COMMIT_INVOICE = "commitInvoice";

    public static final String COMBO = "combo";
    public static final String MIGRATION = "migration";

    public static final String CHILDREN = "children";
    public static final String BCD = "bcd";
    public static final String TRANSFER_CREDIT = "transferCredit";

    public static final String CACHE = "cache";

    public static final String QUERY_INCLUDED_DELETED = "includedDeleted";


}
