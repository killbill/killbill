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

import com.ning.billing.util.entity.Entity;
import com.ning.billing.catalog.api.Currency;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface Invoice extends Entity {
    boolean addInvoiceItem(InvoiceItem item);

    boolean addInvoiceItems(List<InvoiceItem> items);

    List<InvoiceItem> getInvoiceItems();

    int getNumberOfItems();

    boolean addPayment(InvoicePayment payment);

    boolean addPayments(List<InvoicePayment> payments);

    List<InvoicePayment> getPayments();

    int getNumberOfPayments();

    UUID getAccountId();

    DateTime getInvoiceDate();

    DateTime getTargetDate();

    Currency getCurrency();

    DateTime getLastPaymentAttempt();

    BigDecimal getAmountPaid();

    BigDecimal getTotalAmount();

    BigDecimal getBalance();

    boolean isDueForPayment(DateTime targetDate, int numberOfDays);
}
