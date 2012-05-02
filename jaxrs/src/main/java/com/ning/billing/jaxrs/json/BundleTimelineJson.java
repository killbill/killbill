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

import java.util.List;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonView;

public class BundleTimelineJson {

    @JsonView(BundleTimelineViews.Timeline.class)
    private final String viewId;

    @JsonView(BundleTimelineViews.Timeline.class)
    private final BundleJsonWithSubscriptions bundle;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<PaymentJson> payments;

    @JsonView(BundleTimelineViews.ReadTimeline.class)
    private final List<InvoiceJson> invoices;

    @JsonView(BundleTimelineViews.WriteTimeline.class)
    private final String resonForChange;

    @JsonCreator
    public BundleTimelineJson(@JsonProperty("view_id") String viewId,
            @JsonProperty("bundle") BundleJsonWithSubscriptions bundle,
            @JsonProperty("payments") List<PaymentJson> payments,
            @JsonProperty("invoices") List<InvoiceJson> invoices,
            @JsonProperty("reason_for_change") String reason) {
        this.viewId = viewId;
        this.bundle = bundle;
        this.payments = payments;
        this.invoices = invoices;
        this.resonForChange = reason;
    }

    public String getViewId() {
        return viewId;
    }

    public BundleJsonWithSubscriptions getBundle() {
        return bundle;
    }

    public List<PaymentJson> getPayments() {
        return payments;
    }

    public List<InvoiceJson> getInvoices() {
        return invoices;
    }

    public String getResonForChange() {
        return resonForChange;
    }
}
