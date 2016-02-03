/*
 * Copyright 2010-2011 Ning, Inc.
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

package org.killbill.billing.events;

import org.killbill.bus.api.BusEvent;

public interface BusInternalEvent extends BusEvent {

    public BusInternalEventType getBusEventType();

    public enum BusInternalEventType {
        ACCOUNT_CHANGE,
        ACCOUNT_CREATE,
        BLOCKING_STATE,
        BROADCAST_SERVICE,
        BUNDLE_REPAIR,
        CONTROL_TAGDEFINITION_CREATION,
        CONTROL_TAGDEFINITION_DELETION,
        CONTROL_TAG_CREATION,
        CONTROL_TAG_DELETION,
        CUSTOM_FIELD_CREATION,
        CUSTOM_FIELD_DELETION,
        ENTITLEMENT_TRANSITION,
        INVOICE_ADJUSTMENT,
        INVOICE_CREATION,
        INVOICE_NOTIFICATION,
        INVOICE_EMPTY,
        INVOICE_PAYMENT_ERROR,
        INVOICE_PAYMENT_INFO,
        OVERDUE_CHANGE,
        PAYMENT_ERROR,
        PAYMENT_PLUGIN_ERROR,
        PAYMENT_INFO,
        SUBSCRIPTION_TRANSITION,
        USER_TAGDEFINITION_CREATION,
        USER_TAGDEFINITION_DELETION,
        USER_TAG_CREATION,
        USER_TAG_DELETION,
        TENANT_CONFIG_CHANGE,
        TENANT_CONFIG_DELETION;
    }
}
