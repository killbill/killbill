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

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import com.ning.billing.account.api.Account;
import com.ning.billing.entitlement.api.timeline.BundleTimeline;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.payment.api.PaymentAttempt;

public class AccountTimelineJson {

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<PaymentJsonWithBundleKeys> payments;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<InvoiceJsonWithBundleKeys> invoices;
    
    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final AccountJsonSimple account;
    
    @JsonView(BundleTimelineViews.Timeline.class)
    private final List<BundleJsonWithSubscriptions> bundles;
    
    @JsonCreator
    public AccountTimelineJson(@JsonProperty("account") AccountJsonSimple account,
            @JsonProperty("bundles") List<BundleJsonWithSubscriptions> bundles,
            @JsonProperty("invoices") List<InvoiceJsonWithBundleKeys> invoices,            
            @JsonProperty("payments") List<PaymentJsonWithBundleKeys> payments) {
        this.account = account;
        this.bundles = bundles;
        this.invoices = invoices;
        this.payments = payments;
    }
    
    private String getBundleExternalKey(UUID invoiceId,  List<Invoice> invoices, List<BundleTimeline> bundles) {
        for (Invoice cur : invoices) {
            if (cur.getId().equals(invoiceId)) {
                return getBundleExternalKey(cur, bundles);
            }
        }
        return null;
    }
    
    private String getBundleExternalKey(Invoice invoice, List<BundleTimeline> bundles) {
        Set<UUID> b = new HashSet<UUID>();
        for (final InvoiceItem cur : invoice.getInvoiceItems()) {
            b.add(cur.getBundleId());
        }
        boolean first = true;
        StringBuilder tmp = new StringBuilder();
        for (final UUID cur : b) {
            for (final BundleTimeline bt : bundles) {
                if (bt.getBundleId().equals(cur)) {
                    if (!first) {
                        tmp.append(",");
                    }
                    tmp.append(bt.getExternalKey());
                    first = false;
                    break;
                }
            }
        }
        return tmp.toString();
    }
    
    public AccountTimelineJson(Account account, List<Invoice> invoices, List<PaymentAttempt> payments, List<BundleTimeline> bundles) {
        this.account = new AccountJsonSimple(account.getId().toString(), account.getExternalKey());
        this.bundles = new LinkedList<BundleJsonWithSubscriptions>();
        for (BundleTimeline cur : bundles) {
            this.bundles.add(new BundleJsonWithSubscriptions(account.getId(), cur));            
        }
        this.invoices = new LinkedList<InvoiceJsonWithBundleKeys>();
        for (Invoice cur : invoices) {
            this.invoices.add(new InvoiceJsonWithBundleKeys(cur.getAmountCharged(), cur.getAmountCredited(), cur.getId().toString(), cur.getInvoiceDate(), cur.getTargetDate(),
                    Integer.toString(cur.getInvoiceNumber()), cur.getBalance(),
                    getBundleExternalKey(cur, bundles)));
        }
        this.payments = new LinkedList<PaymentJsonWithBundleKeys>();
        for (PaymentAttempt cur : payments) {
            

            String status = cur.getPaymentId() != null ? "Success" : "Failed";
            BigDecimal paidAmount = cur.getPaymentId() != null ? cur.getAmount() : BigDecimal.ZERO;
            
            this.payments.add(new PaymentJsonWithBundleKeys(cur.getAmount(), paidAmount, cur.getInvoiceId(), cur.getPaymentId(), cur.getCreatedDate(), cur.getUpdatedDate(),
                    cur.getRetryCount(), cur.getCurrency().toString(), status,
                    getBundleExternalKey(cur.getInvoiceId(), invoices, bundles)));
          }
    }
    
    public AccountTimelineJson() {
        this.account = null;
        this.bundles = null;
        this.invoices = null;
        this.payments = null;
    }

    public List<PaymentJsonWithBundleKeys> getPayments() {
        return payments;
    }

    public List<InvoiceJsonWithBundleKeys> getInvoices() {
        return invoices;
    }

    public AccountJsonSimple getAccount() {
        return account;
    }

    public List<BundleJsonWithSubscriptions> getBundles() {
        return bundles;
    }
}
