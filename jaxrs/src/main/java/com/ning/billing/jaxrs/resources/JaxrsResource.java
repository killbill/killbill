/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package com.ning.billing.jaxrs.resources;

public interface JaxrsResource {

    public static final String API_PREFIX = "";
    public static final String API_VERSION = "/1.0";
    public static final String API_POSTFIX = "/kb";

    public static final String PREFIX = API_PREFIX + API_VERSION + API_POSTFIX;

    public static final String TIMELINE = "timeline";
    public static final String REGISTER_NOTIFICATION_CALLBACK = "registerNotificationCallback";

    /*
     * Metadata Additional headers
     */
    public static String HDR_CREATED_BY = "X-Killbill-CreatedBy";
    public static String HDR_REASON = "X-Killbill-Reason";
    public static String HDR_COMMENT = "X-Killbill-Comment";

    /*
     * Patterns
     */
    public static String STRING_PATTERN = "[\\w-]+";
    public static String UUID_PATTERN = "\\w+-\\w+-\\w+-\\w+-\\w+";

    /*
     * Query parameters
     */
    public static final String QUERY_EXTERNAL_KEY = "externalKey";
    public static final String QUERY_API_KEY = "apiKey";
    public static final String QUERY_REQUESTED_DT = "requestedDate";
    public static final String QUERY_CALL_COMPLETION = "callCompletion";
    public static final String QUERY_CALL_TIMEOUT = "callTimeoutSec";
    public static final String QUERY_DRY_RUN = "dryRun";
    public static final String QUERY_TARGET_DATE = "targetDate";
    public static final String QUERY_POLICY = "policy";

    public static final String QUERY_ACCOUNT_WITH_BALANCE = "accountWithBalance";
    public static final String QUERY_ACCOUNT_WITH_BALANCE_AND_CBA = "accountWithBalanceAndCBA";

    public static final String QUERY_ACCOUNT_ID = "accountId";

    public static final String QUERY_INVOICE_WITH_ITEMS = "withItems";

    public static final String QUERY_PAYMENT_EXTERNAL = "externalPayment";
    public static final String QUERY_PAYMENT_WITH_REFUNDS_AND_CHARGEBACKS = "withRefundsAndChargebacks";

    public static final String QUERY_TAGS = "tagList";
    public static final String QUERY_CUSTOM_FIELDS = "customFieldList";

    public static final String QUERY_PAYMENT_METHOD_IS_DEFAULT = "isDefault";

    public static final String QUERY_BUNDLE_TRANSFER_ADDON = "transferAddOn";
    public static final String QUERY_BUNDLE_TRANSFER_CANCEL_IMM = "cancelImmediately";

    public static final String QUERY_DELETE_DEFAULT_PM_WITH_AUTO_PAY_OFF = "deleteDefaultPmWithAutoPayOff";

    public static final String QUERY_AUDIT = "audit";

    public static final String QUERY_NOTIFICATION_CALLBACK = "cb";

    public static final String ACCOUNTS = "accounts";
    public static final String ACCOUNTS_PATH = PREFIX + "/" + ACCOUNTS;

    public static final String ANALYTICS = "analytics";
    public static final String ANALYTICS_PATH = PREFIX + "/" + ANALYTICS;

    public static final String BUNDLES = "bundles";
    public static final String BUNDLES_PATH = PREFIX + "/" + BUNDLES;

    public static final String SUBSCRIPTIONS = "subscriptions";
    public static final String SUBSCRIPTIONS_PATH = PREFIX + "/" + SUBSCRIPTIONS;

    public static final String TAG_DEFINITIONS = "tagDefinitions";
    public static final String TAG_DEFINITIONS_PATH = PREFIX + "/" + TAG_DEFINITIONS;

    public static final String INVOICES = "invoices";
    public static final String INVOICES_PATH = PREFIX + "/" + INVOICES;

    public static final String CHARGES = "charges";
    public static final String CHARGES_PATH = PREFIX + "/" + INVOICES + "/" + CHARGES;

    public static final String PAYMENTS = "payments";
    public static final String PAYMENTS_PATH = PREFIX + "/" + PAYMENTS;

    public static final String REFUNDS = "refunds";
    public static final String REFUNDS_PATH = PREFIX + "/" + "refunds";

    public static final String PAYMENT_METHODS = "paymentMethods";
    public static final String PAYMENT_METHODS_PATH = PREFIX + "/" + PAYMENT_METHODS;
    public static final String PAYMENT_METHODS_DEFAULT_PATH_POSTFIX = "setDefault";

    public static final String CREDITS = "credits";
    public static final String CREDITS_PATH = PREFIX + "/" + CREDITS;

    public static final String CHARGEBACKS = "chargebacks";
    public static final String CHARGEBACKS_PATH = PREFIX + "/" + CHARGEBACKS;

    public static final String TAGS = "tags";
    public static final String CUSTOM_FIELDS = "customFields";
    public static final String EMAILS = "emails";
    public static final String EMAIL_NOTIFICATIONS = "emailNotifications";

    public static final String CATALOG = "catalog";
    public static final String CATALOG_PATH = PREFIX + "/" + CATALOG;

    public static final String OVERDUE = "overdue";
    public static final String OVERDUE_PATH = PREFIX + "/" + OVERDUE;

    public static final String TENANTS = "tenants";
    public static final String TENANTS_PATH = PREFIX + "/" + TENANTS;

    public static final String EXPORT = "export";
    public static final String EXPORT_PATH = PREFIX + "/" + EXPORT;

    public static final String PLUGINS = "plugins";
    // No PREFIX here!
    public static final String PLUGINS_PATH = "/" + PLUGINS;

    public static final String CBA_REBALANCING = "cbaRebalancing";
}
