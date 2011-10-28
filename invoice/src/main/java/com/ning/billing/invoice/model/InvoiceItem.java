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

package com.ning.billing.invoice.model;

import java.math.BigDecimal;

public class InvoiceItem {
//    private final String description;
//    private final DateTime startDate;
//    private final DateTime endDate;
    private final BigDecimal amount;
//    private final Currency currency;

    // TODO: Jeff -- determine if a default constructor is required for InvoiceItem
    //public InvoiceItem(DateTime startDate, DateTime endDate, String description, BigDecimal amount, Currency currency) {
    public InvoiceItem(BigDecimal amount) {
        //this.description = description;
        this.amount = amount;
//        this.currency = currency;
//        this.startDate = startDate;
//        this.endDate = endDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
