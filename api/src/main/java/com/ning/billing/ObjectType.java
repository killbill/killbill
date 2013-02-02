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

package com.ning.billing;

public enum ObjectType {
    ACCOUNT("account"),
    ACCOUNT_EMAIL("account email"),
    BUNDLE("subscription bundle"),
    CUSTOM_FIELD("custom field"),
    INVOICE("invoice"),
    PAYMENT("payment"),
    INVOICE_ITEM("invoice item"),
    INVOICE_PAYMENT("invoice payment"),
    SUBSCRIPTION("subscription"),
    SUBSCRIPTION_EVENT("subscription event"),
    PAYMENT_ATTEMPT("payment attempt"),
    PAYMENT_METHOD("payment method"),
    REFUND("refund"),
    TAG("tag"),
    TAG_DEFINITION("tag definition"),
    TENANT("tenant"),
    TENANT_KVS("tenant kvs");

    private final String objectName;

    ObjectType(final String objectName) {
        this.objectName = objectName;
    }

    public String getObjectName() {
        return objectName;
    }
}
