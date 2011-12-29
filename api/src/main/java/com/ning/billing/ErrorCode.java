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

    /*
     *
     * Range 1000 : ENTITLEMENTS
     *
     */
    /* Generic through APIs */
    ENT_INVALID_REQUESTED_DATE(1001, "Requested in the future is not allowed : %s"),

    /* Creation */
    ENT_CREATE_BAD_PHASE(1011, "Can't create plan initial phase %s"),
    ENT_CREATE_NO_BUNDLE(1012, "Bundle %s does not exist"),
    ENT_CREATE_NO_BP(1013, "Missing Base Subscription for bundle %s"),
    ENT_CREATE_BP_EXISTS(1015, "Subscription bundle %s already has a base subscription"),
    ENT_CREATE_AO_BP_NON_ACTIVE(1017, "Can't create AddOn %s for non active Base Plan"),
    ENT_CREATE_AO_ALREADY_INCLUDED(1018, "Can't create AddOn %s for BasePlan %s (Already included)"),
    ENT_CREATE_AO_NOT_AVAILABLE(1019, "Can't create AddOn %s for BasePlan %s (Not available)"),

    /* Change plan */
    ENT_CHANGE_NON_ACTIVE(1021, "Subscription %s is in state %s"),
    ENT_CHANGE_FUTURE_CANCELLED(1022, "Subscription %s is future cancelled"),
    /* Cancellation */
    ENT_CANCEL_BAD_STATE(1031, "Subscription %s is in state %s"),
    /* Un-cancellation */
    ENT_UNCANCEL_BAD_STATE(1070, "Subscription %s was not in a cancelled state"),

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
    CAT_PRICE_VALUE_NULL_FOR_CURRENCY(2011, "The value for the currency '%s' is NULL. This plan cannot be bought in this currnency."),

    /*
     * Plans
     */
    CAT_PLAN_NOT_FOUND(2020,"Could not find a plan matching: (product: '%s', billing period: '%s', pricelist '%s')"),
    CAT_NO_SUCH_PLAN(2021,"Could not find any plans named '%s'"),

    /*
     * Products
     */
    CAT_NO_SUCH_PRODUCT(2030,"Could not find any plans named '%s'"),

    /*
     * Phases
     */
    CAT_NO_SUCH_PHASE(2040,"Could not find any phases named '%s'")
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
