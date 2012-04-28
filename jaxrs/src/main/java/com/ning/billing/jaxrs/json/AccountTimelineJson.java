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
package com.ning.billing.jaxrs.json;

import java.util.LinkedList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.payment.api.PaymentInfoEvent;

public class AccountTimelineJson {

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<PaymentJson> payments;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<InvoiceJson> invoices;
    
    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final AccountJsonSimple account;
    
    @JsonView(BundleTimelineViews.Timeline.class)
    private final List<BundleJsonWithSubscriptions> bundles;
    
    @JsonCreator
    public AccountTimelineJson(@JsonProperty("account") AccountJsonSimple account,
            @JsonProperty("bundles") List<BundleJsonWithSubscriptions> bundles,
            @JsonProperty("invoices") List<InvoiceJson> invoices,            
            @JsonProperty("payments") List<PaymentJson> payments) {
        this.account = account;
        this.bundles = bundles;
        this.invoices = invoices;
        this.payments = payments;
    }
    
    public AccountTimelineJson(Account account, List<Invoice> invoices, List<PaymentInfoEvent> payments, List<BundleTimeline> bundles) {
        this.account = new AccountJsonSimple(account.getId().toString(), account.getExternalKey());
        this.bundles = new LinkedList<BundleJsonWithSubscriptions>();
        for (BundleTimeline cur : bundles) {
            this.bundles.add(new BundleJsonWithSubscriptions(account.getId(), cur));            
        }
        this.invoices = new LinkedList<InvoiceJson>();
        for (Invoice cur : invoices) {
            this.invoices.add(new InvoiceJson(cur.getTotalAmount(), cur.getId().toString(), cur.getInvoiceDate(), Integer.toString(cur.getInvoiceNumber()), cur.getBalance()));
        }
        this.payments = new LinkedList<PaymentJson>();
        for (PaymentInfoEvent cur : payments) {
            // STEPH how to link that payment with the invoice ??
            this.payments.add(new PaymentJson(cur.getAmount(), null , cur.getPaymentNumber(), null, cur.getEffectiveDate(), cur.getStatus()));
        }
    }
    
    public AccountTimelineJson() {
        this.account = null;
        this.bundles = null;
        this.invoices = null;
        this.payments = null;
    }

    public List<PaymentJson> getPayments() {
        return payments;
    }

    public List<InvoiceJson> getInvoices() {
        return invoices;
    }

    public AccountJsonSimple getAccount() {
        return account;
    }

    public List<BundleJsonWithSubscriptions> getBundles() {
        return bundles;
    }
}
