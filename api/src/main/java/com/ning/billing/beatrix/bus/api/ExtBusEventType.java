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
package com.ning.billing.beatrix.bus.api;

/**
 * The enum {@code ExtBusEventType} represents the user visible bus event types.
 */

public enum ExtBusEventType {
    ACCOUNT_CREATION,
    ACCOUNT_CHANGE,
    SUBSCRIPTION_CREATION,
    SUBSCRIPTION_PHASE,
    SUBSCRIPTION_CHANGE,
    SUBSCRIPTION_CANCEL,
    SUBSCRIPTION_UNCANCEL,
    OVERDUE_CHANGE,
    INVOICE_CREATION,
    INVOICE_ADJUSTMENT,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    TAG_CREATION,
    TAG_DELETION,
    CUSTOM_FIELD_CREATION,
    CUSTOM_FIELD_DELETION
}
