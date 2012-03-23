/*
 * Copyright 2010-2011 Ning, Inc.
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

package com.ning.billing;

public enum ErrorCode {

    /*
     * Range 0 : COMMON EXCEPTIONS
     */
    NOT_IMPLEMENTED(1, "Api not implemented yet"),
    DATA_TRUNCATION(2, "Data truncation error. (%s)"),
    /*
     *
     * Range 1000 : ENTITLEMENTS
     *
     */
    /* Generic through APIs */
    ENT_INVALID_REQUESTED_FUTURE_DATE(1001, "Requested date %s in the future is not allowed"),
    ENT_INVALID_REQUESTED_DATE(1001, "Requested date %s is not allowed to be prior to the previous transition %s"),

    /* Creation */
    ENT_CREATE_BAD_PHASE(1011, "Can't create plan initial phase %s"),
    ENT_CREATE_NO_BUNDLE(1012, "Bundle %s does not exist"),
    ENT_CREATE_NO_BP(1013, "Missing Base Subscription for bundle %s"),
    ENT_CREATE_BP_EXISTS(1015, "Subscription bundle %s already has a base subscription"),
    ENT_CREATE_AO_BP_NON_ACTIVE(1017, "Can't create AddOn %s for non active Base Plan"),
    ENT_CREATE_AO_ALREADY_INCLUDED(1018, "Can't create AddOn %s for BasePlan %s (Already included)"),
    ENT_CREATE_AO_NOT_AVAILABLE(1019, "Can't create AddOn %s for BasePlan %s (Not available)"),

    /* Change plan */
    ENT_CHANGE_NON_ACTIVE(1021, "Subscription %s is in state %s: Failed to change plan"),
    ENT_CHANGE_FUTURE_CANCELLED(1022, "Subscription %s is future cancelled: Failed to change plan"),
    /* Cancellation */
    ENT_CANCEL_BAD_STATE(1031, "Subscription %s is in state %s: Failed to cancel"),
    /* Recreation */
    ENT_RECREATE_BAD_STATE(1041, "Subscription %s is in state %s: Failed to recreate"),

    /* Un-cancellation */
    ENT_UNCANCEL_BAD_STATE(1070, "Subscription %s was not in a cancelled state: Failed to uncancel plan"),

    /* Fetch */
    ENT_GET_NO_BUNDLE_FOR_SUBSCRIPTION(1080, "Could not find a bundle for subscription %s"),
    ENT_GET_INVALID_BUNDLE_ID(1081, "Could not find a bundle matching id %s"),
    ENT_INVALID_SUBSCRIPTION_ID(1082, "Unknown subscription %s"),
    /*
    *
    * Range 2000 : CATALOG
    *
    */

    /*
    * Rules exceptions
    */

    /* Plan change is disallowed by the catalog */
    CAT_ILLEGAL_CHANGE_REQUEST(2001, "Attempting to change plan from (product: '%s', billing period: '%s', " +
    		"pricelist '%s') to (product: '%s', billing period: '%s', pricelist '%s'). This transition is not allowed by catalog rules"),

	/*
	 * Price list
	 */

	/*Attempt to reference a price that is not present - should only happen if it is a currency not available in the catalog */
    CAT_NO_PRICE_FOR_CURRENCY(2010, "This price does not have a value for the currency '%s'."),

    /* Price value explicitly set to NULL meaning there is no price available in that currency */
    CAT_PRICE_VALUE_NULL_FOR_CURRENCY(2011, "The value for the currency '%s' is NULL. This plan cannot be bought in this currency."),
    CAT_NULL_PRICE_LIST_NAME(2012,"Price list name was null"),
    CAT_PRICE_LIST_NOT_FOUND(2013, "Could not find a pricelist with name '%s'"),
    /*
     * Plans
     */
    CAT_PLAN_NOT_FOUND(2020,"Could not find a plan matching: (product: '%s', billing period: '%s', pricelist '%s')"),
    CAT_NO_SUCH_PLAN(2021,"Could not find any plans named '%s'"),

    /*
     * Products
     */
    CAT_NO_SUCH_PRODUCT(2030,"Could not find any product named '%s'"),
    CAT_NULL_PRODUCT_NAME(2031,"Product name was null"),
    /*
     * Phases
     */
    CAT_NO_SUCH_PHASE(2040,"Could not find any phases named '%s'"),
    CAT_BAD_PHASE_NAME(2041,"Bad phase name '%s'"),
    /*
     * Versioned Catalog
     */
    CAT_NO_CATALOG_FOR_GIVEN_DATE(2050, "There is no catalog version that applies for the given date '%s'"),
    CAT_NO_CATALOG_ENTRIES_FOR_GIVEN_DATE(2051, "The are no catalog entries that apply for the given date '%s'"),
    CAT_CATALOG_NAME_MISMATCH(2052, "The catalog name '%s' does not match the name of the catalog we are trying to add '%s'"),
    /*
     * Billing Alignment
     */
    CAT_INVALID_BILLING_ALIGNMENT(2060, "Invalid billing alignment '%s'"),

   /*
    *
    * Range 3000 : ACCOUNT
    *
    */
    ACCOUNT_ALREADY_EXISTS(3000, "Account already exists for key %s"),
    ACCOUNT_INVALID_NAME(3001, "An invalid name was specified when creating or updating an account."),
    ACCOUNT_DOES_NOT_EXIST_FOR_ID(3002, "Account does not exist for id %s"),
    ACCOUNT_DOES_NOT_EXIST_FOR_KEY(3003, "Account does not exist for key %s"),
    ACCOUNT_CANNOT_MAP_NULL_KEY(3004, "An attempt was made to get the id for a <null> external key."),
    ACCOUNT_CANNOT_CHANGE_EXTERNAL_KEY(3005, "External keys cannot be updated. Original key remains: %s"),
    ACCOUNT_CREATION_FAILED(3006, "Account creation failed."),
    ACCOUNT_UPDATE_FAILED(3007, "Account update failed."),

   /*
    *
    * Range 3900: Tag definitions
    *
    */
    TAG_DEFINITION_CONFLICTS_WITH_CONTROL_TAG(3900, "The tag definition name conflicts with a reserved name (name %s)"),
    TAG_DEFINITION_ALREADY_EXISTS(3901, "The tag definition name already exists (name: %s)"),
    TAG_DEFINITION_DOES_NOT_EXIST(3902, "The tag definition name does not exist (name: %s)"),
    TAG_DEFINITION_IN_USE(3903, "The tag definition name is currently in use (name: %s)"),
    
    CONTROL_TAG_DOES_NOT_EXIST(3904, "The control tag does not exist (name: %s)"),

   /*
    *
    * Range 4000: INVOICE
    *
    */
    INVOICE_ACCOUNT_ID_INVALID(4001, "No account could be retrieved for id %s"),
    INVOICE_INVALID_TRANSITION(4002, "Transition did not contain a subscription id."),
    INVOICE_NO_ACCOUNT_ID_FOR_SUBSCRIPTION_ID(4003, "No account id was retrieved for subscription id %s"),
    INVOICE_INVALID_DATE_SEQUENCE(4004, "Date sequence was invalid. Start Date: %s; End Date: %s; Target Date: %s"),
    INVOICE_TARGET_DATE_TOO_FAR_IN_THE_FUTURE(4005, "The target date was too far in the future. Target Date: %s")
    ;

    private int code;
    private String format;

    ErrorCode(int code, String format) {
        this.code = code;
        this.format = format;
    }

    public String getFormat() {
        return format;
    }

    public int getCode() {
        return code;
    }

}
