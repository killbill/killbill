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

package com.ning.billing.invoice.api;

public enum InvoiceItemType {
    // Fixed (one-time) charge
    FIXED,
    // Recurring charge
    RECURRING,
    // Internal adjustment, used for repair
    REPAIR_ADJ,
    // Internal adjustment, used as rollover credits
    CBA_ADJ,
    // Credit adjustment, either at the account level (on its own invoice) or against an existing invoice
    // (invoice level adjustment)
    CREDIT_ADJ,
    // Invoice item adjustment (by itself or triggered by a refund)
    ITEM_ADJ,
    // Refund adjustment (against a posted payment), used when adjusting invoices
    REFUND_ADJ
}
