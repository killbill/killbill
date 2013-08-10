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

package com.ning.billing.overdue.config.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PriceList;
import com.ning.billing.catalog.api.Product;
import com.ning.billing.subscription.api.user.SubscriptionBaseBundle;
import com.ning.billing.util.tag.Tag;

public class BillingStateBundle extends BillingState<SubscriptionBaseBundle> {

    private final Product basePlanProduct;
    private final BillingPeriod basePlanBillingPeriod;
    private final PriceList basePlanPriceList;
    private final PhaseType basePlanPhaseType;

    public BillingStateBundle(final UUID id,
                              final int numberOfUnpaidInvoices,
                              final BigDecimal unpaidInvoiceBalance,
                              final LocalDate dateOfEarliestUnpaidInvoice,
                              final DateTimeZone accountTimeZone,
                              final UUID idOfEarliestUnpaidInvoice,
                              final PaymentResponse responseForLastFailedPayment,
                              final Tag[] tags,
                              final Product basePlanProduct,
                              final BillingPeriod basePlanBillingPeriod,
                              final PriceList basePlanPriceList, final PhaseType basePlanPhaseType) {
        super(id, numberOfUnpaidInvoices, unpaidInvoiceBalance,
              dateOfEarliestUnpaidInvoice, accountTimeZone, idOfEarliestUnpaidInvoice,
              responseForLastFailedPayment, tags);

        this.basePlanProduct = basePlanProduct;
        this.basePlanBillingPeriod = basePlanBillingPeriod;
        this.basePlanPriceList = basePlanPriceList;
        this.basePlanPhaseType = basePlanPhaseType;
    }

    public Product getBasePlanProduct() {
        return basePlanProduct;
    }

    public BillingPeriod getBasePlanBillingPeriod() {
        return basePlanBillingPeriod;
    }

    public PriceList getBasePlanPriceList() {
        return basePlanPriceList;
    }

    public PhaseType getBasePlanPhaseType() {
        return basePlanPhaseType;
    }
}
