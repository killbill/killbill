/*
 * Copyright 2014-2019 Groupon, Inc
 * Copyright 2014-2019 The Billing Project, LLC
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

package org.killbill.billing.invoice.dao.serialization;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.killbill.billing.catalog.api.BillingAlignment;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.junction.BillingEvent;
import org.killbill.billing.junction.BillingEventSet;
import org.killbill.billing.subscription.api.SubscriptionBaseTransitionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class BillingEventSetJson {

    private final boolean autoInvoiceOff;
    private final boolean autoInvoiceDraft;
    private final boolean autoInvoiceReuseDraft;
    private final List<SubscriptionBillingEventJson> subs;

    @JsonCreator
    public BillingEventSetJson(@JsonProperty("autoInvoiceOff") final boolean autoInvoiceOff,
                               @JsonProperty("autoInvoiceDraft") final boolean autoInvoiceDraft,
                               @JsonProperty("autoInvoiceReuseDraft") final boolean autoInvoiceReuseDraft,
                               @JsonProperty("subs") final List<SubscriptionBillingEventJson> subs) {
        this.autoInvoiceOff = autoInvoiceOff;
        this.autoInvoiceDraft = autoInvoiceDraft;
        this.autoInvoiceReuseDraft = autoInvoiceReuseDraft;
        this.subs = subs;
    }

    @JsonProperty("autoInvoiceOff")
    public boolean isAutoInvoiceOff() {
        return autoInvoiceOff;
    }

    @JsonProperty("autoInvoiceDraft")
    public boolean isAutoInvoiceDraft() {
        return autoInvoiceDraft;
    }

    @JsonProperty("autoInvoiceReuseDraft")
    public boolean isAutoInvoiceReuseDraft() {
        return autoInvoiceReuseDraft;
    }

    @JsonProperty("subs")
    public List<SubscriptionBillingEventJson> getSubscriptionEvents() {
        return subs;
    }

    public BillingEventSetJson(final BillingEventSet eventSet) {
        this.autoInvoiceOff = eventSet.isAccountAutoInvoiceOff();
        this.autoInvoiceDraft = eventSet.isAccountAutoInvoiceDraft();
        this.autoInvoiceReuseDraft = eventSet.isAccountAutoInvoiceReuseDraft();
        this.subs = new ArrayList<>();

        final Iterator<BillingEvent> it = eventSet.iterator();
        SubscriptionBillingEventJson activeSubscription = null;
        while (it.hasNext()) {
            final BillingEvent cur = it.next();
            if (activeSubscription == null || !activeSubscription.getSubscriptionId().equals(cur.getSubscriptionId())) {
                activeSubscription = new SubscriptionBillingEventJson(cur.getSubscriptionId(),
                                                                      eventSet.getSubscriptionIdsWithAutoInvoiceOff().contains(cur.getSubscriptionId()));
                subs.add(activeSubscription);
            }
            activeSubscription.addBillingEvent(new BillingEventJson(cur));
        }
    }

    public static final class SubscriptionBillingEventJson {

        private final boolean autoInvoiceOff;
        private final UUID subscriptionId;
        private final List<BillingEventJson> evts;

        @JsonCreator
        public SubscriptionBillingEventJson(@JsonProperty("autoInvoiceOff") final boolean autoInvoiceOff,
                                            @JsonProperty("subscriptionId") final UUID subscriptionId,
                                            @JsonProperty("evts") final List<BillingEventJson> evts) {
            this.autoInvoiceOff = autoInvoiceOff;
            this.subscriptionId = subscriptionId;
            this.evts = evts;
        }

        public SubscriptionBillingEventJson(final UUID subscriptionId, final boolean autoInvoiceOff) {
            this.subscriptionId = subscriptionId;
            this.autoInvoiceOff = autoInvoiceOff;
            this.evts = new ArrayList<>();
        }

        @JsonProperty("autoInvoiceOff")
        public boolean isAutoInvoiceOff() {
            return autoInvoiceOff;
        }

        public UUID getSubscriptionId() {
            return subscriptionId;
        }

        @JsonProperty("evts")
        public List<BillingEventJson> getEvents() {
            return evts;
        }

        public void addBillingEvent(final BillingEventJson be) {
            evts.add(be);
        }
    }

    public static final class BillingEventJson {

        private final int bcdLocal;
        private final BillingAlignment alignment;
        private final String planName;
        private final String phaseName;
        private final BillingPeriod billingPeriod;
        private final DateTime effDate;
        private final BigDecimal fixedPrice;
        private final BigDecimal recurringPrice;
        private final SubscriptionBaseTransitionType transitionType;
        private final DateTime catalogEffDt;

        @JsonCreator
        public BillingEventJson(@JsonProperty("bcdLocal") final int bcdLocal,
                                @JsonProperty("alignment") final BillingAlignment alignment,
                                @JsonProperty("planName") final String planName,
                                @JsonProperty("phaseName") final String phaseName,
                                @JsonProperty("billingPeriod") final BillingPeriod billingPeriod,
                                @JsonProperty("effDate") final DateTime effDate,
                                @JsonProperty("fixedPrice") final BigDecimal fixedPrice,
                                @JsonProperty("recurringPrice") final BigDecimal recurringPrice,
                                @JsonProperty("transitionType") final SubscriptionBaseTransitionType transitionType,
                                @JsonProperty("catalogEffDt") final DateTime catalogEffDt) {
            this.bcdLocal = bcdLocal;
            this.alignment = alignment;
            this.planName = planName;
            this.phaseName = phaseName;
            this.billingPeriod = billingPeriod;
            this.effDate = effDate;
            this.fixedPrice = fixedPrice;
            this.recurringPrice = recurringPrice;
            this.transitionType = transitionType;
            this.catalogEffDt = catalogEffDt;
        }

        public BillingEventJson(final BillingEvent event) {
            this.bcdLocal = event.getBillCycleDayLocal();
            this.alignment = event.getBillingAlignment();
            this.planName = event.getPlan() != null ? event.getPlan().getName() : null;
            this.phaseName = event.getPlanPhase() != null ? event.getPlanPhase().getName() : null;
            this.billingPeriod = event.getBillingPeriod();
            this.effDate = event.getEffectiveDate();
            this.fixedPrice = event.getFixedPrice();
            this.recurringPrice = event.getRecurringPrice();
            this.transitionType = event.getTransitionType();
            this.catalogEffDt = event.getCatalogEffectiveDate();
        }

        public int getBcdLocal() {
            return bcdLocal;
        }

        public BillingAlignment getAlignment() {
            return alignment;
        }

        public String getPlanName() {
            return planName;
        }

        public String getPhaseName() {
            return phaseName;
        }

        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        public DateTime getEffDate() {
            return effDate;
        }

        public BigDecimal getFixedPrice() {
            return fixedPrice;
        }

        public BigDecimal getRecurringPrice() {
            return recurringPrice;
        }

        public SubscriptionBaseTransitionType getTransitionType() {
            return transitionType;
        }

        public DateTime getCatalogEffDt() {
            return catalogEffDt;
        }
    }
}
