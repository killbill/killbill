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
package com.ning.billing.payment.api;

// STEPH is that the enum we want to export? seems to internal
public enum PaymentStatus {
    /* Success! */
    SUCCESS,
    /* Initial status for Payment and PaymentAttempt */
    UNKNOWN,
    /* Status for Payment when AUTO_PAY_OFF is turned on */
    AUTO_PAY_OFF,
    /* Status for Payment and PaymentAttempt when the plugin failed to make the Payment and we will schedule a FailedPaymentRetry */
    PAYMENT_FAILURE,
    /* Payment failure , we already retried a maximum of time */
    PAYMENT_FAILURE_ABORTED,
    /* Exception from plugin, state is unknown and needs to be retried */
    PLUGIN_FAILURE,
    /* Exception from plugin, we already retried a maximum of time */
    PLUGIN_FAILURE_ABORTED,
    /* Payment Subsystem is off */
    PAYMENT_SYSTEM_OFF
}
