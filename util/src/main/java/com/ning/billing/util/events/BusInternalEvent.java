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

package com.ning.billing.util.events;

import java.util.UUID;

public interface BusInternalEvent {

    public enum BusInternalEventType {
        ACCOUNT_CREATE,
        ACCOUNT_CHANGE,
        SUBSCRIPTION_TRANSITION,
        BUNDLE_REPAIR,
        INVOICE_EMPTY,
        INVOICE_CREATION,
        INVOICE_ADJUSTMENT,
        PAYMENT_INFO,
        PAYMENT_ERROR,
        CONTROL_TAG_CREATION,
        CONTROL_TAG_DELETION,
        USER_TAG_CREATION,
        USER_TAG_DELETION,
        CONTROL_TAGDEFINITION_CREATION,
        CONTROL_TAGDEFINITION_DELETION,
        USER_TAGDEFINITION_CREATION,
        USER_TAGDEFINITION_DELETION,
        OVERDUE_CHANGE,
        CUSTOM_FIELD_CREATION,
        CUSTOM_FIELD_DELETION,
    }

    public BusInternalEventType getBusEventType();

    public UUID getUserToken();

    public Long getTenantRecordId();

    public Long getAccountRecordId();
}
