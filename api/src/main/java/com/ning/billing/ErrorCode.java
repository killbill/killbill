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
    ENT_CREATE_BAD_CATALOG(1011, "Plan for product %s, term %s and set %s does not exist in the catalog"),
    ENT_CREATE_NO_BUNDLE(1012, "Bundle %s does not exists"),
    ENT_CREATE_NO_BP(1013, "Missing Base Subscription for bundle %s"),
    ENT_CREATE_BP_EXISTS(1015, "Subscription bundle %s already has a base subscription"),
    /* Change plan */
    ENT_CHANGE_NON_ACTIVE(1021, "Subscription %s is in state %s"),
    ENT_CHANGE_FUTURE_CANCELLED(1022, "Subscription %s is future cancelled"),
    /* Cancellation */
    ENT_CANCEL_BAD_STATE(1031, "Subscription %s is in state %s"),
    /* Un-cancellation */
    ENT_UNCANCEL_BAD_STATE(1070, "Subscription %s was not in a cancelled state"),
    
    CAT_ILLEGAL_CHANGE_REQUEST(2001, "Attempting to change plan from (product: '%s', billing period: '%s', " +
    		"pricelist '%s') to (product: '%s', billing period: '%s', pricelist '%s'). This transition is not allowed by catalog rules")
    
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
