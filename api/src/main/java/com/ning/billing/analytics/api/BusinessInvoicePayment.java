/*
 * Copyright 2010-2012 Ning, Inc.
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

package com.ning.billing.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTime;

import com.ning.billing.catalog.api.Currency;
import com.ning.billing.util.entity.Entity;

public interface BusinessInvoicePayment extends Entity {

    public UUID getPaymentId();

    public String getExtFirstPaymentRefId();

    public String getExtSecondPaymentRefId();

    public String getAccountKey();

    public UUID getInvoiceId();

    public DateTime getEffectiveDate();

    public BigDecimal getAmount();

    public Currency getCurrency();

    public String getPaymentError();

    public String getProcessingStatus();

    public BigDecimal getRequestedAmount();

    public String getPluginName();

    public String getPaymentType();

    public String getPaymentMethod();

    public String getCardType();

    public String getCardCountry();

    public String getInvoicePaymentType();

    public UUID getLinkedInvoicePaymentId();
}
