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

package com.ning.billing.invoice.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.Entity;

public interface InvoiceItem extends Entity {

    InvoiceItemType getInvoiceItemType();

    UUID getInvoiceId();

    UUID getAccountId();

    /**
     * @return the service period start date for that item, in the account timezone
     */
    LocalDate getStartDate();

    /**
     * The end date of an item can be null (e.g. for fixed price items).
     *
     * @return the service period end date for that item (if available), in the account timezone
     */
    LocalDate getEndDate();

    BigDecimal getAmount();

    Currency getCurrency();

    String getDescription();

    UUID getBundleId();

    UUID getSubscriptionId();

    String getPlanName();

    String getPhaseName();

    BigDecimal getRate();

    UUID getLinkedItemId();

    /**
     * Items match if they correspond to the same subscription for the same catalog plan and same start / end dates
     *
     * @return true if current and other items match
     */
    boolean matches(Object other);
}
